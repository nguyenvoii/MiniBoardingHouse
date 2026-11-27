package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(invoiceRepo.findByIdForUpdate(50)).thenReturn(Optional.of(invoice)); // moi duong ghi tien khoa dong hoa don
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

    /** T3: hoa don da huy sau khi khach bam QR: tien ve khong duoc cong vao hoa don huy. */
    @Test
    void confirmDoesNotApplyToCancelledInvoice() {
        PaymentTransaction t = pending("QR_CODE", "3000000");
        invoice.setPaymentStatus("CANCELLED");
        var r = service.confirm(901, new ConfirmRequest(new BigDecimal("3000000")), 5, "ip");
        assertFalse(r.confirmed());
        assertEquals("FAILED", t.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getPaidAmount()));
        assertEquals("CANCELLED", invoice.getPaymentStatus());
        verify(invoiceService, never()).notifyPaymentConfirmed(any(), any());
    }

    /** M7: nhan vien ghi chuyen khoan dung so tien cua giao dich QR dang cho thi xac nhan chinh giao dich do. */
    @Test
    void bankTransferMatchingPendingQrConfirmsItInsteadOfCreatingNew() {
        PaymentTransaction t = pending("QR_CODE", "3000000");
        when(repo.findPendingOfInvoice(50)).thenReturn(List.of(t));
        var r = service.record(new PaymentRequest(50, "BANK_TRANSFER", new BigDecimal("3000000"), null), 5, "ip");
        assertEquals(901, r.transactionId());
        assertEquals("SUCCESS", t.getStatus());
        assertEquals("PAID", invoice.getPaymentStatus());
        verify(repo, never()).save(any(PaymentTransaction.class));
    }

    /** M7/M8: hoa don da thu du thi cac giao dich cho con lai duoc dong lai, khong con mo coi. */
    @Test
    void fullyPaidInvoiceClosesOtherPendingTransactions() {
        PaymentTransaction other = pending("QR_CODE", "1000000");
        when(repo.findPendingOfInvoice(50)).thenReturn(List.of(other));
        service.record(new PaymentRequest(50, "CASH", new BigDecimal("3000000"), null), 5, "ip");
        assertEquals("PAID", invoice.getPaymentStatus());
        assertEquals("REVERSED", other.getStatus());
    }

    /** M8: giao dich cho sai so tien / bi bo co duong dong; chi dong duoc giao dich dang cho. */
    @Test
    void cancelPendingClosesOnlyWaitingTransaction() {
        PaymentTransaction t = pending("QR_CODE", "3000000");
        assertEquals("REVERSED", service.cancelPending(901, 5, "ip").status());
        assertEquals("REVERSED", t.getStatus());
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class,
                () -> service.cancelPending(901, 5, "ip")).getStatus());
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
        var replay = service.callback(new CallbackRequest("QR-ABC12345", new BigDecimal("3000000")));
        assertTrue(replay.confirmed());
        verify(invoiceService).notifyPaymentConfirmed(any(), anyString());
        assertThrows(BusinessException.class,
                () -> service.callback(new CallbackRequest("QR-ABC12345", BigDecimal.ONE)));
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

    // ---------- SePay: tien chuyen khoan ve tu dong ----------

    @SuppressWarnings("unchecked")
    private void landlordExists() {
        jakarta.persistence.TypedQuery<Integer> q = org.mockito.Mockito.mock(jakarta.persistence.TypedQuery.class);
        when(em.createQuery(anyString(), eq(Integer.class))).thenReturn(q);
        when(q.setMaxResults(1)).thenReturn(q);
        when(q.getResultList()).thenReturn(List.of(5));
    }

    private Invoice otherInvoice(int id, String code) {
        Invoice other = new Invoice();
        other.setInvoiceId(id);
        other.setInvoiceCode(code);
        other.setTenant(invoice.getTenant());
        other.setTotalAmount(BigDecimal.TEN);
        other.setPaidAmount(BigDecimal.ZERO);
        other.setPaymentStatus("UNPAID");
        return other;
    }

    @Test
    void sepayConfirmsPendingTransferOfSameAmountAndKeepsSepayCode() {
        landlordExists();
        PaymentTransaction t = pending("QR_CODE", "3000000");
        when(repo.findPayableMentionedInFlat("ckhd202611p101thang11")).thenReturn(List.of(invoice));
        when(repo.findPendingOfInvoice(50)).thenReturn(List.of(t));

        service.confirmSepayTransfer(777, "CK HD202611P101 thang 11", new BigDecimal("3000000"), "ip");

        assertEquals("SUCCESS", t.getStatus());
        assertEquals("SEPAY-777", t.getTransactionCode());
        assertEquals("PAID", invoice.getPaymentStatus());
        assertEquals(0, new BigDecimal("3000000").compareTo(invoice.getPaidAmount()));
    }

    @Test
    void sepayCreditsInvoiceWhenAmountEqualsRemainingAndNoPendingTransaction() {
        landlordExists();
        when(repo.findPayableMentionedInFlat("hd202611p101")).thenReturn(List.of(invoice));

        String r = service.confirmSepayTransfer(778, "HD202611-P101", new BigDecimal("3000000"), "ip");

        org.mockito.ArgumentCaptor<PaymentTransaction> cap = org.mockito.ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(repo).save(cap.capture());
        assertEquals("SEPAY-778", cap.getValue().getTransactionCode());
        assertEquals("SUCCESS", cap.getValue().getStatus());
        assertEquals("BANK_TRANSFER", cap.getValue().getPaymentMethod());
        assertEquals("PAID", invoice.getPaymentStatus());
        assertTrue(r.contains("HD202611-P101"));
    }

    @Test
    void sepayDoesNotAutoCreditWrongAmountButLeavesAuditTrail() {
        landlordExists();
        when(repo.findPayableMentionedInFlat("hd202611p101")).thenReturn(List.of(invoice));

        service.confirmSepayTransfer(779, "HD202611-P101", new BigDecimal("1000000"), "ip");

        verify(repo, never()).save(any());
        assertEquals("UNPAID", invoice.getPaymentStatus());
        verify(activityLogService).log(eq(5), eq("SEPAY_UNMATCHED"), anyString(), eq("ip"));
    }

    @Test
    void sepayIgnoresSameTransactionIdSentTwice() {
        landlordExists();
        when(repo.findPayableMentionedInFlat("hd202611p101")).thenReturn(List.of(invoice));
        when(repo.existsByTransactionCode("SEPAY-780")).thenReturn(true);

        String r = service.confirmSepayTransfer(780, "HD202611-P101", new BigDecimal("3000000"), "ip");

        assertEquals("Da xu ly truoc do", r);
        verify(repo, never()).save(any());
        assertEquals("UNPAID", invoice.getPaymentStatus());
    }

    @Test
    void sepayRetryAfterInvoiceIsAlreadyPaidIsRecognisedWithoutNoise() {
        // hoa don da PAID thi khong con trong danh sach khop; phai nhan ra bang ma SEPAY-id truoc khi khop
        when(repo.existsByTransactionCode("SEPAY-784")).thenReturn(true);

        assertEquals("Da xu ly truoc do", service.confirmSepayTransfer(784, "HD202611-P101", new BigDecimal("3000000"), "ip"));

        verify(repo, never()).findPayableMentionedInFlat(anyString());
        verify(activityLogService, never()).log(any(), anyString(), anyString(), anyString());
    }

    @Test
    void sepayWithoutInvoiceCodeOnlyLogs() {
        landlordExists();
        when(repo.findPayableMentionedInFlat(anyString())).thenReturn(List.of());

        assertEquals("Khong khop hoa don", service.confirmSepayTransfer(781, "tien nha thang nay", new BigDecimal("3000000"), "ip"));

        verify(repo, never()).save(any());
        verify(activityLogService).log(eq(5), eq("SEPAY_UNMATCHED"), anyString(), eq("ip"));
    }

    @Test
    void sepayPicksTheLongestInvoiceCodeSoRoom10DoesNotStealRoom101() {
        landlordExists();
        Invoice shorter = otherInvoice(51, "HD202611-P10");
        when(repo.findPayableMentionedInFlat("hd202611p101")).thenReturn(List.of(shorter, invoice));

        service.confirmSepayTransfer(782, "HD202611P101", new BigDecimal("3000000"), "ip");

        assertEquals("PAID", invoice.getPaymentStatus());
        assertEquals("UNPAID", shorter.getPaymentStatus());
    }

    @Test
    void sepayTwoEquallyLongMatchesAreAmbiguousAndNotCredited() {
        landlordExists();
        Invoice twin = otherInvoice(52, "HD202611-P101");
        when(repo.findPayableMentionedInFlat("hd202611p101")).thenReturn(List.of(invoice, twin));

        assertEquals("Khong khop hoa don", service.confirmSepayTransfer(783, "HD202611P101", new BigDecimal("3000000"), "ip"));

        verify(repo, never()).save(any());
    }

    @Test
    void qrIsDynamicSepayUrlWithAmountAndContentWhenEnabled() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "sepayEnabled", true);
        when(bankAccountService.list(1)).thenReturn(List.of(
                new BankAccountResponse(1, 1, "MB Bank", "0071000888888", "CHU TRO", "/qr/x.png", true, true)));

        var info = service.createQr(new QrRequest(50, null, null), 5, 30, "ip");

        assertEquals("https://qr.sepay.vn/img?acc=0071000888888&bank=MBBank&amount=3000000&des=HD202611-P101",
                info.qrImageUrl());
        assertEquals("HD202611-P101", info.transferContent());
    }

    @Test
    void qrKeepsUploadedImageWhenSepayIsOff() {
        when(bankAccountService.list(1)).thenReturn(List.of(
                new BankAccountResponse(1, 1, "MB Bank", "0071000888888", "CHU TRO", "/qr/x.png", true, true)));

        assertEquals("/qr/x.png", service.createQr(new QrRequest(50, null, null), 5, 30, "ip").qrImageUrl());
    }
}
