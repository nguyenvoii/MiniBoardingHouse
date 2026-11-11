package com.group7.minihouse.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `monthly_billing_periods`. */
@Entity
@Table(name = "monthly_billing_periods")
public class MonthlyBillingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "billing_period_id")
    private Integer billingPeriodId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "month_year", nullable = false, length = 7)
    private String monthYear;

    @Column(name = "creation_date", nullable = false)
    private LocalDateTime creationDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    public MonthlyBillingPeriod() {}

    public Integer getBillingPeriodId() { return billingPeriodId; }
    public void setBillingPeriodId(Integer billingPeriodId) { this.billingPeriodId = billingPeriodId; }

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public String getMonthYear() { return monthYear; }
    public void setMonthYear(String monthYear) { this.monthYear = monthYear; }

    public LocalDateTime getCreationDate() { return creationDate; }
    public void setCreationDate(LocalDateTime creationDate) { this.creationDate = creationDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

}
