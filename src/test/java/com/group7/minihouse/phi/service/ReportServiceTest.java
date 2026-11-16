package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.group7.minihouse.phi.dto.ReportDtos.RevenueReport;
import com.group7.minihouse.shared.export.TableData;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** UC116/118: cong thuc dashboard va bao cao (khong can DB). */
class ReportServiceTest {

    @Test
    void occupancyIsRoundedToOneDecimalAndSafeWithNoRooms() {
        assertEquals(new BigDecimal("66.7"), ReportService.occupancy(2, 3));
        assertEquals(new BigDecimal("100.0"), ReportService.occupancy(5, 5));
        assertEquals(BigDecimal.ZERO, ReportService.occupancy(0, 0));
    }

    @Test
    void reportAlwaysHasTwelveMonthsWithZeroForMissingOnesAndCorrectTotals() {
        RevenueReport r = ReportService.buildReport(1, 2026,
                Map.of(1, new BigDecimal("10000000"), 3, new BigDecimal("5000000")),
                Map.of(1, new BigDecimal("2000000"), 2, new BigDecimal("1500000")));
        assertEquals(12, r.months().size());
        assertEquals(new BigDecimal("8000000"), r.months().get(0).profit());
        assertEquals(new BigDecimal("-1500000"), r.months().get(1).profit()); // thang 2 chi khong thu
        assertEquals(BigDecimal.ZERO, r.months().get(11).revenue());
        assertEquals(new BigDecimal("15000000"), r.totalRevenue());
        assertEquals(new BigDecimal("3500000"), r.totalExpense());
        assertEquals(new BigDecimal("11500000"), r.totalProfit());
    }

    @Test
    void exportTableHasTwelveMonthRowsPlusTotalRow() {
        RevenueReport r = ReportService.buildReport(null, 2026, Map.of(), Map.of());
        TableData t = ReportService.toTable(r);
        assertEquals(4, t.headers().size());
        assertEquals(13, t.rows().size());
        assertEquals("Cả năm", t.rows().get(12).get(0));
    }
}
