package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank(message = "không được để trống") String identifier,
        @NotBlank(message = "không được để trống") String code,
        @NotBlank(message = "không được để trống") String newPassword) {
}
