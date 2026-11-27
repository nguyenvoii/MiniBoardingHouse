package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyResetCodeRequest(
        @NotBlank(message = "khong duoc de trong") String identifier,
        @NotBlank(message = "khong duoc de trong") String code) {
}
