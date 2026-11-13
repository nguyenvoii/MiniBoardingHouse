package com.group7.minihouse.phi.dto;

import com.group7.minihouse.shared.security.AuthUser;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, AuthUser user) {
}
