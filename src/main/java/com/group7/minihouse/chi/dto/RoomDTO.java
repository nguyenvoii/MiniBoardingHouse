package com.group7.minihouse.chi.dto;

import java.math.BigDecimal;

public class RoomDTO {
    private Integer roomId;
    private Integer propertyId;
    private Integer floorId;
    private String floorName;
    private String roomCode;
    private BigDecimal areaSqm;
    private Integer maxOccupants;
    private BigDecimal baseRentPrice;
    private BigDecimal depositAmount;
    private Integer initialElectricMeter;
    private Integer initialWaterMeter;
    private String status;

    public RoomDTO() {}

    public Integer getRoomId() { return roomId; }
    public void setRoomId(Integer roomId) { this.roomId = roomId; }

    public Integer getPropertyId() { return propertyId; }
    public void setPropertyId(Integer propertyId) { this.propertyId = propertyId; }

    public Integer getFloorId() { return floorId; }
    public void setFloorId(Integer floorId) { this.floorId = floorId; }

    public String getFloorName() { return floorName; }
    public void setFloorName(String floorName) { this.floorName = floorName; }

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
}
