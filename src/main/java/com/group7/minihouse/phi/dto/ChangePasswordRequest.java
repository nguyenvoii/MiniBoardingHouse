package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "không được để trống") String currentPassword,
        @NotBlank(message = "không được để trống") String newPassword) {
}
