package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

/** Bang `room_addon_fees`. */
@DynamicInsert
@Entity
@Table(name = "room_addon_fees")
public class RoomAddonFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "addon_fee_id")
    private Integer addonFeeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "equipment_request_id")
    private RoomEquipmentRequest equipmentRequest;

    @Column(name = "fee_name", nullable = false, length = 100)
    private String feeName;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "billing_cycle_months", nullable = false)
    private Integer billingCycleMonths;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    public RoomAddonFee() {}

    public Integer getAddonFeeId() { return addonFeeId; }
    public void setAddonFeeId(Integer addonFeeId) { this.addonFeeId = addonFeeId; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public RoomEquipmentRequest getEquipmentRequest() { return equipmentRequest; }
    public void setEquipmentRequest(RoomEquipmentRequest equipmentRequest) { this.equipmentRequest = equipmentRequest; }

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
