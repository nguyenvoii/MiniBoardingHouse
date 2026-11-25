package com.group7.minihouse.chi.dto;

import java.math.BigDecimal;

public class RoomDTO {
    private Integer roomId;
    private Integer propertyId;
    private Integer floorId;
    private String floorName;
    private Integer floorNumber;
    private String roomCode;
    private Integer roomTypeId;
    private String roomTypeName;
    private boolean roomTypeSpecified;
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

    public Integer getFloorNumber() { return floorNumber; }
    public void setFloorNumber(Integer floorNumber) { this.floorNumber = floorNumber; }

    public String getFloorName() { return floorName; }
    public void setFloorName(String floorName) { this.floorName = floorName; }

    public Integer getRoomTypeId() { return roomTypeId; }
    public void setRoomTypeId(Integer roomTypeId) {
        this.roomTypeId = roomTypeId;
        roomTypeSpecified = true;
    }
    public boolean roomTypeSpecified() { return roomTypeSpecified; }
    public String getRoomTypeName() { return roomTypeName; }
    public void setRoomTypeName(String roomTypeName) { this.roomTypeName = roomTypeName; }

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
