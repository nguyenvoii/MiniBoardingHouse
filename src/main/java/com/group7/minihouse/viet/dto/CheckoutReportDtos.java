package com.group7.minihouse.viet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTO cho báo cáo hoàn cọc và quyết toán trả phòng theo tháng (UC132). */
public final class CheckoutReportDtos {

    private CheckoutReportDtos() {
    }

    /**
     * Một dòng báo cáo ứng với một phiếu hoàn cọc. checkoutDate là ngày trả thực tế (đã thanh lý),
     * hoặc ngày trả dự kiến nếu phiếu còn chờ hoàn.
     */
    public record CheckoutReportRow(Integer checkoutId, String roomCode, String tenantName,
                                    LocalDate checkoutDate, BigDecimal originalDeposit,
                                    BigDecimal deductedDebt, BigDecimal deductedDamage,
                                    BigDecimal actualRefund, String voucherStatus, LocalDateTime refundDate) {
    }

    /** Dòng tổng; pendingCount/pendingAmount để nhắc các phiếu còn phải hoàn tiền. */
    public record CheckoutReportTotals(BigDecimal totalDeposit, BigDecimal totalDeductedDebt,
                                       BigDecimal totalDeductedDamage, BigDecimal totalRefunded,
                                       long pendingCount, BigDecimal pendingAmount) {
    }

    public record CheckoutReportResponse(String month, List<CheckoutReportRow> rows,
                                         CheckoutReportTotals totals) {
    }
}
