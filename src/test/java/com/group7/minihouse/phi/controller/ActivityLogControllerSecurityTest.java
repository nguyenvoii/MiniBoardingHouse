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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** UC13: chi LANDLORD xem nhat ky cua moi nguoi; ai da dang nhap cung xem duoc nhat ky cua CHINH MINH. */
@WebMvcTest(controllers = ActivityLogController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.enabled=true",
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234567890"})
class ActivityLogControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean ActivityLogService service;
    @MockBean UserStatusChecker statusChecker;

    @BeforeEach
    void everyoneIsActive() {
        when(statusChecker.isActive(any())).thenReturn(true);
    }

    private String bearer(String role) {
        Integer tenantId = "TENANT".equals(role) ? 1 : null;
        return "Bearer " + jwt.generate(new AuthUser(1, "a@b.c", "A", List.of(role), tenantId));
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
    void everyLoggedInUserSeesOwnLogs() throws Exception {
        for (String role : List.of("LANDLORD", "MANAGER", "CASHIER", "TENANT")) {
            mvc.perform(get("/api/auth/activity").header("Authorization", bearer(role))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/auth/activity")).andExpect(status().isUnauthorized());
    }
}
