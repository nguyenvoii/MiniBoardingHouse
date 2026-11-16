package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** UC12: bang quyen mac dinh cua tung vai tro. */
class RolePermissionsTest {

    /** Cac ma quyen that su co trong db/seed.sql. */
    private Set<String> seedCodes() throws Exception {
        String sql = Files.readString(Path.of("db", "seed.sql"));
        String block = sql.substring(sql.indexOf("INSERT INTO [permissions]"));
        block = block.substring(0, block.indexOf("AS v(code"));
        Set<String> codes = new HashSet<>();
        Matcher m = Pattern.compile("\\('([A-Z_]+)',").matcher(block);
        while (m.find()) codes.add(m.group(1));
        return codes;
    }

    @Test
    void everyCodeInTheMatrixExistsInSeed() throws Exception {
        Set<String> seed = seedCodes();
        assertTrue(seed.size() >= 20, "doc seed that bai: " + seed);
        for (String role : RolePermissions.ROLES) {
            Set<String> codes = RolePermissions.defaults(role, seed);
            assertTrue(seed.containsAll(codes), role + " co ma quyen khong ton tai: " + codes);
        }
    }

    @Test
    void landlordHasEverything() throws Exception {
        Set<String> seed = seedCodes();
        assertEquals(seed, RolePermissions.defaults("LANDLORD", seed));
    }

    @Test
    void managerHasEverythingExceptManagingUsers() throws Exception {
        Set<String> seed = seedCodes();
        Set<String> m = RolePermissions.defaults("MANAGER", seed);
        assertFalse(m.contains("USER_MANAGE"));
        assertTrue(m.contains("USER_VIEW"));
        assertTrue(m.contains("CONTRACT_MANAGE"));
        assertEquals(seed.size() - 1, m.size());
    }

    @Test
    void cashierCollectsMoneyButCannotWriteMeters() throws Exception {
        Set<String> c = RolePermissions.defaults("CASHIER", seedCodes());
        assertTrue(c.contains("PAYMENT_MANAGE"));
        assertTrue(c.contains("INVOICE_VIEW"));
        assertFalse(c.contains("METER_WRITE"));
        assertFalse(c.contains("USER_MANAGE"));
        assertFalse(c.contains("CONTRACT_MANAGE"));
    }

    @Test
    void meterReaderWritesMetersButCannotCollectMoney() throws Exception {
        Set<String> r = RolePermissions.defaults("METER_READER", seedCodes());
        assertTrue(r.contains("METER_WRITE"));
        assertTrue(r.contains("ROOM_VIEW"));
        assertFalse(r.contains("PAYMENT_MANAGE"));
        assertFalse(r.contains("INVOICE_VIEW"));
    }

    @Test
    void tenantAndUnknownRolesHaveNothing() throws Exception {
        Set<String> seed = seedCodes();
        assertTrue(RolePermissions.defaults("TENANT", seed).isEmpty());
        assertTrue(RolePermissions.defaults("KHONG_CO", seed).isEmpty());
    }

    @Test
    void effectiveIsTheUnionOfRoles() throws Exception {
        Set<String> seed = seedCodes();
        Set<String> both = RolePermissions.effective(List.of("CASHIER", "METER_READER"), seed);
        assertTrue(both.contains("PAYMENT_MANAGE"));
        assertTrue(both.contains("METER_WRITE"));
        assertFalse(both.contains("USER_MANAGE"));
    }
}
