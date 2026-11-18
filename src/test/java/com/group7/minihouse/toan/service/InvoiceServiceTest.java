package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
