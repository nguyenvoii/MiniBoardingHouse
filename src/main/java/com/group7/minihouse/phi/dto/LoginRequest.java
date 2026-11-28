package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

/** identifier la email hoac so dien thoai. */
public record LoginRequest(
        @NotBlank(message = "không được để trống") String identifier,
        @NotBlank(message = "không được để trống") String password) {
}
