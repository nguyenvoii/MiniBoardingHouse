package com.group7.minihouse.viet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTO cho thống kê sự cố và lịch sử bảo trì theo phòng (UC131). */
public final class MaintenanceStatsDtos {

    private MaintenanceStatsDtos() {
    }

    public record CountItem(String key, long count) {
    }

    public record CategoryStat(String category, String categoryName, long count, BigDecimal totalCost,
                               Double avgRating) {
    }

    public record MonthStat(String month, long count) {
    }

    public record RoomStat(Integer roomId, String roomCode, long count) {
    }

    /** avgResolutionHours = trung bình (completed_at - created_at), null nếu chưa có sự cố nào xử lý xong. */
    public record MaintenanceStatsResponse(LocalDate from, LocalDate to, long total, BigDecimal totalCost,
                                           Double avgResolutionHours, List<CountItem> byStatus,
                                           List<CategoryStat> byCategory, List<MonthStat> byMonth,
                                           List<RoomStat> topRooms) {
    }

    public record MaintenanceHistoryItem(Integer requestId, String category, String categoryName,
                                         String description, String priority, String status,
                                         LocalDateTime createdAt, String assignedStaffName, BigDecimal repairCost,
                                         Integer tenantRating, LocalDateTime completedAt,
                                         Double resolutionHours) {
    }
}
