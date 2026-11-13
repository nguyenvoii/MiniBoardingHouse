package com.group7.minihouse.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthUserTest {

    /** /api/auth/me va /api/auth/login tra AuthUser: chi lo ra dung 5 truong, khong co "tenant" thua. */
    @Test
    void jsonExposesOnlyTheFiveRecordFields() throws Exception {
        AuthUser u = new AuthUser(2, "a@b.c", "An", List.of("TENANT"), 1);
        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(u));

        assertEquals(5, json.size());
        assertFalse(json.has("tenant"));
        assertEquals(1, json.get("tenantId").asInt());
    }
}
