package com.group7.minihouse.viet.service;

import static com.group7.minihouse.viet.service.VietSupport.nz;

import com.group7.minihouse.shared.entity.CheckoutRequest;
import com.group7.minihouse.shared.entity.DepositRefundVoucher;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableData;
import com.group7.minihouse.viet.dto.CheckoutReportDtos.CheckoutReportResponse;
import com.group7.minihouse.viet.dto.CheckoutReportDtos.CheckoutReportRow;
import com.group7.minihouse.viet.dto.CheckoutReportDtos.CheckoutReportTotals;
import com.group7.minihouse.viet.repository.DepositRefundVoucherRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC132: báo cáo hoàn tiền đặt cọc và quyết toán trả phòng theo tháng. */
@Service
public class CheckoutReportService {

    private static final List<String> HEADERS = List.of("Phòng", "Khách thuê", "Ngày trả", "Tiền cọc gốc",
            "Khấu trừ nợ", "Khấu trừ hư hỏng", "Hoàn thực tế", "Trạng thái phiếu", "Ngày hoàn");

    private final DepositRefundVoucherRepository voucherRepository;

    public CheckoutReportService(DepositRefundVoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    /** UC132: báo cáo một tháng, month có dạng yyyy-MM. */
    @Transactional(readOnly = true)
    public CheckoutReportResponse report(String month) {
        YearMonth ym = parseMonth(month);
        List<CheckoutReportRow> rows = voucherRepository.findForMonthReport(ym.atDay(1), ym.atEndOfMonth())
                .stream()
                .map(CheckoutReportService::toRow)
                .sorted(Comparator.comparing(CheckoutReportRow::checkoutDate)
                        .thenComparing(CheckoutReportRow::roomCode))
                .toList();
        return new CheckoutReportResponse(ym.toString(), rows, totals(rows));
    }

    /** UC132: dữ liệu để xuất Excel/PDF; dòng cuối là dòng tổng. */
    @Transactional(readOnly = true)
    public TableData exportTable(String month) {
        CheckoutReportResponse r = report(month);
        List<List<Object>> out = new ArrayList<>();
        for (CheckoutReportRow row : r.rows()) {
            out.add(java.util.Arrays.<Object>asList(row.roomCode(), row.tenantName(), row.checkoutDate(),
                    row.originalDeposit(), row.deductedDebt(), row.deductedDamage(), row.actualRefund(),
                    statusLabel(row.voucherStatus()), row.refundDate()));
        }
        CheckoutReportTotals t = r.totals();
        out.add(java.util.Arrays.<Object>asList("TỔNG (" + r.rows().size() + " phiếu)", null, null,
                t.totalDeposit(), t.totalDeductedDebt(), t.totalDeductedDamage(), t.totalRefunded(),
                "Chưa hoàn: " + t.pendingCount() + " phiếu (" + t.pendingAmount().toPlainString() + ")", null));
        return new TableData("Hoàn cọc " + r.month(), HEADERS, out);
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new BusinessException("Thiếu tháng báo cáo (định dạng yyyy-MM)");
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException("Tháng không hợp lệ, định dạng đúng là yyyy-MM (ví dụ 2026-11)");
        }
    }

    private static CheckoutReportRow toRow(DepositRefundVoucher v) {
        CheckoutRequest c = v.getCheckout();
        // Phiếu chờ hoàn chưa có ngày trả thực tế nên lấy ngày dự kiến.
        var date = c.getActualCheckoutDate() != null ? c.getActualCheckoutDate() : c.getExpectedCheckoutDate();
        return new CheckoutReportRow(c.getCheckoutId(), c.getContract().getRoom().getRoomCode(),
                c.getContract().getTenant().getFullName(), date, nz(v.getOriginalDepositAmount()),
                nz(v.getDeductedDebtAmount()), nz(v.getDeductedDamageAmount()), nz(v.getActualRefundAmount()),
                v.getStatus(), v.getRefundDate());
    }

    private static CheckoutReportTotals totals(List<CheckoutReportRow> rows) {
        BigDecimal deposit = BigDecimal.ZERO;
        BigDecimal debt = BigDecimal.ZERO;
        BigDecimal damage = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        long pendingCount = 0;
        for (CheckoutReportRow r : rows) {
            deposit = deposit.add(r.originalDeposit());
            debt = debt.add(r.deductedDebt());
            damage = damage.add(r.deductedDamage());
            if ("REFUNDED".equals(r.voucherStatus())) {
                refunded = refunded.add(r.actualRefund());
            } else {
                pendingCount++;
                pendingAmount = pendingAmount.add(r.actualRefund());
            }
        }
        return new CheckoutReportTotals(deposit, debt, damage, refunded, pendingCount, pendingAmount);
    }

    private static String statusLabel(String status) {
        return "REFUNDED".equals(status) ? "Đã hoàn" : "Chờ hoàn";
    }
}
