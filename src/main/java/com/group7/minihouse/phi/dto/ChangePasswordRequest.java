package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "khong duoc de trong") String currentPassword,
        @NotBlank(message = "khong duoc de trong") String newPassword) {
}
