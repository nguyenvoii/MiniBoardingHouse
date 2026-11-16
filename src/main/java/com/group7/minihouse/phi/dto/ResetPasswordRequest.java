package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "khong duoc de trong") String identifier,
        @NotBlank(message = "khong duoc de trong") String code,
        @NotBlank(message = "khong duoc de trong") String newPassword) {
}
