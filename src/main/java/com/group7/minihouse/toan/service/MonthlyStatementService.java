package com.group7.minihouse.toan.service;

import com.group7.minihouse.shared.entity.Invoice;
import com.group7.minihouse.shared.entity.ReceiptVoucher;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableData;
import com.group7.minihouse.shared.export.TableExporter;
import com.group7.minihouse.toan.dto.MonthlyStatementDtos.*;
import com.group7.minihouse.toan.repository.MonthlyStatementRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC129: lập bảng kê hóa đơn và phiếu thu theo tháng, xuất Excel/PDF. */
@Service
public class MonthlyStatementService {
    private final MonthlyStatementRepository repository;
    private final TableExporter exporter;
    public MonthlyStatementService(MonthlyStatementRepository repository, TableExporter exporter) {
        this.repository = repository; this.exporter = exporter;
    }

    /** UC129: lấy toàn bộ hóa đơn và phiếu thu phát sinh trong tháng. */
    @Transactional(readOnly = true)
    public MonthlyStatement get(String month) {
        YearMonth ym;
        try { ym = YearMonth.parse(month); }
        catch (RuntimeException ex) { throw new BusinessException("Tháng phải có định dạng yyyy-MM"); }
        LocalDate start = ym.atDay(1), end = ym.plusMonths(1).atDay(1);
        LocalDateTime startTime = start.atStartOfDay(), endTime = end.atStartOfDay();
        List<InvoiceLine> invoices = repository.findInvoices(start, end).stream().map(this::invoiceLine).toList();
        List<ReceiptLine> receipts = repository.findReceipts(startTime, endTime).stream().map(this::receiptLine).toList();
        BigDecimal invoiced = invoices.stream().map(InvoiceLine::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = invoices.stream().map(InvoiceLine::paidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = invoices.stream().map(InvoiceLine::remainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receiptTotal = receipts.stream().map(ReceiptLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        MonthlySummary summary = new MonthlySummary(month, (long) invoices.size(), invoiced, collected, remaining,
                (long) receipts.size(), receiptTotal);
        return new MonthlyStatement(month, summary, invoices, receipts);
    }

    /** UC129: xuất bảng kê tháng ra Excel hoặc PDF bằng bộ xuất chung của dự án. */
    @Transactional(readOnly = true)
    public byte[] export(String month, String format) {
        MonthlyStatement statement = get(month);
        boolean pdf = "pdf".equalsIgnoreCase(format);
        if (!pdf && !"xlsx".equalsIgnoreCase(format)) throw new BusinessException("Định dạng chỉ nhận xlsx hoặc pdf");
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("TỔNG HỢP", "Số hóa đơn", statement.summary().invoiceCount(), "Tổng phải thu", statement.summary().invoicedAmount()));
        rows.add(List.of("TỔNG HỢP", "Đã thu", statement.summary().collectedAmount(), "Còn nợ", statement.summary().remainingAmount()));
        rows.add(List.of("TỔNG HỢP", "Số phiếu thu", statement.summary().receiptCount(), "Tổng phiếu thu", statement.summary().receiptAmount()));
        rows.add(List.of("", "", "", "", ""));
        rows.add(List.of("HÓA ĐƠN", "Mã hóa đơn", "Người thuê", "Phòng", "Ngày phát hành"));
        for (InvoiceLine i : statement.invoices()) rows.add(List.of("HÓA ĐƠN", i.invoiceCode(), i.tenantName(), i.roomCode(), i.issueDate()));
        rows.add(List.of("", "", "", "", ""));
        rows.add(List.of("PHIẾU THU", "Mã phiếu", "Loại", "Số tiền", "Ngày tạo"));
        for (ReceiptLine r : statement.receipts()) rows.add(List.of("PHIẾU THU", r.voucherCode(), r.voucherType(), r.amount(), r.createdAt()));
        TableData data = new TableData("Bang ke thang " + month,
                List.of("Nhóm", "Mã / Nội dung", "Đối tượng / Loại", "Giá trị", "Ngày"), rows);
        return pdf ? exporter.toPdf(data) : exporter.toExcel(data);
    }

    private InvoiceLine invoiceLine(Invoice i) {
        BigDecimal paid = i.getPaidAmount() == null ? BigDecimal.ZERO : i.getPaidAmount();
        BigDecimal total = i.getTotalAmount() == null ? BigDecimal.ZERO : i.getTotalAmount();
        return new InvoiceLine(i.getInvoiceId(), i.getInvoiceCode(), i.getTenant().getFullName(),
                i.getCalculatedFee().getRoom().getRoomCode(), i.getIssueDate(), i.getDueDate(), total, paid,
                total.subtract(paid).max(BigDecimal.ZERO), i.getPaymentStatus());
    }
    private ReceiptLine receiptLine(ReceiptVoucher v) {
        return new ReceiptLine(v.getVoucherId(), v.getVoucherCode(), v.getVoucherType(), v.getAmount(),
                v.getCreatedAt(), v.getTransaction() == null ? null : v.getTransaction().getTransactionCode(),
                v.getTransaction() == null || v.getTransaction().getInvoice() == null ? null
                        : v.getTransaction().getInvoice().getInvoiceCode());
    }
}
