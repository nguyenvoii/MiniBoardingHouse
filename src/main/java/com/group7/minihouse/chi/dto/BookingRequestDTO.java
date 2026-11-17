package com.group7.minihouse.chi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class BookingRequestDTO {

    public static class Response {
        private Integer bookingId;
        private Integer roomId;
        private String roomCode;
        private Integer tenantId;
        private String tenantName;
        private String tenantPhone;
        private LocalDate expectedStartDate;
        private Integer occupantsCount;
        private BigDecimal depositPaid;
        private String status;
        private String rejectionReason;
        private LocalDateTime createdAt;

        public Integer getBookingId() { return bookingId; }
        public void setBookingId(Integer bookingId) { this.bookingId = bookingId; }
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
        public LocalDate getExpectedStartDate() { return expectedStartDate; }
        public void setExpectedStartDate(LocalDate expectedStartDate) { this.expectedStartDate = expectedStartDate; }
        public Integer getOccupantsCount() { return occupantsCount; }
        public void setOccupantsCount(Integer occupantsCount) { this.occupantsCount = occupantsCount; }
        public BigDecimal getDepositPaid() { return depositPaid; }
        public void setDepositPaid(BigDecimal depositPaid) { this.depositPaid = depositPaid; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class CreateRequest {
        private Integer roomId;
        private Integer tenantId;
        private LocalDate expectedStartDate;
        private Integer occupantsCount;
        private BigDecimal depositPaid;

        public Integer getRoomId() { return roomId; }
        public void setRoomId(Integer roomId) { this.roomId = roomId; }
        public Integer getTenantId() { return tenantId; }
        public void setTenantId(Integer tenantId) { this.tenantId = tenantId; }
        public LocalDate getExpectedStartDate() { return expectedStartDate; }
        public void setExpectedStartDate(LocalDate expectedStartDate) { this.expectedStartDate = expectedStartDate; }
        public Integer getOccupantsCount() { return occupantsCount; }
        public void setOccupantsCount(Integer occupantsCount) { this.occupantsCount = occupantsCount; }
        public BigDecimal getDepositPaid() { return depositPaid; }
        public void setDepositPaid(BigDecimal depositPaid) { this.depositPaid = depositPaid; }
    }

    public static class RejectRequest {
        private String rejectionReason;
        public String getRejectionReason() { return rejectionReason; }
        public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    }

    public static class DepositRequest {
        private BigDecimal depositPaid;
        public BigDecimal getDepositPaid() { return depositPaid; }
        public void setDepositPaid(BigDecimal depositPaid) { this.depositPaid = depositPaid; }
    }
}

