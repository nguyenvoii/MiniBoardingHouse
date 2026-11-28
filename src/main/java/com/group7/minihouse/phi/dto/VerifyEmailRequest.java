package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "không được để trống") String email,
        @NotBlank(message = "không được để trống") String code) {
}
