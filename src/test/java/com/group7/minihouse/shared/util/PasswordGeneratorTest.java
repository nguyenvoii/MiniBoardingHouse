package com.group7.minihouse.shared.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordGeneratorTest {

    @Test
    void generatesTenCharsWithoutConfusingCharacters() {
        for (int i = 0; i < 200; i++) {
            String p = PasswordGenerator.generate();
            assertEquals(10, p.length());
            assertTrue(p.matches("[A-Za-z0-9]{10}"));
            assertFalse(p.matches(".*[0O1lI].*"), p);
        }
    }

    @Test
    void generatesDifferentPasswords() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            seen.add(PasswordGenerator.generate());
        }
        assertNotEquals(1, seen.size());
        assertTrue(seen.size() > 45);
    }
}
