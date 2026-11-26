package com.group7.minihouse.shared.scheduler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** UC120: lich chay do nguoi dung chinh cho tung tac vu tu dong (ghi de cau hinh mac dinh). */
@Entity
@Table(name = "job_settings")
public class JobSetting {

    @Id
    @Column(name = "job_name", length = 60)
    private String jobName;

    @Column(name = "cron", nullable = false, length = 60)
    private String cron;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "updated_by")
    private Integer updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
