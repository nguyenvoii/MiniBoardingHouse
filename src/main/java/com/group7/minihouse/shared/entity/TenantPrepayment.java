package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `tenant_prepayments`. */
@Entity
@Table(name = "tenant_prepayments")
public class TenantPrepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prepayment_id")
    private Integer prepaymentId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "excess_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal excessBalance;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public TenantPrepayment() {}

    public Integer getPrepaymentId() { return prepaymentId; }
    public void setPrepaymentId(Integer prepaymentId) { this.prepaymentId = prepaymentId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public BigDecimal getExcessBalance() { return excessBalance; }
    public void setExcessBalance(BigDecimal excessBalance) { this.excessBalance = excessBalance; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

}
