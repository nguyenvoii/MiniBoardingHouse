package com.group7.minihouse.phi.dto;

import java.util.List;

/** Can it nhat email hoac so dien thoai (dung de dang nhap). roles: MANAGER, CASHIER, METER_READER. */
public record CreateStaffRequest(String fullName, String email, String phoneNumber, List<String> roles) {
}
