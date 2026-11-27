package com.group7.minihouse.shared.monitoring;

import com.group7.minihouse.shared.cache.TtlCache;
import com.group7.minihouse.shared.cache.TtlCacheManager;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/** Doc nhanh tinh trang may chu: JVM, RAM may, ket noi SQL Server, o dia luu file, cache. Chi doc, khong ghi gi. */
@Service
public class SystemStatusService {

    public record App(String name, String javaVersion, String springBootVersion, Instant startedAt, long uptimeSeconds, Instant serverTime) {}

    public record Jvm(long heapUsedMb, long heapMaxMb, long nonHeapUsedMb, int threads, int peakThreads, int processors,
                      Double processCpuPercent) {}

    public record Host(String os, long totalMemoryMb, long freeMemoryMb, Double systemLoad) {}

    public record Pool(int active, int idle, int total, int waiting, int max) {}

    public record Database(boolean up, long latencyMs, String name, String version, String edition, Long sqlProcessMemoryMb,
                           String sqlMemoryNote, Pool pool, String error) {}

    public record Disk(String path, long totalGb, long usableGb, int usedPercent) {}

    public record CacheInfo(String name, long ttlSeconds, int size, long hits, long misses, long hitPercent) {}

    public record Status(String overall, List<String> warnings, App app, Jvm jvm, Host host, Database database, Disk disk,
                         boolean cacheEnabled, List<CacheInfo> caches) {}

    private final DataSource dataSource;
    private final CacheManager cacheManager;
    private final Clock clock;
    private final String uploadDir;

    public SystemStatusService(DataSource dataSource, CacheManager cacheManager, Clock clock,
                               @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.dataSource = dataSource;
        this.cacheManager = cacheManager;
        this.clock = clock;
        this.uploadDir = uploadDir;
    }

    public Status status() {
        List<String> warnings = new java.util.ArrayList<>();
        App app = app();
        Jvm jvm = jvm();
        Host host = host();
        Database db = database();
        Disk disk = disk();
        if (!db.up()) {
            warnings.add("Không kết nối được SQL Server: " + db.error());
        } else if (db.latencyMs() > 1000) {
            warnings.add("SQL Server phản hồi chậm (" + db.latencyMs() + " ms)");
        }
        if (db.pool() != null && db.pool().waiting() > 0) {
            warnings.add(db.pool().waiting() + " yêu cầu đang chờ kết nối DB (pool đầy)");
        }
        if (jvm.heapMaxMb() > 0 && jvm.heapUsedMb() * 100 / jvm.heapMaxMb() >= 90) {
            warnings.add("Bộ nhớ Java đã dùng trên 90%");
        }
        if (disk != null && disk.usedPercent() >= 90) {
            warnings.add("Ổ đĩa lưu file đã đầy trên 90%");
        }
        String overall = !db.up() ? "DOWN" : warnings.isEmpty() ? "OK" : "WARN";
        return new Status(overall, warnings, app, jvm, host, db, disk, cacheManager instanceof TtlCacheManager, caches());
    }

    /** Xoa moi cache (vi du ngay sau khi sua du lieu truc tiep trong SQL). Tra ve so cache da xoa. */
    public int clearCaches() {
        if (cacheManager instanceof TtlCacheManager ttl) {
            int n = ttl.getCacheNames().size();
            ttl.clearAll();
            return n;
        }
        return 0;
    }

    private App app() {
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        return new App("Stayly", System.getProperty("java.version"), SpringBootVersion.getVersion(),
                Instant.ofEpochMilli(rt.getStartTime()), rt.getUptime() / 1000, Instant.now(clock));
    }

    private Jvm jvm() {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        ThreadMXBean th = ManagementFactory.getThreadMXBean();
        Double cpu = null;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            double v = os.getProcessCpuLoad();
            cpu = v < 0 ? null : Math.round(v * 1000) / 10.0;
        }
        return new Jvm(mem.getHeapMemoryUsage().getUsed() >> 20, mem.getHeapMemoryUsage().getMax() >> 20,
                mem.getNonHeapMemoryUsage().getUsed() >> 20, th.getThreadCount(), th.getPeakThreadCount(),
                Runtime.getRuntime().availableProcessors(), cpu);
    }

    private Host host() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        long total = 0;
        long free = 0;
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            total = sun.getTotalMemorySize() >> 20;
            free = sun.getFreeMemorySize() >> 20;
        }
        double load = os.getSystemLoadAverage();
        return new Host(os.getName() + " " + os.getArch(), total, free, load < 0 ? null : Math.round(load * 100) / 100.0);
    }

    private Database database() {
        Pool pool = null;
        if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
            HikariPoolMXBean p = hikari.getHikariPoolMXBean();
            pool = new Pool(p.getActiveConnections(), p.getIdleConnections(), p.getTotalConnections(), p.getThreadsAwaitingConnection(),
                    hikari.getMaximumPoolSize());
        }
        long t0 = System.nanoTime();
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT 1")) {
                r.next();
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            String version = null;
            String edition = null;
            try (ResultSet r = s.executeQuery("SELECT CAST(SERVERPROPERTY('ProductVersion') AS NVARCHAR(50)), "
                    + "CAST(SERVERPROPERTY('Edition') AS NVARCHAR(100))")) {
                if (r.next()) {
                    version = r.getString(1);
                    edition = r.getString(2);
                }
            }
            Long sqlMem = null;
            String note = null;
            try (ResultSet r = s.executeQuery("SELECT physical_memory_in_use_kb / 1024 FROM sys.dm_os_process_memory")) {
                if (r.next()) {
                    sqlMem = r.getLong(1);
                }
            } catch (Exception denied) {
                note = "Tài khoản của ứng dụng không có quyền VIEW SERVER STATE nên không đọc được RAM của SQL Server";
            }
            return new Database(true, ms, c.getCatalog(), version, edition, sqlMem, note, pool, null);
        } catch (Exception e) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            return new Database(false, ms, null, null, null, null, null, pool, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private Disk disk() {
        File f = new File(uploadDir).getAbsoluteFile();
        while (f != null && !f.exists()) {
            f = f.getParentFile();
        }
        if (f == null) {
            return null;
        }
        long total = f.getTotalSpace();
        long usable = f.getUsableSpace();
        int used = total <= 0 ? 0 : (int) ((total - usable) * 100 / total);
        return new Disk(new File(uploadDir).getAbsolutePath(), total >> 30, usable >> 30, used);
    }

    private List<CacheInfo> caches() {
        if (!(cacheManager instanceof TtlCacheManager ttl)) {
            return List.of();
        }
        return ttl.stats().stream().map((TtlCache.Stats s) ->
                new CacheInfo(s.name(), s.ttlSeconds(), s.size(), s.hits(), s.misses(), Math.round(s.hitRate() * 100))).toList();
    }
}
