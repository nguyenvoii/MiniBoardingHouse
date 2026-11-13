package com.group7.minihouse.phi.dto;

/** temporaryPassword chi hien 1 lan; nhan vien nen doi bang POST /api/auth/password sau khi dang nhap. */
public record StaffCreatedResponse(StaffResponse staff, String temporaryPassword) {
}
