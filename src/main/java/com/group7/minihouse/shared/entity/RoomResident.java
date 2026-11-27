package com.group7.minihouse.shared.entity;

import java.time.LocalDate;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

/** Bang `room_residents`. */
@DynamicInsert
@Entity
@Table(name = "room_residents")
public class RoomResident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resident_id")
    private Integer residentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "identity_number", length = 20)
    private String identityNumber;

    @Column(name = "relationship_with_primary", length = 50)
    private String relationshipWithPrimary;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "permanent_address", length = 255)
    private String permanentAddress;

    @Column(name = "temp_residence_registered_on")
    private LocalDate tempResidenceRegisteredOn;

    @Column(name = "id_front_image_url", length = 500)
    private String idFrontImageUrl;

    @Column(name = "id_back_image_url", length = 500)
    private String idBackImageUrl;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active")
    private Boolean isActive;

    public RoomResident() {}

    public String getIdFrontImageUrl() { return idFrontImageUrl; }
    public void setIdFrontImageUrl(String idFrontImageUrl) { this.idFrontImageUrl = idFrontImageUrl; }

    public String getIdBackImageUrl() { return idBackImageUrl; }
    public void setIdBackImageUrl(String idBackImageUrl) { this.idBackImageUrl = idBackImageUrl; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getPermanentAddress() { return permanentAddress; }
    public void setPermanentAddress(String permanentAddress) { this.permanentAddress = permanentAddress; }

    public LocalDate getTempResidenceRegisteredOn() { return tempResidenceRegisteredOn; }
    public void setTempResidenceRegisteredOn(LocalDate tempResidenceRegisteredOn) { this.tempResidenceRegisteredOn = tempResidenceRegisteredOn; }

    public Integer getResidentId() { return residentId; }
    public void setResidentId(Integer residentId) { this.residentId = residentId; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

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

}
