package com.group7.minihouse.phi.dto;

import java.util.List;

/** roles = null: giu nguyen vai tro hien tai; co gia tri: thay toan bo vai tro nhan vien. */
public record UpdateStaffRequest(String fullName, String email, String phoneNumber, List<String> roles) {
}
