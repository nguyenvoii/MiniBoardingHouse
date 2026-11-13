package com.group7.minihouse.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group7.minihouse.shared.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CurrentUserTest {

    @Test
    void returnsTenantIdOfTenantUser() {
        AuthUser tenant = new AuthUser(2, null, "An", List.of("TENANT"), 42);
        assertEquals(42, CurrentUser.requireTenantId(tenant));
    }

    @Test
    void requireUserIdReturnsIdOrFails401() {
        assertEquals(3, CurrentUser.requireUserId(new AuthUser(3, null, "M", List.of("MANAGER"), null)));
        BusinessException ex = assertThrows(BusinessException.class, () -> CurrentUser.requireUserId(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void notLoggedInGets401InsteadOfNullPointer() {
        BusinessException ex = assertThrows(BusinessException.class, () -> CurrentUser.requireTenantId(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void staffTokenGets403() {
        AuthUser staff = new AuthUser(3, "m@x.vn", "Manager", List.of("MANAGER"), null);
        BusinessException ex = assertThrows(BusinessException.class, () -> CurrentUser.requireTenantId(staff));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("nguoi thue"));
    }
}
