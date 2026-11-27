package com.group7.minihouse.shared.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.group7.minihouse.shared.cache.TtlCacheManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NoOpCacheManager;

/** Trang trang thai: DB chet thi bao DOWN co ly do (khong nem loi), cache liet ke va xoa duoc. */
class SystemStatusServiceTest {

    @Test
    void databaseDownIsReportedNotThrown() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("The TCP/IP connection to the host localhost, port 1433 has failed."));
        SystemStatusService service = new SystemStatusService(ds, new NoOpCacheManager(), Clock.systemDefaultZone(), "uploads");

        SystemStatusService.Status st = service.status();

        assertEquals("DOWN", st.overall());
        assertFalse(st.database().up());
        assertTrue(st.database().error().contains("1433"));
        assertTrue(st.warnings().get(0).startsWith("Không kết nối được SQL Server"));
        assertFalse(st.cacheEnabled());
        assertNotNull(st.jvm());
        assertTrue(st.jvm().heapMaxMb() > 0);
    }

    @Test
    void cachesAreListedAndCanBeCleared() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("x"));
        TtlCacheManager mgr = new TtlCacheManager(Map.of("dashboard", Duration.ofSeconds(15)), Duration.ofSeconds(60), 10, Clock.systemDefaultZone());
        mgr.getCache("dashboard").put("k", "v");
        SystemStatusService service = new SystemStatusService(ds, mgr, Clock.systemDefaultZone(), "uploads");

        assertTrue(service.status().cacheEnabled());
        assertEquals(1, service.status().caches().get(0).size());
        assertEquals(1, service.clearCaches());
        assertEquals(0, service.status().caches().get(0).size());
    }
}
