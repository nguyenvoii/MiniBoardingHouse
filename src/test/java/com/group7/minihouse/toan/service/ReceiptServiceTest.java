package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.PaymentTransaction;
import com.group7.minihouse.shared.entity.ReceiptVoucher;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.dto.PaymentDtos.ReceiptRequest;
import com.group7.minihouse.toan.repository.ReceiptVoucherRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** UC89 phieu thu: phai gan giao dich hop le, khong vuot so tien, ma khong trung. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceiptServiceTest {

    @Mock ReceiptVoucherRepository receiptRepo;
    @Mock InvoiceService invoiceService;
    @Mock ToanActivityWriter activity;

    ReceiptService service;
    EntityManager em;
    PaymentTransaction tx;

    @BeforeEach
    void setUp() {
        service = new ReceiptService(receiptRepo, invoiceService, activity);
        em = mock(EntityManager.class);
        ReflectionTestUtils.setField(service, "em", em);
        tx = new PaymentTransaction();
        tx.setTransactionId(7);
        tx.setInvoice(new Invoice());
        tx.setAmountPaid(new BigDecimal("3000000"));
        tx.setStatus("SUCCESS");
        when(em.find(PaymentTransaction.class, 7)).thenReturn(tx);
        when(receiptRepo.sumByTransaction(7)).thenReturn(BigDecimal.ZERO);
        when(receiptRepo.save(any(ReceiptVoucher.class))).thenAnswer(i -> {
            ReceiptVoucher v = i.getArgument(0);
            v.setVoucherId(1);
            return v;
        });
    }

    private static ReceiptRequest req(Integer tx, String type, String amount) {
        return new ReceiptRequest(tx, type, new BigDecimal(amount), null);
    }

    @Test
    void tenantReceiptPrintScopesByTenantAndOmitsInternalNote() {
        ReceiptVoucher voucher = new ReceiptVoucher();
        voucher.setVoucherCode("PT-1");
        voucher.setVoucherType("MONTHLY_RENT");
        voucher.setAmount(new BigDecimal("1000"));
        voucher.setCreatedAt(LocalDateTime.of(2026, 11, 25, 9, 0));
        voucher.setNote("internal note");
        when(receiptRepo.findSuccessfulForTenant(20, 3)).thenReturn(Optional.of(voucher));

        String html = service.printableHtmlForTenant(20, 3);

        assertTrue(html.contains("PT-1"));
        assertFalse(html.contains("internal note"));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.printableHtmlForTenant(21, 3)).getStatus());
    }

    @Test
    void phieuTienPhongKhongGanGiaoDichBiTuChoi() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.create(req(null, "MONTHLY_RENT", "1000"), 5, "ip")).getStatus());
    }

    /** M12: duong API khong cho lap phieu coc tay (tien coc suy ra tu phieu coc dung mau cua hop dong). */
    @Test
    void duongApiTuChoiPhieuCoc() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.create(req(null, "DEPOSIT", "1000"), 5, "ip")).getStatus());
    }

    @Test
    void phieuCocHoacDichVuKhongBatBuocGiaoDich() {
        assertEquals("DEPOSIT", service.create(req(null, "deposit", "1000")).voucherType());
        assertEquals("EXTRA_SERVICE", service.create(req(null, "EXTRA_SERVICE", "1000"), 5, "ip").voucherType());
    }

    @Test
    void khongLapPhieuVuotSoTienGiaoDichKeCaCacPhieuDaLap() {
        assertEquals("MONTHLY_RENT", service.create(req(7, "MONTHLY_RENT", "3000000"), 5, "ip").voucherType());
        when(receiptRepo.sumByTransaction(7)).thenReturn(new BigDecimal("2500000"));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.create(req(7, "MONTHLY_RENT", "600000"), 5, "ip")).getStatus());
    }

    @Test
    void locksInvoiceBeforeCheckingPreviouslyIssuedReceipts() {
        service.create(req(7, "MONTHLY_RENT", "2000000"), 5, "ip");

        var order = org.mockito.Mockito.inOrder(em, receiptRepo);
        order.verify(em).lock(tx.getInvoice(), LockModeType.PESSIMISTIC_WRITE);
        order.verify(em).refresh(tx);
        order.verify(receiptRepo).sumByTransaction(7);
    }

    @Test
    void chiLapPhieuChoGiaoDichDaThuThanhCong() {
        tx.setStatus("FAILED");
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.create(req(7, "MONTHLY_RENT", "1000"), 5, "ip")).getStatus());
        tx.setStatus("REVERSED");
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.create(req(7, "MONTHLY_RENT", "1000"), 5, "ip")).getStatus());
    }

    @Test
    void giaoDichKhongTonTaiLa404() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.create(req(99, "MONTHLY_RENT", "1000"), 5, "ip")).getStatus());
    }

    /** B1: dem +1 co the trung ma (phieu bi xoa / lech so): bo qua cac ma da ton tai. */
    @Test
    void maPhieuBoQuaMaDaTonTai() {
        when(receiptRepo.countByVoucherCodeStartingWith(any())).thenReturn(2L);
        when(receiptRepo.existsByVoucherCode(any())).thenAnswer(i -> ((String) i.getArgument(0)).endsWith("0003"));
        String code = service.create(req(null, "DEPOSIT", "1000")).voucherCode();
        assertEquals(true, code.endsWith("0004"), code);
    }

    @Test
    void soTienPhaiLonHonKhongVaLoaiPhieuHopLe() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.create(req(null, "MONTHLY_RENT", "0"), 5, "ip")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.create(req(null, "OTHER", "1000"), 5, "ip")).getStatus());
    }
}
