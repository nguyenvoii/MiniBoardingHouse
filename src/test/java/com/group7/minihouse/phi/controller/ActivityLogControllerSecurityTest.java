package com.group7.minihouse.phi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group7.minihouse.phi.service.ActivityLogService;
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
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** UC13: chi LANDLORD xem nhat ky cua moi nguoi; ai da dang nhap cung xem duoc nhat ky cua CHINH MINH. */
@ImportAutoConfiguration(Jackson2AutoConfiguration.class)
@WebMvcTest(controllers = ActivityLogController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.enabled=true",
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234567890"})
class ActivityLogControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean ActivityLogService service;
    @MockitoBean UserStatusChecker statusChecker;
    @MockitoBean com.group7.minihouse.shared.export.TableExporter exporter;

    String dbRole = "MANAGER";

    @BeforeEach
    void databaseKnowsTheUser() {
        when(statusChecker.state(any(), any())).thenAnswer(inv -> Optional.of(new UserStatusChecker.State(List.of(dbRole), false, "test-hash")));
    }

    private String bearer(String role) {
        dbRole = role;
        Integer tenantId = "TENANT".equals(role) ? 1 : null;
        return "Bearer " + jwt.generate(new AuthUser(1, "a@b.c", "A", List.of(role), tenantId), "test-hash");
    }

    @Test
    void onlyLandlordSeesEveryonesLogs() throws Exception {
        mvc.perform(get("/api/activity-logs").header("Authorization", bearer("LANDLORD"))).andExpect(status().isOk());
        for (String role : List.of("MANAGER", "CASHIER", "METER_READER", "TENANT")) {
            mvc.perform(get("/api/activity-logs").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void onlyLandlordCanExportLogs() throws Exception {
        mvc.perform(get("/api/activity-logs/export").header("Authorization", bearer("LANDLORD")))
                .andExpect(status().isOk());
        for (String role : List.of("MANAGER", "CASHIER", "TENANT")) {
            mvc.perform(get("/api/activity-logs/export").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/activity-logs/export?format=csv").header("Authorization", bearer("LANDLORD")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void everyLoggedInUserSeesOwnLogs() throws Exception {
        for (String role : List.of("LANDLORD", "MANAGER", "CASHIER", "TENANT")) {
            mvc.perform(get("/api/auth/activity").header("Authorization", bearer(role))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/auth/activity")).andExpect(status().isUnauthorized());
    }
}
