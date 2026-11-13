package com.group7.minihouse.shared.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.JwtService;
import com.group7.minihouse.shared.security.UserStatusChecker;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Luat URL khi app.security.enabled=true (khong can DB): mac dinh chi nhan vien duoc vao,
 * nguoi thue chi vao /api/me/** va /api/auth/** => dong doi quen @PreAuthorize cung khong lo du lieu cho nguoi thue.
 */
@WebMvcTest(controllers = SecurityRulesTest.Probe.class)
@Import({SecurityConfig.class, JwtService.class, SecurityRulesTest.Probe.class})
@TestPropertySource(properties = {
        "app.security.enabled=true",
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234567890"})
class SecurityRulesTest {

    @RestController
    static class Probe {
        @GetMapping("/api/probe/staff") String staff() { return "ok"; }
        @GetMapping("/api/me/probe") String tenantArea() { return "ok"; }
        @GetMapping("/api/auth/probe") String auth() { return "ok"; }
    }

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean UserStatusChecker statusChecker;

    @BeforeEach
    void everyoneIsActiveByDefault() {
        when(statusChecker.isActive(any())).thenReturn(true);
    }

    /** UC9: khoa tai khoan phai chan NGAY token da cap (kiem tra trang thai trong DB moi request). */
    @Test
    void lockedUsersTokenIsRejectedImmediately() throws Exception {
        when(statusChecker.isActive(1)).thenReturn(false);

        mvc.perform(get("/api/probe/staff").header("Authorization", bearer("MANAGER")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/probe").header("Authorization", bearer("TENANT")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/probe").header("Authorization", bearer("MANAGER")))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(String... roles) {
        Integer tenantId = Arrays.asList(roles).contains("TENANT") ? 1 : null;
        return "Bearer " + jwt.generate(new AuthUser(1, "a@b.c", "A", List.of(roles), tenantId));
    }

    @Test
    void tenantTokenCannotReachUnannotatedStaffEndpoint() throws Exception {
        mvc.perform(get("/api/probe/staff").header("Authorization", bearer("TENANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffTokenReachesStaffEndpoint() throws Exception {
        mvc.perform(get("/api/probe/staff").header("Authorization", bearer("MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void tenantTokenReachesTenantArea() throws Exception {
        mvc.perform(get("/api/me/probe").header("Authorization", bearer("TENANT")))
                .andExpect(status().isOk());
    }

    @Test
    void staffTokenCannotReachTenantArea() throws Exception {
        mvc.perform(get("/api/me/probe").header("Authorization", bearer("MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anyLoggedInUserReachesAuthArea() throws Exception {
        mvc.perform(get("/api/auth/probe").header("Authorization", bearer("TENANT")))
                .andExpect(status().isOk());
    }

    @Test
    void noTokenGets401() throws Exception {
        mvc.perform(get("/api/probe/staff")).andExpect(status().isUnauthorized());
    }
}
