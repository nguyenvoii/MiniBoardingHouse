package com.group7.minihouse.shared.entity;

import java.time.LocalDate;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

/** Bang `utility_reading_periods`. */
@DynamicInsert
@Entity
@Table(name = "utility_reading_periods")
public class UtilityReadingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "period_id")
    private Integer periodId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "period_name", nullable = false, length = 50)
    private String periodName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_closed")
    private Boolean isClosed;

    public UtilityReadingPeriod() {}

    public Integer getPeriodId() { return periodId; }
    public void setPeriodId(Integer periodId) { this.periodId = periodId; }

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public String getPeriodName() { return periodName; }
    public void setPeriodName(String periodName) { this.periodName = periodName; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Boolean getIsClosed() { return isClosed; }
    public void setIsClosed(Boolean isClosed) { this.isClosed = isClosed; }

}
