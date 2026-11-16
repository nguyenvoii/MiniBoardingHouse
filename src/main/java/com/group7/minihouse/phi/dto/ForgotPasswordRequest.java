package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

/** identifier la email hoac so dien thoai cua tai khoan. */
public record ForgotPasswordRequest(
        @NotBlank(message = "khong duoc de trong") String identifier) {
}
