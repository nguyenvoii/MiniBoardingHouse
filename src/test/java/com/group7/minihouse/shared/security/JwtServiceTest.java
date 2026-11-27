package com.group7.minihouse.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Test thuan (khong can DB): tao token roi doc lai, token sai/het han thi bi tu choi. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234567890";

    @Test
    void tokenRoundTrip() {
        JwtService jwt = new JwtService(SECRET, 60);
        AuthUser u = new AuthUser(7, "a@b.c", "An", List.of("MANAGER", "CASHIER"), null);
        AuthUser back = jwt.parse(jwt.generate(u, "test-hash"));
        assertNotNull(back);
        assertEquals(7, back.userId());
        assertEquals("a@b.c", back.email());
        assertEquals(List.of("MANAGER", "CASHIER"), back.roles());
    }

    @Test
    void tenantIdRoundTrip() {
        JwtService jwt = new JwtService(SECRET, 60);
        AuthUser u = new AuthUser(7, "a@b.c", "An", List.of("TENANT"), 42);
        AuthUser back = jwt.parse(jwt.generate(u, "test-hash"));
        assertNotNull(back);
        assertEquals(42, back.tenantId());
        assertTrue(back.isTenant());
    }

    @Test
    void staffHasNullTenantId() {
        JwtService jwt = new JwtService(SECRET, 60);
        AuthUser u = new AuthUser(3, "m@b.c", "Manager", List.of("MANAGER"), null);
        AuthUser back = jwt.parse(jwt.generate(u, "test-hash"));
        assertNotNull(back);
        assertNull(back.tenantId());
        assertFalse(back.isTenant());
    }

    @Test
    void rejectsTamperedOrForeignToken() {
        JwtService jwt = new JwtService(SECRET, 60);
        JwtService other = new JwtService("another-secret-another-secret-1234567890!!", 60);
        String token = other.generate(new AuthUser(1, "x@y.z", "X", List.of(), null), "test-hash");
        assertNull(jwt.parse(token));
        assertNull(jwt.parse("khong.phai.token"));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwt = new JwtService(SECRET, -1);
        String token = jwt.generate(new AuthUser(1, "x@y.z", "X", List.of(), null), "test-hash");
        assertNull(jwt.parse(token));
    }

    /** Chi phien lay tu viec quet QR moi duoc dat mat khau moi khong can mat khau cu. */
    @Test
    void onlyQrSessionsAreMarkedAsQr() {
        JwtService jwt = new JwtService(SECRET, 60);
        AuthUser u = new AuthUser(7, "a@b.c", "An", List.of("TENANT"), 42);
        assertTrue(jwt.isQrSession(jwt.generate(u, "test-hash", true)));
        assertFalse(jwt.isQrSession(jwt.generate(u, "test-hash")));
        assertFalse(jwt.isQrSession(jwt.generateQrLogin(7, 42, "abc", 60))); // token trong ma QR khong tinh
        assertFalse(jwt.isQrSession("rac"));
        assertNotNull(jwt.parse(jwt.generate(u, "test-hash", true))); // van dung nhu phien binh thuong
    }

    /** Token trong ma QR chi dung de doi lay phien: dua thang lam Bearer thi phai bi tu choi, va nguoc lai. */
    @Test
    void qrTokenIsNotASessionToken() {
        JwtService jwt = new JwtService(SECRET, 60);
        String qr = jwt.generateQrLogin(7, 42, "abc", 60);
        assertNull(jwt.parse(qr));
        JwtService.QrClaims c = jwt.parseQrLogin(qr);
        assertEquals(7, c.userId());
        assertEquals(42, c.tenantId());
        assertEquals("abc", c.fingerprint());
        assertNull(jwt.parseQrLogin(jwt.generate(new AuthUser(7, "a@b.c", "An", List.of("TENANT"), 42), "test-hash")));
        assertNull(jwt.parseQrLogin(new JwtService(SECRET, 60).generateQrLogin(7, 42, "abc", -1)));
    }
}
