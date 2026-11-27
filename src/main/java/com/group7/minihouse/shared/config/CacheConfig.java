package com.group7.minihouse.shared.config;

import com.group7.minihouse.shared.cache.TtlCacheManager;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bo nho dem de giam so truy van len SQL Server. Chi cache so lieu tong hop nang (tong quan, bao cao: song ngan,
 * chap nhan tre vai giay) va du lieu gan nhu tinh (khu tro, bang gia: song lau hon, tu xoa khi co sua).
 * KHONG cache du lieu giao dich (hoa don, thanh toan, hop dong) de so lieu khong bi sai.
 * Tat bang app.cache.enabled=false khi can kiem tra loi do du lieu cu.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    CacheManager cacheManager(@Value("${app.cache.enabled:true}") boolean enabled,
                              @Value("${app.cache.dashboard-seconds:15}") long dashboardSeconds,
                              @Value("${app.cache.report-seconds:30}") long reportSeconds,
                              @Value("${app.cache.properties-seconds:30}") long propertiesSeconds,
                              @Value("${app.cache.lookup-seconds:300}") long lookupSeconds,
                              @Value("${app.cache.max-entries:200}") int maxEntries,
                              Clock clock) {
        if (!enabled) {
            return new NoOpCacheManager();
        }
        Duration lookup = Duration.ofSeconds(lookupSeconds);
        return new TtlCacheManager(Map.of(
                "dashboard", Duration.ofSeconds(dashboardSeconds),
                "revenue", Duration.ofSeconds(reportSeconds),
                "properties", Duration.ofSeconds(propertiesSeconds),
                "servicePlans", lookup,
                "serviceRates", lookup), lookup, maxEntries, clock);
    }
}
