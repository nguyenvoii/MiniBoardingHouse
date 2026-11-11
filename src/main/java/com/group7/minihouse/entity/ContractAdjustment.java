package com.group7.minihouse.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `contract_adjustments`. */
@Entity
@Table(name = "contract_adjustments")
public class ContractAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "adjustment_id")
    private Integer adjustmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private RentalContract contract;

    @Column(name = "adjustment_type", nullable = false, length = 50)
    private String adjustmentType;

    @Column(name = "old_value", length = 255)
    private String oldValue;

    @Column(name = "new_value", length = 255)
    private String newValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "adjusted_by")
    private User adjustedBy;

    @Column(name = "adjusted_at", nullable = false)
    private LocalDateTime adjustedAt;

    public ContractAdjustment() {}

    public Integer getAdjustmentId() { return adjustmentId; }
    public void setAdjustmentId(Integer adjustmentId) { this.adjustmentId = adjustmentId; }

    public RentalContract getContract() { return contract; }
    public void setContract(RentalContract contract) { this.contract = contract; }

    public String getAdjustmentType() { return adjustmentType; }
    public void setAdjustmentType(String adjustmentType) { this.adjustmentType = adjustmentType; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public User getAdjustedBy() { return adjustedBy; }
    public void setAdjustedBy(User adjustedBy) { this.adjustedBy = adjustedBy; }

    public LocalDateTime getAdjustedAt() { return adjustedAt; }
    public void setAdjustedAt(LocalDateTime adjustedAt) { this.adjustedAt = adjustedAt; }

}
