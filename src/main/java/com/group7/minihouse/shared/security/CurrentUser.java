package com.group7.minihouse.shared.security;

import com.group7.minihouse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Dung trong API cua NGUOI THUE (duoi /api/me/**): lay ma khach thue tu TOKEN, khong bao gio nhan tu client.
 * Vi du: Integer tenantId = CurrentUser.requireTenantId(user);  // user la @AuthenticationPrincipal AuthUser
 * Luu y: khi app.security.enabled=false (che do dev) khong co token nen user = null; hay test API nguoi thue
 * voi token nguoi thue (bat app.security.enabled=true), neu khong se nhan 401.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Integer requireTenantId(AuthUser user) {
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Chua dang nhap hoac token het han");
        }
        if (user.tenantId() == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chi danh cho nguoi thue");
        }
        return user.tenantId();
    }
}
