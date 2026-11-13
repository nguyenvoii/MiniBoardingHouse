package com.group7.minihouse.phi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.group7.minihouse.phi.service.StaffService;
import com.group7.minihouse.shared.config.MethodSecurityConfig;
import com.group7.minihouse.shared.config.SecurityConfig;
import com.group7.minihouse.shared.exception.GlobalExceptionHandler;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Phan quyen that cua /api/staff khi bat bao mat: chi LANDLORD ghi, LANDLORD + MANAGER doc, con lai 403. */
@WebMvcTest(controllers = StaffController.class)
@Import({SecurityConfig.class, MethodSecurityConfig.class, JwtService.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.enabled=true",
        "app.jwt.secret=unit-test-secret-unit-test-secret-1234567890"})
class StaffControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockBean StaffService service;

    private String bearer(String role) {
        Integer tenantId = "TENANT".equals(role) ? 1 : null;
        return "Bearer " + jwt.generate(new AuthUser(1, "a@b.c", "A", List.of(role), tenantId));
    }

    private static final String BODY = "{\"fullName\":\"A\",\"email\":\"a@x.vn\",\"roles\":[\"MANAGER\"]}";

    @Test
    void landlordCanWrite() throws Exception {
        mvc.perform(post("/api/staff").header("Authorization", bearer("LANDLORD"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        mvc.perform(put("/api/staff/7").header("Authorization", bearer("LANDLORD"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        mvc.perform(patch("/api/staff/7/status").header("Authorization", bearer("LANDLORD"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}")).andExpect(status().isOk());
    }

    @Test
    void managerCanReadButNotWrite() throws Exception {
        mvc.perform(get("/api/staff").header("Authorization", bearer("MANAGER"))).andExpect(status().isOk());
        mvc.perform(get("/api/staff/7").header("Authorization", bearer("MANAGER"))).andExpect(status().isOk());
        mvc.perform(post("/api/staff").header("Authorization", bearer("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(put("/api/staff/7").header("Authorization", bearer("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/staff/7/status").header("Authorization", bearer("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cashierMeterReaderAndTenantCannotEvenRead() throws Exception {
        for (String role : List.of("CASHIER", "METER_READER", "TENANT")) {
            mvc.perform(get("/api/staff").header("Authorization", bearer(role))).andExpect(status().isForbidden());
        }
    }

    @Test
    void noTokenGets401() throws Exception {
        mvc.perform(get("/api/staff")).andExpect(status().isUnauthorized());
    }
}
