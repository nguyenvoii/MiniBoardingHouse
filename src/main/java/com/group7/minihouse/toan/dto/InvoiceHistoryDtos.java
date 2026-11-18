package com.group7.minihouse.toan.dto;

import java.time.LocalDateTime;

/** DTO cho UC128: lịch sử thao tác hóa đơn và giao dịch. */
public final class InvoiceHistoryDtos {
    private InvoiceHistoryDtos() {}
    public record HistoryItem(Integer logId, LocalDateTime createdAt, Integer userId, String userName,
                              String actionType, String description, String ipAddress) {}
}
