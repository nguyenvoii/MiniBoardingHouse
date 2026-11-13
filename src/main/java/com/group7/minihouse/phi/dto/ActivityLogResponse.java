package com.group7.minihouse.phi.dto;

import java.time.LocalDateTime;

public record ActivityLogResponse(Integer logId, Integer userId, String userFullName, String actionType,
                                  String description, String ipAddress, LocalDateTime createdAt) {
}
