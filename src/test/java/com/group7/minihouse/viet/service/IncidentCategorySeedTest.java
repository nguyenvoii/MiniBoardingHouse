package com.group7.minihouse.viet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * F7: nut "Bao su co" cua nguoi thue gui 7 ma nhom cung trong giao dien (me.ts); neu db/seed.sql, db/sample-data.sql
 * va db/patch_05 thieu mot ma thi tao yeu cau luon bi tu choi. Test nay giu cho ba noi va giao dien khop nhau.
 */
class IncidentCategorySeedTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static List<String> uiCodes() throws IOException {
        String ts = read("frontend/src/pages/me.ts");
        int start = ts.indexOf("const SUPPORT_CATEGORIES");
        String block = ts.substring(start, ts.indexOf("];", start));
        List<String> codes = new ArrayList<>();
        Matcher m = Pattern.compile("\\['([A-Z_]+)',").matcher(block);
        while (m.find()) {
            codes.add(m.group(1));
        }
        return codes;
    }

    @Test
    void uiHasSevenIncidentCodes() throws IOException {
        assertEquals(7, uiCodes().size(), uiCodes().toString());
    }

    @Test
    void everyUiCodeIsSeededAsIncidentInSeedSamplePatch() throws IOException {
        for (String file : List.of("db/seed.sql", "db/sample-data.sql", "db/patch_05_incident_categories.sql")) {
            String sql = read(file);
            for (String code : uiCodes()) {
                assertTrue(Pattern.compile("\\('" + code + "',[^\\n]*'INCIDENT'").matcher(sql).find(),
                        file + " thieu ma INCIDENT " + code);
            }
            assertTrue(sql.contains("'EQUIPMENT'"), file + " thieu nhom EQUIPMENT");
            assertTrue(sql.contains("WHERE NOT EXISTS (SELECT 1 FROM [incident_categories]"),
                    file + " phai chay lai an toan (IF NOT EXISTS)");
        }
    }
}
