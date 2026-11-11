package com.group7.minihouse.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `receipt_vouchers`. */
@Entity
@Table(name = "receipt_vouchers")
public class ReceiptVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voucher_id")
    private Integer voucherId;

    @Column(name = "voucher_code", nullable = false, unique = true, length = 50)
    private String voucherCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "transaction_id")
    private PaymentTransaction transaction;

    @Column(name = "voucher_type", nullable = false, length = 30)
    private String voucherType;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ReceiptVoucher() {}

    public Integer getVoucherId() { return voucherId; }
    public void setVoucherId(Integer voucherId) { this.voucherId = voucherId; }

    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }

    public PaymentTransaction getTransaction() { return transaction; }
    public void setTransaction(PaymentTransaction transaction) { this.transaction = transaction; }

    public String getVoucherType() { return voucherType; }
    public void setVoucherType(String voucherType) { this.voucherType = voucherType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
