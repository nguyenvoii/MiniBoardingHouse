package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "khong duoc de trong") String email,
        @NotBlank(message = "khong duoc de trong") String code) {
}
