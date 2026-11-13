package com.group7.minihouse.shared.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

/** Bang `maintenance_logs`. */
@Entity
@Table(name = "maintenance_logs")
public class MaintenanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maintenance_log_id")
    private Integer maintenanceLogId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private MaintenanceRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "assigned_staff_id")
    private User assignedStaff;

    @Column(name = "repair_cost", precision = 18, scale = 2)
    private BigDecimal repairCost;

    @Column(name = "tenant_rating")
    private Integer tenantRating;

    @Column(name = "resolution_notes", columnDefinition = "NVARCHAR(MAX)")
    private String resolutionNotes;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public MaintenanceLog() {}

    public Integer getMaintenanceLogId() { return maintenanceLogId; }
    public void setMaintenanceLogId(Integer maintenanceLogId) { this.maintenanceLogId = maintenanceLogId; }

    public MaintenanceRequest getRequest() { return request; }
    public void setRequest(MaintenanceRequest request) { this.request = request; }

    public User getAssignedStaff() { return assignedStaff; }
    public void setAssignedStaff(User assignedStaff) { this.assignedStaff = assignedStaff; }

    public BigDecimal getRepairCost() { return repairCost; }
    public void setRepairCost(BigDecimal repairCost) { this.repairCost = repairCost; }

    public Integer getTenantRating() { return tenantRating; }
    public void setTenantRating(Integer tenantRating) { this.tenantRating = tenantRating; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

}
