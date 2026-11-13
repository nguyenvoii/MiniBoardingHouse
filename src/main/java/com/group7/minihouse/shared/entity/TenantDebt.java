package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `tenant_debts`. */
@Entity
@Table(name = "tenant_debts")
public class TenantDebt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "debt_id")
    private Integer debtId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(name = "opening_debt_balance", precision = 18, scale = 2)
    private BigDecimal openingDebtBalance;

    @Column(name = "current_period_debt", precision = 18, scale = 2)
    private BigDecimal currentPeriodDebt;

    @Column(name = "total_outstanding_debt", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalOutstandingDebt;

    @Column(name = "overdue_days")
    private Integer overdueDays;

    @Column(name = "debt_status", nullable = false, length = 30)
    private String debtStatus;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public TenantDebt() {}

    public Integer getDebtId() { return debtId; }
    public void setDebtId(Integer debtId) { this.debtId = debtId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }

    public BigDecimal getOpeningDebtBalance() { return openingDebtBalance; }
    public void setOpeningDebtBalance(BigDecimal openingDebtBalance) { this.openingDebtBalance = openingDebtBalance; }

    public BigDecimal getCurrentPeriodDebt() { return currentPeriodDebt; }
    public void setCurrentPeriodDebt(BigDecimal currentPeriodDebt) { this.currentPeriodDebt = currentPeriodDebt; }

    public BigDecimal getTotalOutstandingDebt() { return totalOutstandingDebt; }
    public void setTotalOutstandingDebt(BigDecimal totalOutstandingDebt) { this.totalOutstandingDebt = totalOutstandingDebt; }

    public Integer getOverdueDays() { return overdueDays; }
    public void setOverdueDays(Integer overdueDays) { this.overdueDays = overdueDays; }

    public String getDebtStatus() { return debtStatus; }
    public void setDebtStatus(String debtStatus) { this.debtStatus = debtStatus; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

}
