package com.group7.minihouse.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.*;

/** Bang `service_rate_plans`. */
@Entity
@Table(name = "service_rate_plans")
public class ServiceRatePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Integer planId;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "electric_unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal electricUnitPrice;

    @Column(name = "water_unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal waterUnitPrice;

    @Column(name = "effective_start_date", nullable = false)
    private LocalDate effectiveStartDate;

    @Column(name = "effective_end_date")
    private LocalDate effectiveEndDate;

    public ServiceRatePlan() {}

    public Integer getPlanId() { return planId; }
    public void setPlanId(Integer planId) { this.planId = planId; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public BigDecimal getElectricUnitPrice() { return electricUnitPrice; }
    public void setElectricUnitPrice(BigDecimal electricUnitPrice) { this.electricUnitPrice = electricUnitPrice; }

    public BigDecimal getWaterUnitPrice() { return waterUnitPrice; }
    public void setWaterUnitPrice(BigDecimal waterUnitPrice) { this.waterUnitPrice = waterUnitPrice; }

    public LocalDate getEffectiveStartDate() { return effectiveStartDate; }
    public void setEffectiveStartDate(LocalDate effectiveStartDate) { this.effectiveStartDate = effectiveStartDate; }

    public LocalDate getEffectiveEndDate() { return effectiveEndDate; }
    public void setEffectiveEndDate(LocalDate effectiveEndDate) { this.effectiveEndDate = effectiveEndDate; }

}
