package com.group7.minihouse.toan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DTO cho thu tien, phieu thu, thanh toan mot phan, can tru (UC 89-92). */
public final class PaymentDtos {

    private PaymentDtos() {}

    /** voucherType: MONTHLY_RENT, DEPOSIT, EXTRA_SERVICE (UC 89). transactionId co the de trong. */
    public record ReceiptRequest(Integer transactionId, String voucherType, BigDecimal amount, String note) {}

    public record TenantReceipt(Integer voucherId, String voucherCode, Integer invoiceId, String invoiceCode,
                                String voucherType, BigDecimal amount, LocalDateTime createdAt) {}

    public record ReceiptResponse(Integer voucherId, String voucherCode, Integer transactionId,
                                  String voucherType, BigDecimal amount, String note,
                                  LocalDateTime createdAt) {}

    public record PartialPaymentRequest(BigDecimal amount, LocalDateTime paidDate) {}

    public record PartialPaymentResponse(Integer partialLogId, Integer invoiceId, int installmentNumber,
                                         BigDecimal amount, LocalDateTime paidDate) {}

    public record PartialPaymentSummary(Integer invoiceId, BigDecimal totalAmount, BigDecimal paidAmount,
                                        BigDecimal remainingAmount, String paymentStatus,
                                        List<PartialPaymentResponse> installments) {}

    public record PrepaymentResponse(Integer prepaymentId, Integer tenantId, BigDecimal excessBalance,
                                     LocalDateTime updatedAt) {}

    public record DeductRequest(Integer tenantId, Integer targetInvoiceId, BigDecimal amount) {}

    public record DeductionResponse(Integer deductionId, Integer prepaymentId, Integer targetInvoiceId,
                                    BigDecimal deductedAmount, LocalDateTime deductedAt) {}

    public record RefundRequest(Integer tenantId, BigDecimal amount) {}

    public record ReverseRequest(String reason) {}
}
