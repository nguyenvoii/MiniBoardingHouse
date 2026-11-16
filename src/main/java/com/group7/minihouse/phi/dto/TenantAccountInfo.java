package com.group7.minihouse.phi.dto;

/** Tinh trang tai khoan dang nhap cua khach thue. exists = false khi chua cap (userId, status null). */
public record TenantAccountInfo(boolean exists, Integer userId, String phoneNumber, String status) {
}
