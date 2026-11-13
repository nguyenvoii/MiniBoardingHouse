package com.group7.minihouse.phi.dto;

import java.time.LocalDateTime;
import java.util.List;

/** createdAt co the null trong response cua thao tac them (DB tu dien); danh sach/chi tiet luon co. */
public record StaffResponse(Integer userId, String fullName, String email, String phoneNumber, String status,
                            List<String> roles, LocalDateTime createdAt) {
}
