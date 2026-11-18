package com.group7.minihouse.toan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO cho UC129: bảng kê hóa đơn và phiếu thu theo tháng. */
public final class MonthlyStatementDtos {
    private MonthlyStatementDtos() {}
    public record InvoiceLine(Integer invoiceId, String invoiceCode, String tenantName, String roomCode,
                              LocalDate issueDate, LocalDate dueDate, BigDecimal totalAmount,
                              BigDecimal paidAmount, BigDecimal remainingAmount, String status) {}
    public record ReceiptLine(Integer voucherId, String voucherCode, String voucherType, BigDecimal amount,
                              LocalDateTime createdAt, String transactionCode, String invoiceCode) {}
    public record MonthlySummary(String month, Long invoiceCount, BigDecimal invoicedAmount,
                                 BigDecimal collectedAmount, BigDecimal remainingAmount, Long receiptCount,
                                 BigDecimal receiptAmount) {}
    public record MonthlyStatement(String month, MonthlySummary summary,
                                   java.util.List<InvoiceLine> invoices, java.util.List<ReceiptLine> receipts) {}
}
