package com.group7.minihouse.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `partial_payment_logs`. */
@Entity
@Table(name = "partial_payment_logs")
public class PartialPaymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "partial_log_id")
    private Integer partialLogId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_date", nullable = false)
    private LocalDateTime paidDate;

    public PartialPaymentLog() {}

    public Integer getPartialLogId() { return partialLogId; }
    public void setPartialLogId(Integer partialLogId) { this.partialLogId = partialLogId; }

    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }

    public Integer getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(Integer installmentNumber) { this.installmentNumber = installmentNumber; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDateTime getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDateTime paidDate) { this.paidDate = paidDate; }

}
