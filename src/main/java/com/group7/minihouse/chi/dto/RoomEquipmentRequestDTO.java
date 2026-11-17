package com.group7.minihouse.chi.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RoomEquipmentRequestDTO {

    public static class Response {
        private Integer equipmentRequestId;
        private Integer roomId;
        private String roomCode;
        private Integer tenantId;
        private String tenantName;
        private String tenantPhone;
        private String equipmentName;
        private String requestType; // INSTALL, EXTRA_USAGE
        private BigDecimal estimatedMonthlyFee;
        private String status; // PENDING, APPROVED, REJECTED, INSTALLED, REMOVED
        private String note;
        private LocalDateTime createdAt;

        public Response() {}

        public Integer getEquipmentRequestId() { return equipmentRequestId; }
        public void setEquipmentRequestId(Integer equipmentRequestId) { this.equipmentRequestId = equipmentRequestId; }
        public Integer getRoomId() { return roomId; }
        public void setRoomId(Integer roomId) { this.roomId = roomId; }
        public String getRoomCode() { return roomCode; }
        public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
        public Integer getTenantId() { return tenantId; }
        public void setTenantId(Integer tenantId) { this.tenantId = tenantId; }
        public String getTenantName() { return tenantName; }
        public void setTenantName(String tenantName) { this.tenantName = tenantName; }
        public String getTenantPhone() { return tenantPhone; }
        public void setTenantPhone(String tenantPhone) { this.tenantPhone = tenantPhone; }
        public String getEquipmentName() { return equipmentName; }
        public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }
        public String getRequestType() { return requestType; }
        public void setRequestType(String requestType) { this.requestType = requestType; }
        public BigDecimal getEstimatedMonthlyFee() { return estimatedMonthlyFee; }
        public void setEstimatedMonthlyFee(BigDecimal estimatedMonthlyFee) { this.estimatedMonthlyFee = estimatedMonthlyFee; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class CreateRequest {
        private Integer roomId;
        private Integer tenantId;
        private String equipmentName;
        private String requestType; // INSTALL hoặc EXTRA_USAGE
        private BigDecimal estimatedMonthlyFee;
        private String note;

        public CreateRequest() {}

        public Integer getRoomId() { return roomId; }
        public void setRoomId(Integer roomId) { this.roomId = roomId; }
        public Integer getTenantId() { return tenantId; }
        public void setTenantId(Integer tenantId) { this.tenantId = tenantId; }
        public String getEquipmentName() { return equipmentName; }
        public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }
        public String getRequestType() { return requestType; }
        public void setRequestType(String requestType) { this.requestType = requestType; }
        public BigDecimal getEstimatedMonthlyFee() { return estimatedMonthlyFee; }
        public void setEstimatedMonthlyFee(BigDecimal estimatedMonthlyFee) { this.estimatedMonthlyFee = estimatedMonthlyFee; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public static class RejectRequest {
        private String reason;
        public RejectRequest() {}
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
