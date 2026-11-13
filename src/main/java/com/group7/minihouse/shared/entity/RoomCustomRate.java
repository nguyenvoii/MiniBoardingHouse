package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;

/** Bang `room_custom_rates`. */
@Entity
@Table(name = "room_custom_rates")
public class RoomCustomRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "custom_rate_id")
    private Integer customRateId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "rate_name", nullable = false, length = 100)
    private String rateName;

    @Column(name = "custom_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal customPrice;

    @Column(name = "notes", length = 255)
    private String notes;

    public RoomCustomRate() {}

    public Integer getCustomRateId() { return customRateId; }
    public void setCustomRateId(Integer customRateId) { this.customRateId = customRateId; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public String getRateName() { return rateName; }
    public void setRateName(String rateName) { this.rateName = rateName; }

    public BigDecimal getCustomPrice() { return customPrice; }
    public void setCustomPrice(BigDecimal customPrice) { this.customPrice = customPrice; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

}
