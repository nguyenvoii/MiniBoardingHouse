package com.group7.minihouse.phi.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPhoneRequest(@NotBlank String phone, @NotBlank String code) {}
