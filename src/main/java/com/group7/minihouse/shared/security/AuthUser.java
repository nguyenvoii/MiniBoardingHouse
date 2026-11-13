package com.group7.minihouse.shared.security;

import java.util.List;

/** Nguoi dang nhap hien tai (principal). Lay trong controller bang @AuthenticationPrincipal AuthUser user. */
public record AuthUser(Integer userId, String email, String fullName, List<String> roles) {

    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }
}
