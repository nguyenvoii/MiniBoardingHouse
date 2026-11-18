package com.group7.minihouse.toan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTO cho UC127: báo cáo và đối soát thu tiền. */
public final class PaymentReportDtos {
    private PaymentReportDtos() {}

    public record DailyPaymentRow(LocalDate paymentDate, Integer receiverId, String receiverName,
                                  String paymentMethod, Long transactionCount, BigDecimal totalAmount) {}
    public record DailyPaymentSummary(LocalDate fromDate, LocalDate toDate, Long successfulTransactions,
                                      Long reversedTransactions, Long failedTransactions,
                                      BigDecimal successfulAmount, BigDecimal reversedAmount,
                                      BigDecimal netCollectedAmount, BigDecimal receiptAmount,
                                      BigDecimal difference) {}
    public record DailyPaymentReport(DailyPaymentSummary summary, java.util.List<DailyPaymentRow> rows) {}
}
