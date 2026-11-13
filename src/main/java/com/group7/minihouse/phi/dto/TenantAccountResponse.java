package com.group7.minihouse.phi.dto;

/** Ket qua cap tai khoan nguoi thue. temporaryPassword chi hien 1 lan, nguoi thue nen doi sau khi dang nhap. */
public record TenantAccountResponse(Integer userId, String phoneNumber, String temporaryPassword) {
}
