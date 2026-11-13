package com.group7.minihouse.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Test thuan (khong can DB): tao token roi doc lai, token sai/het han thi bi tu choi. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234567890";

    @Test
    void tokenRoundTrip() {
        JwtService jwt = new JwtService(SECRET, 60);
        AuthUser u = new AuthUser(7, "a@b.c", "An", List.of("MANAGER", "CASHIER"));
        AuthUser back = jwt.parse(jwt.generate(u));
        assertNotNull(back);
        assertEquals(7, back.userId());
        assertEquals("a@b.c", back.email());
        assertEquals(List.of("MANAGER", "CASHIER"), back.roles());
    }

    @Test
    void rejectsTamperedOrForeignToken() {
        JwtService jwt = new JwtService(SECRET, 60);
        JwtService other = new JwtService("another-secret-another-secret-1234567890!!", 60);
        String token = other.generate(new AuthUser(1, "x@y.z", "X", List.of()));
        assertNull(jwt.parse(token));
        assertNull(jwt.parse("khong.phai.token"));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwt = new JwtService(SECRET, -1);
        String token = jwt.generate(new AuthUser(1, "x@y.z", "X", List.of()));
        assertNull(jwt.parse(token));
    }
}
