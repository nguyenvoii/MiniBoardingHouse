package com.group7.minihouse.phi.dto;

import com.group7.minihouse.shared.security.AuthUser;

/** mustChangePassword: đang dùng mật khẩu tạm, giao diện phải bắt đổi mật khẩu trước khi làm việc. */
public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, AuthUser user,
                            boolean mustChangePassword) {
}
