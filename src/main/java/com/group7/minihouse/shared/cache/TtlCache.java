package com.group7.minihouse.shared.cache;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.cache.support.AbstractValueAdaptingCache;

/**
 * Cache trong bo nho co han su dung va gioi han so muc, khong can thu vien ngoai.
 * Het han thi tu bo; dat toi da maxEntries thi bo muc het han truoc, roi muc cu nhat.
 * shortcut: khong khoa theo tung khoa, hai luong cung tai mot muc thi ca hai cung truy van DB mot lan, nang cap sang Caffeine neu can.
 */
public class TtlCache extends AbstractValueAdaptingCache {

    public record Stats(String name, long ttlSeconds, int size, long hits, long misses, long expired) {
        public double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0 : (double) hits / total;
        }
    }

    private record Entry(Object value, long expiresAtMillis) {}

    private final String name;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<Object, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong expired = new AtomicLong();

    public TtlCache(String name, Duration ttl, int maxEntries, Clock clock) {
        super(false); // khong luu null: ket qua rong van duoc cache, null thi khong
        this.name = name;
        this.ttl = ttl;
        this.maxEntries = Math.max(1, maxEntries);
        this.clock = clock;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return entries;
    }

    @Override
    protected Object lookup(Object key) {
        Entry e = entries.get(key);
        if (e == null) {
            misses.incrementAndGet();
            return null;
        }
        if (e.expiresAtMillis() <= clock.millis()) {
            if (entries.remove(key, e)) {
                expired.incrementAndGet();
            }
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return e.value();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object cached = lookup(key);
        if (cached != null) {
            return (T) fromStoreValue(cached);
        }
        T loaded;
        try {
            loaded = valueLoader.call();
        } catch (Exception ex) {
            throw new ValueRetrievalException(key, valueLoader, ex);
        }
        put(key, loaded);
        return loaded;
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null) {
            return;
        }
        if (entries.size() >= maxEntries && !entries.containsKey(key)) {
            makeRoom();
        }
        entries.put(key, new Entry(toStoreValue(value), clock.millis() + ttl.toMillis()));
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existing = get(key);
        if (existing != null) {
            return existing;
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        entries.remove(key);
    }

    @Override
    public void clear() {
        entries.clear();
    }

    public Stats stats() {
        return new Stats(name, ttl.toSeconds(), entries.size(), hits.get(), misses.get(), expired.get());
    }

    private void makeRoom() {
        long now = clock.millis();
        entries.entrySet().removeIf(e -> e.getValue().expiresAtMillis() <= now);
        if (entries.size() >= maxEntries) {
            entries.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().expiresAtMillis()))
                    .ifPresent(oldest -> entries.remove(oldest.getKey()));
        }
    }
}
