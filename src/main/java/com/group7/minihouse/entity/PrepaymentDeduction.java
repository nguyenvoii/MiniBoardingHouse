package com.group7.minihouse.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `prepayment_deductions`. */
@Entity
@Table(name = "prepayment_deductions")
public class PrepaymentDeduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deduction_id")
    private Integer deductionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prepayment_id", nullable = false)
    private TenantPrepayment prepayment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_invoice_id", nullable = false)
    private Invoice targetInvoice;

    @Column(name = "deducted_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal deductedAmount;

    @Column(name = "deducted_at", nullable = false)
    private LocalDateTime deductedAt;

    public PrepaymentDeduction() {}

    public Integer getDeductionId() { return deductionId; }
    public void setDeductionId(Integer deductionId) { this.deductionId = deductionId; }

    public TenantPrepayment getPrepayment() { return prepayment; }
    public void setPrepayment(TenantPrepayment prepayment) { this.prepayment = prepayment; }

    public Invoice getTargetInvoice() { return targetInvoice; }
    public void setTargetInvoice(Invoice targetInvoice) { this.targetInvoice = targetInvoice; }

    public BigDecimal getDeductedAmount() { return deductedAmount; }
    public void setDeductedAmount(BigDecimal deductedAmount) { this.deductedAmount = deductedAmount; }

    public LocalDateTime getDeductedAt() { return deductedAt; }
    public void setDeductedAt(LocalDateTime deductedAt) { this.deductedAt = deductedAt; }

}
