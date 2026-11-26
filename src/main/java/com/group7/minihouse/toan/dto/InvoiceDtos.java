package com.group7.minihouse.toan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTO cho hoa don (UC 76, 78-84). */
public final class InvoiceDtos {

    private InvoiceDtos() {}

    public record IssueRequest(Integer calculatedFeeId, LocalDate issueDate, LocalDate dueDate) {}

    public record IssueBatchRequest(Integer billingPeriodId, LocalDate issueDate, LocalDate dueDate) {}

    public record AdjustInvoiceRequest(BigDecimal newTotalAmount, String reason) {}

    public record InvoiceResponse(Integer invoiceId, String invoiceCode, Integer calculatedFeeId,
                                  Integer tenantId, String tenantName, Integer roomId, String roomCode,
                                  String monthYear, LocalDate issueDate, LocalDate dueDate,
                                  BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal remainingAmount,
                                  String paymentStatus, String shareLink, String pdfUrl) {}

    public record FeeLine(String itemName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal subtotal) {}

    /** Chi tiet hoa don: tien phong, dien, nuoc, dich vu... (UC 78). */
    public record InvoiceDetail(InvoiceResponse invoice, BigDecimal rentAmount, BigDecimal electricAmount,
                                BigDecimal waterAmount, BigDecimal serviceAmount, BigDecimal surchargeAmount,
                                BigDecimal discountAmount, BigDecimal latePenaltyAmount,
                                List<FeeLine> items) {}

    public record ShareLink(String shareLink) {}

    public record SharedInvoice(String invoiceCode, String monthYear, LocalDate issueDate, LocalDate dueDate,
                                BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal remainingAmount,
                                String paymentStatus) {}

    public record NotificationResponse(Integer notificationId, Integer invoiceId, String channel,
                                       LocalDateTime sentAt, String status) {}

    public record RoomSummaryLine(Integer roomId, String roomCode, String invoiceCode,
                                  BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal remainingAmount,
                                  String paymentStatus) {}

    public record MonthSummary(String monthYear, long invoiceCount, BigDecimal totalAmount,
                               BigDecimal paidAmount, BigDecimal remainingAmount) {}

    /** Bang tong hop tien phai thu cua mot thang trong khu (UC 76). */
    public record MonthDetailSummary(String monthYear, BigDecimal totalAmount, BigDecimal paidAmount,
                                     BigDecimal remainingAmount, List<RoomSummaryLine> rooms) {}
}
