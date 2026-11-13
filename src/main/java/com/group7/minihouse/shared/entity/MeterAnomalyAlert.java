package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;

/** Bang `meter_anomaly_alerts`. */
@Entity
@Table(name = "meter_anomaly_alerts")
public class MeterAnomalyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Integer alertId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reading_id", nullable = false)
    private MeterReading reading;

    @Column(name = "utility_type", nullable = false, length = 10)
    private String utilityType;

    @Column(name = "increase_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal increasePercentage;

    @Column(name = "alert_message", nullable = false, length = 255)
    private String alertMessage;

    @Column(name = "is_resolved")
    private Boolean isResolved;

    public MeterAnomalyAlert() {}

    public Integer getAlertId() { return alertId; }
    public void setAlertId(Integer alertId) { this.alertId = alertId; }

    public MeterReading getReading() { return reading; }
    public void setReading(MeterReading reading) { this.reading = reading; }

    public String getUtilityType() { return utilityType; }
    public void setUtilityType(String utilityType) { this.utilityType = utilityType; }

    public BigDecimal getIncreasePercentage() { return increasePercentage; }
    public void setIncreasePercentage(BigDecimal increasePercentage) { this.increasePercentage = increasePercentage; }

    public String getAlertMessage() { return alertMessage; }
    public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }

    public Boolean getIsResolved() { return isResolved; }
    public void setIsResolved(Boolean isResolved) { this.isResolved = isResolved; }

}
