package com.group7.minihouse.phi.dto;

/** Dang ky chu tro: email bat buoc (de nhan ma xac thuc), so dien thoai tuy chon. */
public record RegisterRequest(String fullName, String email, String phoneNumber, String password) {
}
