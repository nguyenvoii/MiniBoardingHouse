package com.group7.minihouse.phi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTO dashboard (UC116) va bao cao doanh thu - chi phi (UC118). */
public final class ReportDtos {

    private ReportDtos() {}

    /** UC116. propertyId null = tat ca khu tro. occupancyPercent lam tron 1 chu so thap phan. */
    public record Dashboard(Integer propertyId, LocalDate asOf, long totalRooms, long rentedRooms, long vacantRooms,
                            long otherRooms, BigDecimal occupancyPercent, BigDecimal revenueThisMonth,
                            BigDecimal billedThisMonth, BigDecimal expenseThisMonth, BigDecimal outstandingDebt,
                            long unpaidInvoices, long overdueInvoices, long expiringSoonContracts,
                            long contractsEndingIn30Days) {}

    public record MonthRow(int month, BigDecimal revenue, BigDecimal expense, BigDecimal profit) {}

    /** UC118: 12 dong thang 1-12 cua nam + tong. */
    public record RevenueReport(Integer propertyId, int year, List<MonthRow> months, BigDecimal totalRevenue,
                                BigDecimal totalExpense, BigDecimal totalProfit) {}
}
