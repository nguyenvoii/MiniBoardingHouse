package com.group7.minihouse.viet.dto;

import com.group7.minihouse.shared.entity.MaintenanceLog;
import com.group7.minihouse.shared.entity.MaintenanceRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** DTO cho su co, yeu cau sua chua (UC110-115). */
public final class MaintenanceDtos {

    private MaintenanceDtos() {
    }

    /** category = category_code cua nhom su co (UC113). roomId chi can khi nguoi thue co nhieu phong. */
    public record CreateMaintenanceRequest(Integer roomId, Integer tenantId, String category, String description,
                                           String issuePhotoUrl, String priority) {
    }

    public record AssignRequest(Integer staffId) {
    }

    public record ResolveRequest(BigDecimal repairCost, String resolutionNotes) {
    }

    public record RatingRequest(Integer rating) {
    }

    /** UC111: giam tien hoa don sau khi kiem tra cong to/dong ho nuoc cho thay hoa don tinh sai. */
    public record InvoiceAdjustmentRequest(Integer invoiceId, BigDecimal reduceAmount, String reason) {
    }

    public record InvoiceAdjustmentResponse(Integer requestId, Integer invoiceId, String invoiceCode,
                                            BigDecimal oldTotal, BigDecimal newTotal, BigDecimal paidAmount,
                                            BigDecimal remainingAmount, String paymentStatus) {
    }

    public record LogResponse(Integer maintenanceLogId, Integer assignedStaffId, String assignedStaffName,
                              BigDecimal repairCost, Integer tenantRating, String resolutionNotes,
                              LocalDateTime completedAt) {
        public static LogResponse from(MaintenanceLog l) {
            return new LogResponse(l.getMaintenanceLogId(),
                    l.getAssignedStaff() == null ? null : l.getAssignedStaff().getUserId(),
                    l.getAssignedStaff() == null ? null : l.getAssignedStaff().getFullName(),
                    l.getRepairCost(), l.getTenantRating(), l.getResolutionNotes(), l.getCompletedAt());
        }
    }

    public record MaintenanceResponse(Integer requestId, Integer roomId, String roomCode, Integer tenantId,
                                      String tenantName, String category, String description, String issuePhotoUrl,
                                      String priority, String status, LocalDateTime createdAt, LogResponse log) {
        public static MaintenanceResponse from(MaintenanceRequest r, MaintenanceLog log) {
            return new MaintenanceResponse(r.getRequestId(), r.getRoom().getRoomId(), r.getRoom().getRoomCode(),
                    r.getTenant() == null ? null : r.getTenant().getTenantId(),
                    r.getTenant() == null ? null : r.getTenant().getFullName(), r.getCategory(),
                    r.getDescription(), r.getIssuePhotoUrl(), r.getPriority(), r.getStatus(), r.getCreatedAt(),
                    log == null ? null : LogResponse.from(log));
        }
    }
}
