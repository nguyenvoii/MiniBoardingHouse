package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

/** identifier la email hoac so dien thoai. */
public record LoginRequest(
        @NotBlank(message = "khong duoc de trong") String identifier,
        @NotBlank(message = "khong duoc de trong") String password) {
}
