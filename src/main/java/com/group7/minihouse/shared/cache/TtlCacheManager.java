package com.group7.minihouse.shared.cache;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** Tao cache theo ten khi can; moi ten co thoi han rieng (khong khai bao thi dung defaultTtl). */
public class TtlCacheManager implements CacheManager {

    private final Map<String, TtlCache> caches = new ConcurrentHashMap<>();
    private final Map<String, Duration> ttlByName;
    private final Duration defaultTtl;
    private final int maxEntries;
    private final Clock clock;

    public TtlCacheManager(Map<String, Duration> ttlByName, Duration defaultTtl, int maxEntries, Clock clock) {
        this.ttlByName = Map.copyOf(ttlByName);
        this.defaultTtl = defaultTtl;
        this.maxEntries = maxEntries;
        this.clock = clock;
        // tao san de trang thai server luon liet ke du chua ai goi
        ttlByName.keySet().forEach(this::getCache);
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, n -> new TtlCache(n, ttlByName.getOrDefault(n, defaultTtl), maxEntries, clock));
    }

    @Override
    public Collection<String> getCacheNames() {
        return List.copyOf(caches.keySet());
    }

    public List<TtlCache.Stats> stats() {
        return caches.values().stream().map(TtlCache::stats).sorted((a, b) -> a.name().compareTo(b.name())).toList();
    }

    public void clearAll() {
        caches.values().forEach(TtlCache::clear);
    }
}
