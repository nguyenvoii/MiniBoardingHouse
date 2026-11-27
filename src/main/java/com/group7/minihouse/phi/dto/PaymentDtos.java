package com.group7.minihouse.phi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTO thanh toan hoa don (UC85-88). */
public final class PaymentDtos {

    private PaymentDtos() {}

    /** UC85: nhan vien ghi nhan da thu tien mat / chuyen khoan (method CASH hoac BANK_TRANSFER). */
    public record PaymentRequest(Integer invoiceId, String method, BigDecimal amount, String transactionCode) {}

    /** UC86: tao giao dich cho thanh toan QR (QR_CODE) hoac cong dien tu (GATEWAY); amount trong = con phai tra. */
    public record QrRequest(Integer invoiceId, String method, BigDecimal amount) {}

    /** UC88: so tien thuc nhan tren sao ke ngan hang. */
    public record ConfirmRequest(BigDecimal amountReceived) {}

    /** UC86: cong thanh toan goi ve. */
    public record CallbackRequest(String transactionCode, BigDecimal amount) {}

    /** SePay bao co giao dich ngan hang (webhook). Chi lay cac truong can dung, bo qua truong la SePay them sau. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record SepayWebhook(Long id, String gateway, String transactionDate, String accountNumber, String code,
                               String content, String transferType, BigDecimal transferAmount,
                               String referenceCode, String description) {}

    /** UC88: noi dung chuyen khoan can doi chieu voi ma hoa don. */
    public record MatchRequest(String content) {}

    public record PaymentResponse(Integer transactionId, Integer invoiceId, String invoiceCode, String method,
                                  String transactionCode, BigDecimal amountPaid, LocalDateTime paymentDate,
                                  Integer receivedByUserId, String receivedByName, String status) {}

    /** UC86: giao dich cho + thong tin tai khoan nhan tien de hien QR. */
    public record QrInfo(PaymentResponse payment, String bankName, String accountNumber, String accountHolder,
                         String qrImageUrl, String transferContent) {}

    public record InvoiceMatch(Integer invoiceId, String invoiceCode, String tenantName, BigDecimal remaining,
                               String paymentStatus) {}

    public record ConfirmResult(boolean confirmed, String message, PaymentResponse payment) {}

    public record MatchResult(List<InvoiceMatch> matches) {}
}
