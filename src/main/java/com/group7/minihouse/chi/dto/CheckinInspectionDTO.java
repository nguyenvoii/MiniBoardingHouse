package com.group7.minihouse.chi.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CheckinInspectionDTO {

    private Integer inspectionId;
    private Integer roomId;
    private String roomCode;
    private Integer tenantId;
    private String tenantName;
    private String tenantPhone;
    private Integer contractId;
    private String contractCode;
    private LocalDate checkinDate;
    private Integer initialElectricMeter;
    private Integer initialWaterMeter;
    private Integer keyHandedCount;
    private String furnishingsCondition;
    private String inspectionPhotoUrl;
    private String notes;
    private Boolean tenantConfirmed;
    private String inspectedByName;
    private LocalDateTime createdAt;

    public CheckinInspectionDTO() {}

    public Integer getInspectionId() { return inspectionId; }
    public void setInspectionId(Integer inspectionId) { this.inspectionId = inspectionId; }

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

    public Integer getContractId() { return contractId; }
    public void setContractId(Integer contractId) { this.contractId = contractId; }

    public String getContractCode() { return contractCode; }
    public void setContractCode(String contractCode) { this.contractCode = contractCode; }

    public LocalDate getCheckinDate() { return checkinDate; }
    public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }

    public Integer getInitialElectricMeter() { return initialElectricMeter; }
    public void setInitialElectricMeter(Integer initialElectricMeter) { this.initialElectricMeter = initialElectricMeter; }

    public Integer getInitialWaterMeter() { return initialWaterMeter; }
    public void setInitialWaterMeter(Integer initialWaterMeter) { this.initialWaterMeter = initialWaterMeter; }

    public Integer getKeyHandedCount() { return keyHandedCount; }
    public void setKeyHandedCount(Integer keyHandedCount) { this.keyHandedCount = keyHandedCount; }

    public String getFurnishingsCondition() { return furnishingsCondition; }
    public void setFurnishingsCondition(String furnishingsCondition) { this.furnishingsCondition = furnishingsCondition; }

    public String getInspectionPhotoUrl() { return inspectionPhotoUrl; }
    public void setInspectionPhotoUrl(String inspectionPhotoUrl) { this.inspectionPhotoUrl = inspectionPhotoUrl; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getTenantConfirmed() { return tenantConfirmed; }
    public void setTenantConfirmed(Boolean tenantConfirmed) { this.tenantConfirmed = tenantConfirmed; }

    public String getInspectedByName() { return inspectedByName; }
    public void setInspectedByName(String inspectedByName) { this.inspectedByName = inspectedByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static class CreateRequest {
        private Integer roomId;
        private Integer tenantId;
        private Integer contractId;
        private LocalDate checkinDate;
        private Integer initialElectricMeter;
        private Integer initialWaterMeter;
        private Integer keyHandedCount = 1;
        private String furnishingsCondition;
        private String inspectionPhotoUrl;
        private String notes;
        private Boolean tenantConfirmed = true;
        private String inspectedByName;

        public CreateRequest() {}

        public Integer getRoomId() { return roomId; }
        public void setRoomId(Integer roomId) { this.roomId = roomId; }

        public Integer getTenantId() { return tenantId; }
        public void setTenantId(Integer tenantId) { this.tenantId = tenantId; }

        public Integer getContractId() { return contractId; }
        public void setContractId(Integer contractId) { this.contractId = contractId; }

        public LocalDate getCheckinDate() { return checkinDate; }
        public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }

        public Integer getInitialElectricMeter() { return initialElectricMeter; }
        public void setInitialElectricMeter(Integer initialElectricMeter) { this.initialElectricMeter = initialElectricMeter; }

        public Integer getInitialWaterMeter() { return initialWaterMeter; }
        public void setInitialWaterMeter(Integer initialWaterMeter) { this.initialWaterMeter = initialWaterMeter; }

        public Integer getKeyHandedCount() { return keyHandedCount; }
        public void setKeyHandedCount(Integer keyHandedCount) { this.keyHandedCount = keyHandedCount; }

        public String getFurnishingsCondition() { return furnishingsCondition; }
        public void setFurnishingsCondition(String furnishingsCondition) { this.furnishingsCondition = furnishingsCondition; }

        public String getInspectionPhotoUrl() { return inspectionPhotoUrl; }
        public void setInspectionPhotoUrl(String inspectionPhotoUrl) { this.inspectionPhotoUrl = inspectionPhotoUrl; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        public Boolean getTenantConfirmed() { return tenantConfirmed; }
        public void setTenantConfirmed(Boolean tenantConfirmed) { this.tenantConfirmed = tenantConfirmed; }

        public String getInspectedByName() { return inspectedByName; }
        public void setInspectedByName(String inspectedByName) { this.inspectedByName = inspectedByName; }
    }
}
