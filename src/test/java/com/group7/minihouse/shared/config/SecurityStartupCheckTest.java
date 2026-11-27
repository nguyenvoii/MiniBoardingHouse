package com.group7.minihouse.shared.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Bao mat bat thi secret JWT thieu, ngan hoac la chuoi mac dinh cong khai phai chan viec khoi dong. */
class SecurityStartupCheckTest {

    private static final String GOOD = "mot-secret-rieng-dai-hon-32-ky-tu-1234567890";

    @Test
    void goodSecretPasses() {
        assertDoesNotThrow(() -> SecurityStartupCheck.validate(true, GOOD));
    }

    @Test
    void missingShortOrLeakedSecretStopsTheApp() {
        assertThrows(IllegalStateException.class, () -> SecurityStartupCheck.validate(true, null));
        assertThrows(IllegalStateException.class, () -> SecurityStartupCheck.validate(true, "  "));
        assertThrows(IllegalStateException.class, () -> SecurityStartupCheck.validate(true, "ngan"));
        assertThrows(IllegalStateException.class,
                () -> SecurityStartupCheck.validate(true, SecurityStartupCheck.LEAKED_DEFAULT));
    }

    @Test
    void devModeWithSecurityOffIsNotChecked() {
        assertDoesNotThrow(() -> SecurityStartupCheck.validate(false, null));
    }
}
