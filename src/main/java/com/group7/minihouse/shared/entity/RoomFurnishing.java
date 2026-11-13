package com.group7.minihouse.shared.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

/** Bang `room_furnishings`. */
@DynamicInsert
@Entity
@Table(name = "room_furnishings")
public class RoomFurnishing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "furnishing_id")
    private Integer furnishingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "condition_status", nullable = false, length = 100)
    private String conditionStatus;

    @Column(name = "notes", length = 255)
    private String notes;

    public RoomFurnishing() {}

    public Integer getFurnishingId() { return furnishingId; }
    public void setFurnishingId(Integer furnishingId) { this.furnishingId = furnishingId; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getConditionStatus() { return conditionStatus; }
    public void setConditionStatus(String conditionStatus) { this.conditionStatus = conditionStatus; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

}
