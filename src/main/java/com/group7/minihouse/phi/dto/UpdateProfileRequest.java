package com.group7.minihouse.phi.dto;

/** phoneNumber: de trong = xoa SDT (chi nhan vien; nguoi thue khong duoc doi SDT). */
public record UpdateProfileRequest(String fullName, String phoneNumber) {
}
