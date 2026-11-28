package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyResetCodeRequest(
        @NotBlank(message = "không được để trống") String identifier,
        @NotBlank(message = "không được để trống") String code) {
}
