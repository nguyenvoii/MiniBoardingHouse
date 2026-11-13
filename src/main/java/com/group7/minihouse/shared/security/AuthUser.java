package com.group7.minihouse.shared.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/**
 * Nguoi dang nhap hien tai (principal). Lay trong controller bang @AuthenticationPrincipal AuthUser user.
 * tenantId chi co gia tri voi nguoi thue (role TENANT), nhan vien/chu tro thi null.
 * API cua nguoi thue PHAI dung user.tenantId() de loc du lieu, khong nhan tenantId tu client.
 */
public record AuthUser(Integer userId, String email, String fullName, List<String> roles, Integer tenantId) {

    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }

    @JsonIgnore
    public boolean isTenant() {
        return hasRole("TENANT");
    }
}
