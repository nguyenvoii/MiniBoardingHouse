package com.group7.minihouse.viet.dto;

import com.group7.minihouse.shared.entity.IncidentCategory;
import com.group7.minihouse.shared.entity.RoomAddonFee;
import com.group7.minihouse.shared.entity.RoomEquipmentRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO cho nhom su co/thiet bi (UC113), yeu cau thiet bi va phu phi phong. */
public final class EquipmentDtos {

    private EquipmentDtos() {
    }

    /** categoryType: INCIDENT hoac EQUIPMENT. */
    public record CategoryRequest(String categoryCode, String categoryName, String categoryType, String description) {
    }

    public record ActiveRequest(Boolean active) {
    }

    public record CategoryResponse(Integer categoryId, String categoryCode, String categoryName, String categoryType,
                                   String description, Boolean isActive) {
        public static CategoryResponse from(IncidentCategory c) {
            return new CategoryResponse(c.getCategoryId(), c.getCategoryCode(), c.getCategoryName(),
                    c.getCategoryType(), c.getDescription(), c.getIsActive());
        }
    }

    /** requestType: INSTALL (lap moi) hoac EXTRA_USAGE (dung them). */
    public record CreateEquipmentRequest(Integer roomId, Integer categoryId, String equipmentName,
                                         String requestType, BigDecimal estimatedMonthlyFee) {
    }

    public record ReviewRequest(Boolean approve, String note) {
    }

    /** Tat ca truong tuy chon: mac dinh lay ten thiet bi, phi du kien, chu ky 1 thang. */
    public record InstallRequest(String feeName, BigDecimal amount, Integer billingCycleMonths) {
    }

    public record EquipmentResponse(Integer equipmentRequestId, Integer roomId, String roomCode, Integer tenantId,
                                    String tenantName, Integer categoryId, String equipmentName, String requestType,
                                    BigDecimal estimatedMonthlyFee, String status, Integer reviewedBy, String note,
                                    LocalDateTime createdAt) {
        public static EquipmentResponse from(RoomEquipmentRequest r) {
            return new EquipmentResponse(r.getEquipmentRequestId(), r.getRoom().getRoomId(),
                    r.getRoom().getRoomCode(), r.getTenant().getTenantId(), r.getTenant().getFullName(),
                    r.getCategory() == null ? null : r.getCategory().getCategoryId(), r.getEquipmentName(),
                    r.getRequestType(), r.getEstimatedMonthlyFee(), r.getStatus(),
                    r.getReviewedBy() == null ? null : r.getReviewedBy().getUserId(), r.getNote(),
                    r.getCreatedAt());
        }
    }

    public record AddonFeeResponse(Integer addonFeeId, Integer roomId, String roomCode, Integer equipmentRequestId,
                                   String feeName, BigDecimal amount, Integer billingCycleMonths,
                                   LocalDate startDate, LocalDate endDate, Boolean isActive) {
        public static AddonFeeResponse from(RoomAddonFee f) {
            return new AddonFeeResponse(f.getAddonFeeId(), f.getRoom().getRoomId(), f.getRoom().getRoomCode(),
                    f.getEquipmentRequest() == null ? null : f.getEquipmentRequest().getEquipmentRequestId(),
                    f.getFeeName(), f.getAmount(), f.getBillingCycleMonths(), f.getStartDate(), f.getEndDate(),
                    f.getIsActive());
        }
    }
}
