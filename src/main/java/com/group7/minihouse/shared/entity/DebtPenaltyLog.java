package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

/** Bang `debt_penalty_logs`. */
@DynamicInsert
@Entity
@Table(name = "debt_penalty_logs")
public class DebtPenaltyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "penalty_id")
    private Integer penaltyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id", nullable = false)
    private TenantDebt debt;

    @Column(name = "overdue_days", nullable = false)
    private Integer overdueDays;

    @Column(name = "daily_penalty_rate", nullable = false, precision = 18, scale = 2)
    private BigDecimal dailyPenaltyRate;

    @Column(name = "calculated_penalty_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal calculatedPenaltyAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public DebtPenaltyLog() {}

    public Integer getPenaltyId() { return penaltyId; }
    public void setPenaltyId(Integer penaltyId) { this.penaltyId = penaltyId; }

    public TenantDebt getDebt() { return debt; }
    public void setDebt(TenantDebt debt) { this.debt = debt; }

    public Integer getOverdueDays() { return overdueDays; }
    public void setOverdueDays(Integer overdueDays) { this.overdueDays = overdueDays; }

    public BigDecimal getDailyPenaltyRate() { return dailyPenaltyRate; }
    public void setDailyPenaltyRate(BigDecimal dailyPenaltyRate) { this.dailyPenaltyRate = dailyPenaltyRate; }

    public BigDecimal getCalculatedPenaltyAmount() { return calculatedPenaltyAmount; }
    public void setCalculatedPenaltyAmount(BigDecimal calculatedPenaltyAmount) { this.calculatedPenaltyAmount = calculatedPenaltyAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
