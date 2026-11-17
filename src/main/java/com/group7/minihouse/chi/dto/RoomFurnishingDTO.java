package com.group7.minihouse.chi.dto;

public class RoomFurnishingDTO {
    private Integer furnishingId;
    private Integer roomId;
    private String roomCode;
    private String itemName;
    private Integer quantity;
    private String conditionStatus;
    private String notes;

    public RoomFurnishingDTO() {}

    public Integer getFurnishingId() { return furnishingId; }
    public void setFurnishingId(Integer furnishingId) { this.furnishingId = furnishingId; }

    public Integer getRoomId() { return roomId; }
    public void setRoomId(Integer roomId) { this.roomId = roomId; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getConditionStatus() { return conditionStatus; }
    public void setConditionStatus(String conditionStatus) { this.conditionStatus = conditionStatus; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
