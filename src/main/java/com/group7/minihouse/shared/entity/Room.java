package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

/** Bang `rooms`. */
@DynamicInsert
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Integer roomId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", nullable = false)
    private PropertyFloor floor;

    @Column(name = "room_code", nullable = false, length = 50)
    private String roomCode;

    @Column(name = "area_sqm", nullable = false, precision = 5, scale = 2)
    private BigDecimal areaSqm;

    @Column(name = "max_occupants", nullable = false)
    private Integer maxOccupants;

    @Column(name = "base_rent_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal baseRentPrice;

    @Column(name = "deposit_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "initial_electric_meter", nullable = false)
    private Integer initialElectricMeter;

    @Column(name = "initial_water_meter", nullable = false)
    private Integer initialWaterMeter;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Room() {}

    public Integer getRoomId() { return roomId; }
    public void setRoomId(Integer roomId) { this.roomId = roomId; }

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public PropertyFloor getFloor() { return floor; }
    public void setFloor(PropertyFloor floor) { this.floor = floor; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public BigDecimal getAreaSqm() { return areaSqm; }
    public void setAreaSqm(BigDecimal areaSqm) { this.areaSqm = areaSqm; }

    public Integer getMaxOccupants() { return maxOccupants; }
    public void setMaxOccupants(Integer maxOccupants) { this.maxOccupants = maxOccupants; }

    public BigDecimal getBaseRentPrice() { return baseRentPrice; }
    public void setBaseRentPrice(BigDecimal baseRentPrice) { this.baseRentPrice = baseRentPrice; }

    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }

    public Integer getInitialElectricMeter() { return initialElectricMeter; }
    public void setInitialElectricMeter(Integer initialElectricMeter) { this.initialElectricMeter = initialElectricMeter; }

    public Integer getInitialWaterMeter() { return initialWaterMeter; }
    public void setInitialWaterMeter(Integer initialWaterMeter) { this.initialWaterMeter = initialWaterMeter; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
