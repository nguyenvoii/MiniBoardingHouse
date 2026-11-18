package com.group7.minihouse.toan.service;

import com.group7.minihouse.shared.entity.PaymentTransaction;
import com.group7.minihouse.shared.entity.ReceiptVoucher;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableData;
import com.group7.minihouse.shared.export.TableExporter;
import com.group7.minihouse.toan.dto.PaymentReportDtos.*;
import com.group7.minihouse.toan.repository.PaymentReportRepository;
import com.group7.minihouse.toan.repository.ReceiptVoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC127: tổng hợp và đối soát tiền thu theo ngày, người thu và phương thức. */
@Service
public class PaymentReportService {
    private final PaymentReportRepository transactions;
    private final ReceiptVoucherRepository vouchers;
    private final TableExporter exporter;

    public PaymentReportService(PaymentReportRepository transactions, ReceiptVoucherRepository vouchers,
                                TableExporter exporter) {
        this.transactions = transactions;
        this.vouchers = vouchers;
        this.exporter = exporter;
    }

    /** UC127: lấy báo cáo trong khoảng ngày bao gồm cả ngày kết thúc. */
    @Transactional(readOnly = true)
    public DailyPaymentReport report(LocalDate fromDate, LocalDate toDate) {
        validate(fromDate, toDate);
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.plusDays(1).atStartOfDay();
        List<DailyPaymentRow> rows = transactions.summarizeSuccessful(from, to).stream()
                .map(r -> new DailyPaymentRow(toDate(r[0]), ((Number) r[1]).intValue(), (String) r[2],
                        (String) r[3], ((Number) r[4]).longValue(), money(r[5])))
                .toList();
        long successCount = 0, reversedCount = 0, failedCount = 0;
        BigDecimal successAmount = BigDecimal.ZERO, reversedAmount = BigDecimal.ZERO;
        for (Object[] r : transactions.summarizeStatuses(from, to)) {
            String status = (String) r[0];
            long count = ((Number) r[1]).longValue();
            BigDecimal amount = money(r[2]);
            if ("SUCCESS".equals(status)) { successCount = count; successAmount = amount; }
            if ("REVERSED".equals(status)) { reversedCount = count; reversedAmount = amount; }
            if ("FAILED".equals(status)) failedCount = count;
        }
        BigDecimal receiptAmount = vouchers.sumSuccessfulReceipts(from, to);
        if (receiptAmount == null) receiptAmount = BigDecimal.ZERO;
        BigDecimal net = successAmount.subtract(reversedAmount);
        DailyPaymentSummary summary = new DailyPaymentSummary(fromDate, toDate, successCount, reversedCount,
                failedCount, successAmount, reversedAmount, net, receiptAmount, net.subtract(receiptAmount));
        return new DailyPaymentReport(summary, rows);
    }

    /** UC127: xuất Excel báo cáo thu tiền và đối soát. */
    @Transactional(readOnly = true)
    public byte[] exportExcel(LocalDate from, LocalDate to) {
        DailyPaymentReport report = report(from, to);
        List<List<Object>> rows = new ArrayList<>();
        for (DailyPaymentRow r : report.rows()) rows.add(List.of(r.paymentDate(), r.receiverName(),
                r.paymentMethod(), r.transactionCount(), r.totalAmount()));
        DailyPaymentSummary s = report.summary();
        rows.add(List.of("TỔNG KẾT", "Giao dịch thành công", s.successfulTransactions(), "Số tiền", s.successfulAmount()));
        rows.add(List.of("TỔNG KẾT", "Giao dịch đảo", s.reversedTransactions(), "Số tiền", s.reversedAmount()));
        rows.add(List.of("ĐỐI SOÁT", "Tiền thực thu", s.netCollectedAmount(), "Phiếu thu", s.receiptAmount()));
        rows.add(List.of("ĐỐI SOÁT", "Chênh lệch", s.difference(), "", ""));
        return exporter.toExcel(new TableData("Bao cao thu tien", List.of("Ngày", "Người thu", "Phương thức", "Số giao dịch", "Tổng tiền"), rows));
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new BusinessException("Vui lòng chọn ngày bắt đầu và ngày kết thúc");
        if (from.isAfter(to)) throw new BusinessException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        if (to.isAfter(from.plusMonths(12))) throw new BusinessException("Khoảng thời gian báo cáo không được vượt quá 12 tháng");
    }
    private static BigDecimal money(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
    private static LocalDate toDate(Object value) {
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        return LocalDate.parse(value.toString());
    }
}
