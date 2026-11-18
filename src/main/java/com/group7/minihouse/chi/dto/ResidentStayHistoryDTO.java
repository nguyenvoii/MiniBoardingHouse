package com.group7.minihouse.chi.dto;

import java.time.LocalDate;

public class ResidentStayHistoryDTO {
    private Integer historyId;
    private Integer tenantId;
    private String tenantName;
    private Integer roomId;
    private String roomCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private String checkoutReason;

    public ResidentStayHistoryDTO() {}

    public Integer getHistoryId() { return historyId; }
    public void setHistoryId(Integer historyId) { this.historyId = historyId; }

    public Integer getTenantId() { return tenantId; }
    public void setTenantId(Integer tenantId) { this.tenantId = tenantId; }

    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }

    public Integer getRoomId() { return roomId; }
    public void setRoomId(Integer roomId) { this.roomId = roomId; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getCheckoutReason() { return checkoutReason; }
    public void setCheckoutReason(String checkoutReason) { this.checkoutReason = checkoutReason; }
}
