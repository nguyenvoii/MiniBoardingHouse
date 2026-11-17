package com.group7.minihouse.chi.dto;

import java.time.LocalDate;

public class RoomResidentDTO {
    private Integer residentId;
    private Integer roomId;
    private String roomCode;
    private Integer tenantId;
    private String fullName;
    private String phoneNumber;
    private String identityNumber;
    private String relationshipWithPrimary;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isActive;

    public RoomResidentDTO() {}

    public Integer getResidentId() { return residentId; }
    public void setResidentId(Integer residentId) { this.residentId = residentId; }

    public Integer getRoomId() { return roomId; }
    public void setRoomId(Integer roomId) { this.roomId = roomId; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public Integer getTenantId() { return tenantId; }
    public void setTenantId(Integer tenantId) { this.tenantId = tenantId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getIdentityNumber() { return identityNumber; }
    public void setIdentityNumber(String identityNumber) { this.identityNumber = identityNumber; }

    public String getRelationshipWithPrimary() { return relationshipWithPrimary; }
    public void setRelationshipWithPrimary(String relationshipWithPrimary) { this.relationshipWithPrimary = relationshipWithPrimary; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public static class CreateRequest {
        private Integer roomId;
        private Integer tenantId;
        private String fullName;
        private String phoneNumber;
        private String identityNumber;
        private String relationshipWithPrimary;
        private LocalDate startDate;

        public CreateRequest() {}

        public Integer getRoomId() { return roomId; }
        public void setRoomId(Integer roomId) { this.roomId = roomId; }

        public Integer getTenantId() { return tenantId; }
        public void setTenantId(Integer tenantId) { this.tenantId = tenantId; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public String getIdentityNumber() { return identityNumber; }
        public void setIdentityNumber(String identityNumber) { this.identityNumber = identityNumber; }

        public String getRelationshipWithPrimary() { return relationshipWithPrimary; }
        public void setRelationshipWithPrimary(String relationshipWithPrimary) { this.relationshipWithPrimary = relationshipWithPrimary; }

        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    }
}
