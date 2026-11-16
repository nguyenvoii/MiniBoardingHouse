package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.BankAccountResponse;
import com.group7.minihouse.phi.dto.PaymentDtos.CallbackRequest;
import com.group7.minihouse.phi.dto.PaymentDtos.ConfirmRequest;
import com.group7.minihouse.phi.dto.PaymentDtos.PaymentRequest;
import com.group7.minihouse.phi.dto.PaymentDtos.QrRequest;
import com.group7.minihouse.phi.repository.PaymentTransactionRepository;
import com.group7.minihouse.shared.entity.CalculatedMonthlyFee;
import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.MonthlyBillingPeriod;
import com.group7.minihouse.shared.entity.PaymentTransaction;
import com.group7.minihouse.shared.entity.Property;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.repository.InvoiceRepository;
import com.group7.minihouse.toan.service.InvoiceBalanceCalculator;
import com.group7.minihouse.toan.service.InvoiceService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/** UC85-88 thanh toan hoa don (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock PaymentTransactionRepository repo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock InvoiceService invoiceService;
    @Mock BankAccountService bankAccountService;
    @Mock EntityManager em;
    @Mock ActivityLogService activityLogService;

    PaymentService service;
    Invoice invoice;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new PaymentService(repo, invoiceRepo, new InvoiceBalanceCalculator(), invoiceService,
                bankAccountService, em, activityLogService, clock);
        Property property = new Property();
        property.setPropertyId(1);
        MonthlyBillingPeriod period = new MonthlyBillingPeriod();
        period.setProperty(property);
        CalculatedMonthlyFee fee = new CalculatedMonthlyFee();
        fee.setBillingPeriod(period);
        Tenant tenant = new Tenant();
        tenant.setTenantId(7);
        tenant.setFullName("A");
        invoice = new Invoice();
        invoice.setInvoiceId(50);
        invoice.setInvoiceCode("HD202611-P101");
        invoice.setCalculatedFee(fee);
        invoice.setTenant(tenant);
        invoice.setTotalAmount(new BigDecimal("3000000"));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setDueDate(LocalDate.of(2026, 12, 5));
        invoice.setPaymentStatus("UNPAID");
        when(invoiceRepo.findById(50)).thenReturn(Optional.of(invoice));
        when(repo.save(any(PaymentTransaction.class))).thenAnswer(i -> {
            PaymentTransaction t = i.getArgument(0);
            t.setTransactionId(900);
            return t;
        });
        User u = new User();
        u.setUserId(5);
        u.setFullName("Thu ngan");
        when(em.getReference(eq(User.class), any())).thenReturn(u);
    }

    private PaymentTransaction pending(String method, String amount) {
        PaymentTransaction t = new PaymentTransaction();
        t.setTransactionId(901);
        t.setInvoice(invoice);
        t.setPaymentMethod(method);
        t.setTransactionCode("QR-ABC12345");
        t.setAmountPaid(new BigDecimal(amount));
        t.setStatus("FAILED");
        when(repo.findById(901)).thenReturn(Optional.of(t));
        when(repo.findByTransactionCode("QR-ABC12345")).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void cashPaymentAddsToInvoiceAndMarksPartiallyPaid() {
        var r = service.record(new PaymentRequest(50, "cash", new BigDecimal("1000000"), null), 5, "ip");
        assertEquals("SUCCESS", r.status());
        assertEquals("CASH", r.method());
        assertEquals(0, new BigDecimal("1000000").compareTo(invoice.getPaidAmount()));
        assertEquals("PARTIALLY_PAID", invoice.getPaymentStatus());
        verify(invoiceService).notifyPaymentConfirmed(eq(invoice), any());
    }

    @Test
    void fullPaymentMarksPaid() {
        service.record(new PaymentRequest(50, "BANK_TRANSFER", new BigDecimal("3000000"), "FT123"), 5, "ip");
        assertEquals("PAID", invoice.getPaymentStatus());
    }

    @Test
    void rejectsOverpaymentZeroAndUnknownMethod() {
        assertThrows(BusinessException.class,
                () -> service.record(new PaymentRequest(50, "CASH", new BigDecimal("3000001"), null), 5, "ip"));
        assertThrows(BusinessException.class,
                () -> service.record(new PaymentRequest(50, "CASH", BigDecimal.ZERO, null), 5, "ip"));
        assertThrows(BusinessException.class,
                () -> service.record(new PaymentRequest(50, "QR_CODE", BigDecimal.TEN, null), 5, "ip"));
        verify(invoiceService, never()).notifyPaymentConfirmed(any(), any());
    }

    @Test
    void rejectsPaidOrCancelledInvoiceAndUnknownInvoice() {
        invoice.setPaymentStatus("PAID");
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.record(new PaymentRequest(50, "CASH", BigDecimal.TEN, null), 5, "ip")).getStatus());
        assertThrows(BusinessException.class,
                () -> service.record(new PaymentRequest(99, "CASH", BigDecimal.TEN, null), 5, "ip"));
    }

    @Test
    void needsLoggedInReceiver() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> service.record(new PaymentRequest(50, "CASH", BigDecimal.TEN, null), null, "ip")).getStatus());
    }

    @Test
    void duplicateTransactionCodeRejected() {
        when(repo.existsByTransactionCode("FT123")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.record(new PaymentRequest(50, "BANK_TRANSFER", BigDecimal.TEN, "FT123"), 5, "ip"))
                .getStatus());
    }

    @Test
    void qrNeedsDefaultBankAccountAndReturnsInvoiceCodeAsContent() {
        when(bankAccountService.list(1)).thenReturn(List.of());
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.createQr(new QrRequest(50, null, null), 5, 5, "ip")).getStatus());

        when(bankAccountService.list(1)).thenReturn(List.of(
                new BankAccountResponse(1, 1, "VCB", "0123456789", "CHU TRO", "/qr/x.png", true, true)));
        var info = service.createQr(new QrRequest(50, null, null), 5, 5, "ip");
        assertEquals("HD202611-P101", info.transferContent());
        assertEquals("FAILED", info.payment().status()); // cho xac nhan
        assertEquals(0, new BigDecimal("3000000").compareTo(info.payment().amountPaid()));
        assertTrue(info.payment().transactionCode().startsWith("QR-"));
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getPaidAmount())); // chua cong vao hoa don
    }

    @Test
    void confirmWithMatchingAmountAppliesToInvoice() {
        PaymentTransaction t = pending("BANK_TRANSFER", "3000000");
        var r = service.confirm(901, new ConfirmRequest(new BigDecimal("3000000")), 5, "ip");
        assertTrue(r.confirmed());
        assertEquals("SUCCESS", t.getStatus());
        assertEquals("PAID", invoice.getPaymentStatus());
    }

    @Test
    void confirmWithDifferentAmountKeepsPendingAndDoesNotPay() {
        PaymentTransaction t = pending("BANK_TRANSFER", "3000000");
        var r = service.confirm(901, new ConfirmRequest(new BigDecimal("2999000")), 5, "ip");
        assertFalse(r.confirmed());
        assertEquals("FAILED", t.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getPaidAmount()));
    }

    @Test
    void confirmFailsWhenInvoiceAlreadyPaidElsewhere() {
        pending("QR_CODE", "3000000");
        invoice.setPaidAmount(new BigDecimal("1000000"));
        var r = service.confirm(901, new ConfirmRequest(new BigDecimal("3000000")), 5, "ip");
        assertFalse(r.confirmed());
        assertEquals(0, new BigDecimal("1000000").compareTo(invoice.getPaidAmount()));
    }

    @Test
    void cannotConfirmCashOrAlreadyConfirmedTransaction() {
        PaymentTransaction cash = pending("CASH", "100");
        assertThrows(BusinessException.class, () -> service.confirm(901, new ConfirmRequest(new BigDecimal("100")), 5, "ip"));
        cash.setPaymentMethod("QR_CODE");
        cash.setStatus("SUCCESS");
        assertThrows(BusinessException.class, () -> service.confirm(901, new ConfirmRequest(new BigDecimal("100")), 5, "ip"));
    }

    @Test
    void callbackFindsTransactionByCode() {
        PaymentTransaction t = pending("GATEWAY", "3000000");
        var r = service.callback(new CallbackRequest("QR-ABC12345", new BigDecimal("3000000")));
        assertTrue(r.confirmed());
        assertEquals("SUCCESS", t.getStatus());
        assertThrows(BusinessException.class, () -> service.callback(new CallbackRequest("NOPE", BigDecimal.ONE)));
    }

    @Test
    void invoiceCodeMustBeWholeTokenInTransferContent() {
        assertTrue(PaymentService.mentions("TT hoa don HD202611-P101 thang 11", "HD202611-P101"));
        assertTrue(PaymentService.mentions("hd202611-p101", "HD202611-P101"));
        assertFalse(PaymentService.mentions("TT HD202611-P1010", "HD202611-P101"));
        assertFalse(PaymentService.mentions("TT XHD202611-P101", "HD202611-P101"));
    }

    @Test
    void matchFiltersSubstringFalsePositives() {
        Invoice other = new Invoice();
        other.setInvoiceId(51);
        other.setInvoiceCode("HD202611-P10");
        other.setTenant(invoice.getTenant());
        other.setTotalAmount(BigDecimal.TEN);
        other.setPaymentStatus("UNPAID");
        when(repo.findPayableMentionedIn("ck HD202611-P101")).thenReturn(List.of(invoice, other));
        var r = service.match("ck HD202611-P101");
        assertEquals(1, r.matches().size());
        assertEquals("HD202611-P101", r.matches().get(0).invoiceCode());
        assertThrows(BusinessException.class, () -> service.match("  "));
    }

    @Test
    void tenantCannotCreateQrForSomeoneElsesInvoice() {
        assertThrows(BusinessException.class,
                () -> service.createQrForTenant(new QrRequest(50, null, null), 999, 30, "ip"));
    }
}
