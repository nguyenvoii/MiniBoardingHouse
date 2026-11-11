package com.group7.minihouse.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `meter_readings`. */
@Entity
@Table(name = "meter_readings")
public class MeterReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reading_id")
    private Integer readingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private UtilityReadingPeriod period;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "old_electric", nullable = false)
    private Integer oldElectric;

    @Column(name = "new_electric", nullable = false)
    private Integer newElectric;

    @Column(name = "used_electric", insertable = false, updatable = false)
    private Integer usedElectric;

    @Column(name = "old_water", nullable = false)
    private Integer oldWater;

    @Column(name = "new_water", nullable = false)
    private Integer newWater;

    @Column(name = "used_water", insertable = false, updatable = false)
    private Integer usedWater;

    @Column(name = "reading_date", nullable = false)
    private LocalDateTime readingDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private User recordedByUser;

    @Column(name = "is_verified")
    private Boolean isVerified;

    public MeterReading() {}

    public Integer getReadingId() { return readingId; }
    public void setReadingId(Integer readingId) { this.readingId = readingId; }

    public UtilityReadingPeriod getPeriod() { return period; }
    public void setPeriod(UtilityReadingPeriod period) { this.period = period; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public Integer getOldElectric() { return oldElectric; }
    public void setOldElectric(Integer oldElectric) { this.oldElectric = oldElectric; }

    public Integer getNewElectric() { return newElectric; }
    public void setNewElectric(Integer newElectric) { this.newElectric = newElectric; }

    public Integer getUsedElectric() { return usedElectric; }

    public Integer getOldWater() { return oldWater; }
    public void setOldWater(Integer oldWater) { this.oldWater = oldWater; }

    public Integer getNewWater() { return newWater; }
    public void setNewWater(Integer newWater) { this.newWater = newWater; }

    public Integer getUsedWater() { return usedWater; }

    public LocalDateTime getReadingDate() { return readingDate; }
    public void setReadingDate(LocalDateTime readingDate) { this.readingDate = readingDate; }

    public User getRecordedByUser() { return recordedByUser; }
    public void setRecordedByUser(User recordedByUser) { this.recordedByUser = recordedByUser; }

    public Boolean getIsVerified() { return isVerified; }
    public void setIsVerified(Boolean isVerified) { this.isVerified = isVerified; }

}
