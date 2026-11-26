package com.group7.minihouse.chi.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TenantDTO {
    private Integer tenantId;
    private String fullName;
    private String phoneNumber;
    private String email;
    private LocalDate dob;
    private String identityNumber;
    private String permanentAddress;
    private LocalDate residenceStartDate;
    private LocalDate residenceEndDate;
    private String occupation;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private Boolean isVerified;
    private LocalDateTime createdAt;
    private String frontImageUrl;
    private String backImageUrl;

    public TenantDTO() {}

    public Integer getTenantId() { return tenantId; }
    public void setTenantId(Integer tenantId) { this.tenantId = tenantId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }

    public String getIdentityNumber() { return identityNumber; }
    public void setIdentityNumber(String identityNumber) { this.identityNumber = identityNumber; }

    public String getPermanentAddress() { return permanentAddress; }
    public void setPermanentAddress(String permanentAddress) { this.permanentAddress = permanentAddress; }

    public LocalDate getResidenceStartDate() { return residenceStartDate; }
    public void setResidenceStartDate(LocalDate value) { this.residenceStartDate = value; }

    public LocalDate getResidenceEndDate() { return residenceEndDate; }
    public void setResidenceEndDate(LocalDate value) { this.residenceEndDate = value; }

    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }

    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }

    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }

    public Boolean getIsVerified() { return isVerified; }
    public void setIsVerified(Boolean isVerified) { this.isVerified = isVerified; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getFrontImageUrl() { return frontImageUrl; }
    public void setFrontImageUrl(String frontImageUrl) { this.frontImageUrl = frontImageUrl; }

    public String getBackImageUrl() { return backImageUrl; }
    public void setBackImageUrl(String backImageUrl) { this.backImageUrl = backImageUrl; }

    public static class CreateRequest {
        private String fullName;
        private String phoneNumber;
        private String email;
        private LocalDate dob;
        private String identityNumber;
        private String permanentAddress;
        private LocalDate residenceStartDate;
        private LocalDate residenceEndDate;
        private String occupation;
        private String emergencyContactName;
        private String emergencyContactPhone;

        public CreateRequest() {}

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public LocalDate getDob() { return dob; }
        public void setDob(LocalDate dob) { this.dob = dob; }

        public String getIdentityNumber() { return identityNumber; }
        public void setIdentityNumber(String identityNumber) { this.identityNumber = identityNumber; }

        public String getPermanentAddress() { return permanentAddress; }
        public void setPermanentAddress(String permanentAddress) { this.permanentAddress = permanentAddress; }

        public LocalDate getResidenceStartDate() { return residenceStartDate; }
        public void setResidenceStartDate(LocalDate value) { this.residenceStartDate = value; }

        public LocalDate getResidenceEndDate() { return residenceEndDate; }
        public void setResidenceEndDate(LocalDate value) { this.residenceEndDate = value; }

        public String getOccupation() { return occupation; }
        public void setOccupation(String occupation) { this.occupation = occupation; }

        public String getEmergencyContactName() { return emergencyContactName; }
        public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }

        public String getEmergencyContactPhone() { return emergencyContactPhone; }
        public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    }
}
