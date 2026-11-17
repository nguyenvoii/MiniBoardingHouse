package com.group7.minihouse.chi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class RoomAddonFeeDTO {

    public static class Response {
        private Integer addonFeeId;
        private Integer roomId;
        private String roomCode;
        private Integer equipmentRequestId;
        private String equipmentName;
        private String feeName;
        private BigDecimal amount;
        private Integer billingCycleMonths;
        private LocalDate startDate;
        private LocalDate endDate;
        private Boolean isActive;

        public Response() {}

        public Integer getAddonFeeId() { return addonFeeId; }
        public void setAddonFeeId(Integer addonFeeId) { this.addonFeeId = addonFeeId; }
        public Integer getRoomId() { return roomId; }
        public void setRoomId(Integer roomId) { this.roomId = roomId; }
        public String getRoomCode() { return roomCode; }
        public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
        public Integer getEquipmentRequestId() { return equipmentRequestId; }
        public void setEquipmentRequestId(Integer equipmentRequestId) { this.equipmentRequestId = equipmentRequestId; }
        public String getEquipmentName() { return equipmentName; }
        public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }
        public String getFeeName() { return feeName; }
        public void setFeeName(String feeName) { this.feeName = feeName; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public Integer getBillingCycleMonths() { return billingCycleMonths; }
        public void setBillingCycleMonths(Integer billingCycleMonths) { this.billingCycleMonths = billingCycleMonths; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }

    public static class CreateRequest {
        private Integer roomId;
        private Integer equipmentRequestId;
        private String feeName;
        private BigDecimal amount;
        private Integer billingCycleMonths;
        private LocalDate startDate;
        private LocalDate endDate;
        private Boolean isActive;

        public CreateRequest() {}

        public Integer getRoomId() { return roomId; }
        public void setRoomId(Integer roomId) { this.roomId = roomId; }
        public Integer getEquipmentRequestId() { return equipmentRequestId; }
        public void setEquipmentRequestId(Integer equipmentRequestId) { this.equipmentRequestId = equipmentRequestId; }
        public String getFeeName() { return feeName; }
        public void setFeeName(String feeName) { this.feeName = feeName; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public Integer getBillingCycleMonths() { return billingCycleMonths; }
        public void setBillingCycleMonths(Integer billingCycleMonths) { this.billingCycleMonths = billingCycleMonths; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }
}
