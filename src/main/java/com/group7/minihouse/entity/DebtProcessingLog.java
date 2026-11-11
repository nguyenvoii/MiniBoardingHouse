package com.group7.minihouse.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `debt_processing_logs`. */
@Entity
@Table(name = "debt_processing_logs")
public class DebtProcessingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proc_log_id")
    private Integer procLogId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id", nullable = false)
    private TenantDebt debt;

    @Column(name = "promised_payment_date")
    private LocalDate promisedPaymentDate;

    @Column(name = "delay_reason", length = 500)
    private String delayReason;

    @Column(name = "staff_note", length = 500)
    private String staffNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private User recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public DebtProcessingLog() {}

    public Integer getProcLogId() { return procLogId; }
    public void setProcLogId(Integer procLogId) { this.procLogId = procLogId; }

    public TenantDebt getDebt() { return debt; }
    public void setDebt(TenantDebt debt) { this.debt = debt; }

    public LocalDate getPromisedPaymentDate() { return promisedPaymentDate; }
    public void setPromisedPaymentDate(LocalDate promisedPaymentDate) { this.promisedPaymentDate = promisedPaymentDate; }

    public String getDelayReason() { return delayReason; }
    public void setDelayReason(String delayReason) { this.delayReason = delayReason; }

    public String getStaffNote() { return staffNote; }
    public void setStaffNote(String staffNote) { this.staffNote = staffNote; }

    public User getRecordedBy() { return recordedBy; }
    public void setRecordedBy(User recordedBy) { this.recordedBy = recordedBy; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }

}
