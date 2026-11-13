package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `deposit_refund_vouchers`. */
@Entity
@Table(name = "deposit_refund_vouchers")
public class DepositRefundVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Integer refundId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checkout_id", nullable = false)
    private CheckoutRequest checkout;

    @Column(name = "original_deposit_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal originalDepositAmount;

    @Column(name = "deducted_debt_amount", precision = 18, scale = 2)
    private BigDecimal deductedDebtAmount;

    @Column(name = "deducted_damage_amount", precision = 18, scale = 2)
    private BigDecimal deductedDamageAmount;

    @Column(name = "actual_refund_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal actualRefundAmount;

    @Column(name = "refund_date")
    private LocalDateTime refundDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    public DepositRefundVoucher() {}

    public Integer getRefundId() { return refundId; }
    public void setRefundId(Integer refundId) { this.refundId = refundId; }

    public CheckoutRequest getCheckout() { return checkout; }
    public void setCheckout(CheckoutRequest checkout) { this.checkout = checkout; }

    public BigDecimal getOriginalDepositAmount() { return originalDepositAmount; }
    public void setOriginalDepositAmount(BigDecimal originalDepositAmount) { this.originalDepositAmount = originalDepositAmount; }

    public BigDecimal getDeductedDebtAmount() { return deductedDebtAmount; }
    public void setDeductedDebtAmount(BigDecimal deductedDebtAmount) { this.deductedDebtAmount = deductedDebtAmount; }

    public BigDecimal getDeductedDamageAmount() { return deductedDamageAmount; }
    public void setDeductedDamageAmount(BigDecimal deductedDamageAmount) { this.deductedDamageAmount = deductedDamageAmount; }

    public BigDecimal getActualRefundAmount() { return actualRefundAmount; }
    public void setActualRefundAmount(BigDecimal actualRefundAmount) { this.actualRefundAmount = actualRefundAmount; }

    public LocalDateTime getRefundDate() { return refundDate; }
    public void setRefundDate(LocalDateTime refundDate) { this.refundDate = refundDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

}
