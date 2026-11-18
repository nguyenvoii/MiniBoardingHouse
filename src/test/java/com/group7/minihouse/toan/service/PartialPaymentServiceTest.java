package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.PartialPaymentLog;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.dto.PaymentDtos.PartialPaymentRequest;
import com.group7.minihouse.toan.repository.InvoiceRepository;
import com.group7.minihouse.toan.repository.PartialPaymentLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/** UC91 thanh toan mot phan: khoa hoa don, chan hoa don huy/da tra du, danh so dot, nhat ky. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartialPaymentServiceTest {

    @Mock PartialPaymentLogRepository logRepo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock ToanActivityWriter activity;

    PartialPaymentService service;
    Invoice invoice;

    @BeforeEach
    void setUp() {
        service = new PartialPaymentService(logRepo, invoiceRepo, new InvoiceBalanceCalculator(), activity);
        invoice = new Invoice();
        invoice.setInvoiceId(10);
        invoice.setInvoiceCode("INV-1");
        invoice.setTotalAmount(new BigDecimal("1000000"));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setDueDate(LocalDate.now().plusDays(10));
        invoice.setPaymentStatus("UNPAID");
        when(invoiceRepo.findByIdForUpdate(10)).thenReturn(Optional.of(invoice));
        when(logRepo.save(any(PartialPaymentLog.class))).thenAnswer(i -> {
            PartialPaymentLog l = i.getArgument(0);
            l.setPartialLogId(1);
            return l;
        });
    }

    private static PartialPaymentRequest req(String amount) {
        return new PartialPaymentRequest(amount == null ? null : new BigDecimal(amount), null);
    }

    @Test
    void ghiDotDauCongVaoHoaDonVaGhiNhatKyNguoiThu() {
        when(logRepo.maxInstallment(10)).thenReturn(0);
        var r = service.record(10, req("400000"), 5, "ip");
        assertEquals(1, r.installmentNumber());
        assertEquals("PARTIALLY_PAID", invoice.getPaymentStatus());
        assertEquals(0, new BigDecimal("400000").compareTo(invoice.getPaidAmount()));
        verify(activity).log(org.mockito.ArgumentMatchers.eq(5), any(), any(), any());
    }

    /** B1: danh so theo dot lon nhat + 1, khong dem so dong (dem lech khi co dot bi xoa). */
    @Test
    void soDotLaMaxCongMot() {
        when(logRepo.maxInstallment(10)).thenReturn(4);
        assertEquals(5, service.record(10, req("100000"), 5, "ip").installmentNumber());
    }

    @Test
    void thuDuThiHoaDonPaid() {
        service.record(10, req("1000000"), 5, "ip");
        assertEquals("PAID", invoice.getPaymentStatus());
    }

    @Test
    void khongGhiVuotSoConPhaiTraHoacSoKhongHopLe() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.record(10, req("1000001"), 5, "ip")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.record(10, req("0"), 5, "ip")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.record(10, req(null), 5, "ip")).getStatus());
        verify(logRepo, never()).save(any());
    }

    @Test
    void khongGhiChoHoaDonHuyHoacDaTraDu() {
        invoice.setPaymentStatus("CANCELLED");
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.record(10, req("1000"), 5, "ip")).getStatus());
        invoice.setPaymentStatus("PAID");
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.record(10, req("1000"), 5, "ip")).getStatus());
    }

    @Test
    void khongTimThayHoaDonLa404() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.record(99, req("1000"), 5, "ip")).getStatus());
    }

    /** B3: khong sua dot thanh toan cua hoa don da huy. */
    @Test
    void khongSuaDotCuaHoaDonDaHuy() {
        PartialPaymentLog l = log(300000);
        when(logRepo.findById(1)).thenReturn(Optional.of(l));
        invoice.setPaymentStatus("CANCELLED");
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.update(1, req("200000"), 5, "ip")).getStatus());
        assertEquals(0, new BigDecimal("300000").compareTo(l.getAmount()));
    }

    @Test
    void suaDotTinhLaiSoDaTraVaChanVuotTongHoaDon() {
        PartialPaymentLog l = log(300000);
        invoice.setPaidAmount(new BigDecimal("300000"));
        invoice.setPaymentStatus("PARTIALLY_PAID");
        when(logRepo.findById(1)).thenReturn(Optional.of(l));

        service.update(1, req("500000"), 5, "ip");
        assertEquals(0, new BigDecimal("500000").compareTo(invoice.getPaidAmount()));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.update(1, req("1000001"), 5, "ip")).getStatus());
        assertEquals(0, new BigDecimal("500000").compareTo(invoice.getPaidAmount()));
    }

    private PartialPaymentLog log(long amount) {
        PartialPaymentLog l = new PartialPaymentLog();
        l.setPartialLogId(1);
        l.setInvoice(invoice);
        l.setInstallmentNumber(1);
        l.setAmount(BigDecimal.valueOf(amount));
        return l;
    }
}
