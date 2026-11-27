package com.group7.minihouse.shared.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Cache co han su dung: het han thi tai lai, day thi bo muc cu, khong luu null, xoa het duoc. */
class TtlCacheTest {

    /** Dong ho chinh tay de test khong phai cho that. */
    static class MutableClock extends Clock {
        long now = 1_000_000;

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
    }

    @Test
    void loaderRunsOnceWithinTtlAndAgainAfterExpiry() {
        MutableClock clock = new MutableClock();
        TtlCache cache = new TtlCache("t", Duration.ofSeconds(10), 10, clock);
        AtomicInteger loads = new AtomicInteger();

        assertEquals("v1", cache.get("k", () -> "v" + loads.incrementAndGet()));
        assertEquals("v1", cache.get("k", () -> "v" + loads.incrementAndGet()));
        assertEquals(1, loads.get());

        clock.now += 10_001; // qua han
        assertEquals("v2", cache.get("k", () -> "v" + loads.incrementAndGet()));
        assertEquals(2, loads.get());
        TtlCache.Stats s = cache.stats();
        assertEquals(1, s.hits());
        assertEquals(2, s.misses());
        assertEquals(1, s.expired());
    }

    @Test
    void doesNotStoreNullAndClearEmptiesEverything() {
        TtlCache cache = new TtlCache("t", Duration.ofSeconds(10), 10, new MutableClock());
        cache.put("a", null);
        assertNull(cache.get("a"));
        cache.put("a", "x");
        cache.put("b", "y");
        assertNotNull(cache.get("a"));
        cache.evict("a");
        assertNull(cache.get("a"));
        cache.clear();
        assertNull(cache.get("b"));
        assertEquals(0, cache.stats().size());
    }

    @Test
    void whenFullDropsExpiredFirstThenOldest() {
        MutableClock clock = new MutableClock();
        TtlCache cache = new TtlCache("t", Duration.ofSeconds(10), 2, clock);
        cache.put("old", 1);
        clock.now += 1_000;
        cache.put("new", 2);
        clock.now += 1_000;
        cache.put("third", 3); // day: bo "old" (het han som nhat)
        assertEquals(2, cache.stats().size());
        assertNull(cache.get("old"));
        assertNotNull(cache.get("new"));
        assertNotNull(cache.get("third"));

        clock.now += 9_500; // "new" het han, "third" con song
        cache.put("fourth", 4);
        assertNull(cache.get("new"));
        assertNotNull(cache.get("third"));
    }

    @Test
    void managerUsesPerNameTtlAndClearsAll() {
        MutableClock clock = new MutableClock();
        TtlCacheManager mgr = new TtlCacheManager(Map.of("short", Duration.ofSeconds(1)), Duration.ofSeconds(60), 10, clock);
        assertEquals(1, mgr.stats().size()); // cache khai bao san hien ngay trong thong ke
        mgr.getCache("short").put("k", "v");
        mgr.getCache("other").put("k", "v");
        clock.now += 2_000;
        assertNull(mgr.getCache("short").get("k"));       // ttl 1 giay da het
        assertNotNull(mgr.getCache("other").get("k"));    // ttl mac dinh 60 giay con song
        mgr.clearAll();
        assertNull(mgr.getCache("other").get("k"));
    }
}
