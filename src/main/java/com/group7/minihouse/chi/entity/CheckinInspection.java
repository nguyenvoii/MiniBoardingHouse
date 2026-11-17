package com.group7.minihouse.chi.entity;

import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * UC 124: Biên bản nhận phòng / bàn giao khi bắt đầu thuê phòng trọ
 */
@Entity
@Table(name = "checkin_inspections")
public class CheckinInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Integer inspectionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private RentalContract contract;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(name = "initial_electric_meter", nullable = false)
    private Integer initialElectricMeter;

    @Column(name = "initial_water_meter", nullable = false)
    private Integer initialWaterMeter;

    @Column(name = "key_handed_count", nullable = false)
    private Integer keyHandedCount = 1;

    @Column(name = "furnishings_condition", columnDefinition = "NVARCHAR(MAX)")
    private String furnishingsCondition;

    @Column(name = "inspection_photo_url", length = 500)
    private String inspectionPhotoUrl;

    @Column(name = "notes", columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "tenant_confirmed", nullable = false)
    private Boolean tenantConfirmed = true;

    @Column(name = "inspected_by_name", length = 100)
    private String inspectedByName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public CheckinInspection() {}

    public Integer getInspectionId() { return inspectionId; }
    public void setInspectionId(Integer inspectionId) { this.inspectionId = inspectionId; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public RentalContract getContract() { return contract; }
    public void setContract(RentalContract contract) { this.contract = contract; }

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
}
