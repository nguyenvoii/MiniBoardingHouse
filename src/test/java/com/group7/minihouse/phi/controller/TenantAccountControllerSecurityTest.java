package com.group7.minihouse.phi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group7.minihouse.phi.service.TenantAccountService;
import com.group7.minihouse.shared.config.MethodSecurityConfig;
import com.group7.minihouse.shared.config.SecurityConfig;
import com.group7.minihouse.shared.exception.GlobalExceptionHandler;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.JwtService;
import com.group7.minihouse.shared.security.UserStatusChecker;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Cap tai khoan nguoi thue (UC10) va khoa/mo khoa tai khoan do: chi LANDLORD va MANAGER. */
@WebMvcTest(controllers = TenantAccountController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.enabled=true",
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234567890"})
class TenantAccountControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean TenantAccountService service;
    @MockBean UserStatusChecker statusChecker;

    String dbRole = "MANAGER";

    @BeforeEach
    void databaseKnowsTheUser() {
        when(statusChecker.activeRoles(any())).thenAnswer(inv -> Optional.of(List.of(dbRole)));
    }

    private String bearer(String role) {
        dbRole = role;
        Integer tenantId = "TENANT".equals(role) ? 1 : null;
        return "Bearer " + jwt.generate(new AuthUser(1, "a@b.c", "A", List.of(role), tenantId));
    }

    private static final String LOCK = "{\"status\":\"LOCKED\"}";

    @Test
    void landlordAndManagerCanIssueAndLock() throws Exception {
        for (String role : List.of("LANDLORD", "MANAGER")) {
            mvc.perform(post("/api/tenants/42/account").header("Authorization", bearer(role)))
                    .andExpect(status().isOk());
            mvc.perform(patch("/api/tenants/42/account/status").header("Authorization", bearer(role))
                    .contentType(MediaType.APPLICATION_JSON).content(LOCK)).andExpect(status().isOk());
        }
    }

    @Test
    void cashierMeterReaderAndTenantCannotIssueOrLock() throws Exception {
        for (String role : List.of("CASHIER", "METER_READER", "TENANT")) {
            mvc.perform(post("/api/tenants/42/account").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/tenants/42/account/status").header("Authorization", bearer(role))
                    .contentType(MediaType.APPLICATION_JSON).content(LOCK)).andExpect(status().isForbidden());
        }
    }

    @Test
    void noTokenGets401() throws Exception {
        mvc.perform(patch("/api/tenants/42/account/status").contentType(MediaType.APPLICATION_JSON).content(LOCK))
                .andExpect(status().isUnauthorized());
    }
}
