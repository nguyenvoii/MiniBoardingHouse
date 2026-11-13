package com.group7.minihouse.shared.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `meter_adjustment_logs`. */
@Entity
@Table(name = "meter_adjustment_logs")
public class MeterAdjustmentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "adj_id")
    private Integer adjId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reading_id", nullable = false)
    private MeterReading reading;

    @Column(name = "adjusted_field", nullable = false, length = 30)
    private String adjustedField;

    @Column(name = "old_val", nullable = false)
    private Integer oldVal;

    @Column(name = "new_val", nullable = false)
    private Integer newVal;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adjusted_by", nullable = false)
    private User adjustedBy;

    @Column(name = "adjusted_at", nullable = false)
    private LocalDateTime adjustedAt;

    public MeterAdjustmentLog() {}

    public Integer getAdjId() { return adjId; }
    public void setAdjId(Integer adjId) { this.adjId = adjId; }

    public MeterReading getReading() { return reading; }
    public void setReading(MeterReading reading) { this.reading = reading; }

    public String getAdjustedField() { return adjustedField; }
    public void setAdjustedField(String adjustedField) { this.adjustedField = adjustedField; }

    public Integer getOldVal() { return oldVal; }
    public void setOldVal(Integer oldVal) { this.oldVal = oldVal; }

    public Integer getNewVal() { return newVal; }
    public void setNewVal(Integer newVal) { this.newVal = newVal; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public User getAdjustedBy() { return adjustedBy; }
    public void setAdjustedBy(User adjustedBy) { this.adjustedBy = adjustedBy; }

    public LocalDateTime getAdjustedAt() { return adjustedAt; }
    public void setAdjustedAt(LocalDateTime adjustedAt) { this.adjustedAt = adjustedAt; }

}
