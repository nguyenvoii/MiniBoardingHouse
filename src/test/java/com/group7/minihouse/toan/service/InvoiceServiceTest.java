package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.dto.CancelInvoiceRequest;
import com.group7.minihouse.toan.repository.InvoiceNotificationRepository;
import com.group7.minihouse.toan.repository.InvoiceRepository;
import com.group7.minihouse.toan.repository.PartialPaymentLogRepository;
import com.group7.minihouse.toan.repository.ToanPaymentTransactionRepository;
import com.group7.minihouse.toan.repository.ToanUserActivityLogRepository;
import com.group7.minihouse.toan.repository.ToanUserRepository;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepo;
    @Mock private InvoiceNotificationRepository notificationRepo;
    @Mock private InvoiceBalanceCalculator calculator;
    @Mock private PrepaymentService prepaymentService;
    @Mock private ObjectProvider<InvoiceMailGateway> mailGateway;
    @Mock private ToanPaymentTransactionRepository paymentTransactionRepo;
    @Mock private PartialPaymentLogRepository partialPaymentLogRepo;
    @Mock private ToanUserActivityLogRepository activityLogRepo;
    @Mock private ToanUserRepository userRepo;

    private InvoiceService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepo, notificationRepo, calculator, prepaymentService,
                mailGateway, paymentTransactionRepo, partialPaymentLogRepo, activityLogRepo, userRepo);
    }

    @Test
    void huyHoaDonChuaThuTienThanhCongVaGhiNhatKy() {
        Invoice invoice = invoice("UNPAID", BigDecimal.ZERO);
        User actor = new User();
        actor.setUserId(7);
        when(invoiceRepo.findByIdForUpdate(10)).thenReturn(Optional.of(invoice));
        when(paymentTransactionRepo.existsByInvoiceInvoiceIdAndStatus(10, "SUCCESS")).thenReturn(false);
        when(partialPaymentLogRepo.existsByInvoiceInvoiceIdAndAmountGreaterThan(10, BigDecimal.ZERO)).thenReturn(false);
        when(userRepo.findById(7)).thenReturn(Optional.of(actor));

        var result = service.cancel(10, new CancelInvoiceRequest("Phát hành nhầm"), 7, "127.0.0.1");

        assertEquals("CANCELLED", invoice.getPaymentStatus());
        assertEquals("CANCELLED", result.paymentStatus());
        assertEquals("Phát hành nhầm", result.reason());
        verify(activityLogRepo).save(any());
        verify(invoiceRepo).flush();
    }

    @Test
    void khongChoHuyKhiPaidAmountLonHonKhong() {
        Invoice invoice = invoice("PARTIALLY_PAID", new BigDecimal("100000"));
        when(invoiceRepo.findByIdForUpdate(10)).thenReturn(Optional.of(invoice));
        when(paymentTransactionRepo.existsByInvoiceInvoiceIdAndStatus(10, "SUCCESS")).thenReturn(false);
        when(partialPaymentLogRepo.existsByInvoiceInvoiceIdAndAmountGreaterThan(10, BigDecimal.ZERO)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(10, new CancelInvoiceRequest("Nhập nhầm"), 7, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("PARTIALLY_PAID", invoice.getPaymentStatus());
        verifyNoInteractions(activityLogRepo);
    }

    @Test
    void khongChoHuyKhiCoGiaoDichThanhCongDuPaidAmountBangKhong() {
        Invoice invoice = invoice("UNPAID", BigDecimal.ZERO);
        when(invoiceRepo.findByIdForUpdate(10)).thenReturn(Optional.of(invoice));
        when(paymentTransactionRepo.existsByInvoiceInvoiceIdAndStatus(10, "SUCCESS")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(10, new CancelInvoiceRequest("Nhập nhầm"), 7, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verifyNoInteractions(activityLogRepo);
    }

    @Test
    void khongChoHuyKhiCoDotThanhToanMotPhan() {
        Invoice invoice = invoice("UNPAID", BigDecimal.ZERO);
        when(invoiceRepo.findByIdForUpdate(10)).thenReturn(Optional.of(invoice));
        when(paymentTransactionRepo.existsByInvoiceInvoiceIdAndStatus(10, "SUCCESS")).thenReturn(false);
        when(partialPaymentLogRepo.existsByInvoiceInvoiceIdAndAmountGreaterThan(10, BigDecimal.ZERO)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(10, new CancelInvoiceRequest("Nhập nhầm"), 7, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verifyNoInteractions(activityLogRepo);
    }

    @Test
    void khongChoHuyHoaDonDaHuy() {
        Invoice invoice = invoice("CANCELLED", BigDecimal.ZERO);
        when(invoiceRepo.findByIdForUpdate(10)).thenReturn(Optional.of(invoice));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(10, new CancelInvoiceRequest("Lý do"), 7, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verifyNoInteractions(paymentTransactionRepo, partialPaymentLogRepo, activityLogRepo);
    }

    @Test
    void khongTimThayHoaDonTraVe404() {
        when(invoiceRepo.findByIdForUpdate(10)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(10, new CancelInvoiceRequest("Lý do"), 7, "127.0.0.1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void tuChoiLyDoTrong() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(10, new CancelInvoiceRequest("  "), 7, "127.0.0.1"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(invoiceRepo);
    }

    /** T3: con giao dich QR cho xac nhan thi khong huy hoa don (tien co the ve sau khi huy). */
    @Test
    void khongChoHuyKhiConGiaoDichChoXacNhan() {
        Invoice invoice = invoice("UNPAID", BigDecimal.ZERO);
        when(invoiceRepo.findByIdForUpdate(10)).thenReturn(Optional.of(invoice));
        when(paymentTransactionRepo.existsByInvoiceInvoiceIdAndStatusAndPaymentMethodIn(eq(10), eq("FAILED"), any()))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(10, new CancelInvoiceRequest("Nhập nhầm"), 7, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("UNPAID", invoice.getPaymentStatus());
        verifyNoInteractions(activityLogRepo);
    }

    /** T4: hoa don da huy thi phat hanh lai duoc tren cung bang tinh (dung lai dong hoa don, khong ket UNIQUE). */
    @Test
    void phatHanhLaiHoaDonDaHuyTrenCungBangTinh() {
        com.group7.minihouse.shared.entity.MonthlyBillingPeriod period = new com.group7.minihouse.shared.entity.MonthlyBillingPeriod();
        period.setStatus("APPROVED");
        period.setMonthYear("2026-11");
        com.group7.minihouse.shared.entity.Tenant tenant = new com.group7.minihouse.shared.entity.Tenant();
        tenant.setTenantId(3);
        com.group7.minihouse.shared.entity.RentalContract contract = new com.group7.minihouse.shared.entity.RentalContract();
        contract.setTenant(tenant);
        contract.setPaymentDueDay(5);
        com.group7.minihouse.shared.entity.CalculatedMonthlyFee fee = new com.group7.minihouse.shared.entity.CalculatedMonthlyFee();
        fee.setCalculatedFeeId(20);
        fee.setBillingPeriod(period);
        fee.setContract(contract);
        com.group7.minihouse.shared.entity.Room room = new com.group7.minihouse.shared.entity.Room();
        room.setRoomId(8);
        room.setRoomCode("P101");
        fee.setRoom(room);
        cancelledTenant(tenant);
        fee.setTotalCalculatedAmount(new BigDecimal("2500000"));
        Invoice cancelled = invoice("CANCELLED", BigDecimal.ZERO);
        cancelled.setCalculatedFee(fee);
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "em", em);
        when(em.find(com.group7.minihouse.shared.entity.CalculatedMonthlyFee.class, 20)).thenReturn(fee);
        when(invoiceRepo.findByCalculatedFeeCalculatedFeeId(20)).thenReturn(Optional.of(cancelled));
        when(invoiceRepo.findByIdForUpdate(10)).thenReturn(Optional.of(cancelled));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        service.issue(new com.group7.minihouse.toan.dto.InvoiceDtos.IssueRequest(20, null, null));

        assertEquals("UNPAID", cancelled.getPaymentStatus());
        assertEquals(0, new BigDecimal("2500000").compareTo(cancelled.getTotalAmount()));
        assertEquals("INV-202611-P101-2", cancelled.getInvoiceCode());
    }

    /** Phat hanh theo lo van bo qua bang tinh co hoa don (ke ca da huy): chi phat hanh lai tung phong co chu y. */
    @Test
    void phatHanhHoaDonConHieuLucTrenCungBangTinhBiTuChoi() {
        com.group7.minihouse.shared.entity.MonthlyBillingPeriod period = new com.group7.minihouse.shared.entity.MonthlyBillingPeriod();
        period.setStatus("APPROVED");
        com.group7.minihouse.shared.entity.CalculatedMonthlyFee fee = new com.group7.minihouse.shared.entity.CalculatedMonthlyFee();
        fee.setBillingPeriod(period);
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "em", em);
        when(em.find(com.group7.minihouse.shared.entity.CalculatedMonthlyFee.class, 20)).thenReturn(fee);
        when(invoiceRepo.findByCalculatedFeeCalculatedFeeId(20)).thenReturn(Optional.of(invoice("UNPAID", BigDecimal.ZERO)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.issue(new com.group7.minihouse.toan.dto.InvoiceDtos.IssueRequest(20, null, null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    private static void cancelledTenant(com.group7.minihouse.shared.entity.Tenant t) {
        t.setFullName("Khach A");
    }

    private Invoice invoice(String status, BigDecimal paid) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceId(10);
        invoice.setInvoiceCode("INV-202611-P101-2");
        invoice.setPaymentStatus(status);
        invoice.setPaidAmount(paid);
        invoice.setTotalAmount(new BigDecimal("1000000"));
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(5));
        return invoice;
    }
}
