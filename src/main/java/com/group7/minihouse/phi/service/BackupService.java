package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.BackupInfo;
import com.group7.minihouse.shared.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * UC122 sao luu / phuc hoi co so du lieu SQL Server bang BACKUP/RESTORE DATABASE.
 * File .bak do chinh SQL Server ghi, nen thu muc phai la noi tai khoan dich vu SQL Server ghi duoc.
 * Mac dinh dung thu muc sao luu cua SQL Server (SERVERPROPERTY InstanceDefaultBackupPath); doi bang
 * app.backup.dir. Ung dung va SQL Server phai chay CUNG MAY de ung dung liet ke/kiem tra file.
 * Phuc hoi ghi de TOAN BO du lieu hien tai bang ban sao luu va ngat moi ket noi dang mo.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final Pattern FILE_NAME = Pattern.compile("mbh-\\d{8}-\\d{6}\\.bak");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Mo ket noi vao database master (phuc hoi can ket noi ngoai DB dang phuc hoi). */
    @FunctionalInterface
    interface MasterConnections {
        Connection open() throws SQLException;
    }

    private final DataSource dataSource;
    private final MasterConnections master;
    private final ActivityLogService activityLogService;
    private final Clock clock;
    private final String configuredDir;

    @Autowired
    public BackupService(DataSource dataSource, ActivityLogService activityLogService, Clock clock,
                         @Value("${app.backup.dir:}") String configuredDir,
                         @Value("${spring.datasource.url}") String url,
                         @Value("${spring.datasource.username}") String username,
                         @Value("${spring.datasource.password}") String password) {
        this(dataSource,
                () -> DriverManager.getConnection(url.replaceFirst("(?i)databaseName=[^;]*", "databaseName=master"),
                        username, password),
                activityLogService, clock, configuredDir);
    }

    BackupService(DataSource dataSource, MasterConnections master, ActivityLogService activityLogService,
                  Clock clock, String configuredDir) {
        this.dataSource = dataSource;
        this.master = master;
        this.activityLogService = activityLogService;
        this.clock = clock;
        this.configuredDir = configuredDir == null ? "" : configuredDir.trim();
    }

    /** Sao luu ngay bay gio vao file mbh-yyyyMMdd-HHmmss.bak. */
    public BackupInfo backup(Integer actorId, String ip) {
        String name = "mbh-" + LocalDateTime.now(clock).format(STAMP) + ".bak";
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            String db = databaseName(s);
            Path file = backupDir(s).resolve(name);
            s.execute("BACKUP DATABASE " + bracket(db) + " TO DISK = N'" + literal(file) + "' WITH INIT, CHECKSUM");
            activityLogService.log(actorId, "BACKUP_DB", "Sao luu co so du lieu: " + name, ip);
            log.info("Da sao luu CSDL vao {}", file);
            return info(file, name);
        } catch (SQLException | IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Khong sao luu duoc, kiem tra quyen ghi cua SQL Server vao thu muc sao luu: " + e.getMessage());
        }
    }

    /** Cac ban sao luu trong thu muc, moi nhat truoc. Rong neu khong doc duoc thu muc. */
    public List<BackupInfo> list() {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement();
             Stream<Path> files = Files.list(backupDir(s))) {
            return files.filter(p -> FILE_NAME.matcher(p.getFileName().toString()).matches())
                    .map(p -> {
                        try {
                            return info(p, p.getFileName().toString());
                        } catch (IOException e) {
                            return new BackupInfo(p.getFileName().toString(), null, null);
                        }
                    })
                    .sorted(Comparator.comparing(BackupInfo::name).reversed())
                    .toList();
        } catch (SQLException | IOException e) {
            log.warn("Khong liet ke duoc thu muc sao luu: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Phuc hoi tu 1 ban sao luu. confirm phai bang ten CSDL. Cac buoc: kiem tra file hop le, dua DB ve SINGLE_USER
     * (ngat ket noi khac), RESTORE ... WITH REPLACE, luon tra ve MULTI_USER.
     */
    public void restore(String fileName, String confirm) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ten file sao luu khong hop le");
        }
        String db;
        Path file;
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            db = databaseName(s);
            file = backupDir(s).resolve(fileName);
        } catch (SQLException | IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Khong doc duoc thong tin may chu CSDL");
        }
        if (!db.equals(confirm)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Phai gui confirm = ten co so du lieu de xac nhan phuc hoi (ghi de toan bo du lieu hien tai)");
        }
        if (!Files.isRegularFile(file)) {
            throw BusinessException.notFound("Ban sao luu");
        }
        try (Connection c = master.open(); Statement s = c.createStatement()) {
            s.execute("RESTORE VERIFYONLY FROM DISK = N'" + literal(file) + "'");
            try {
                s.execute("ALTER DATABASE " + bracket(db) + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                s.execute("RESTORE DATABASE " + bracket(db) + " FROM DISK = N'" + literal(file) + "' WITH REPLACE");
            } finally {
                try {
                    s.execute("ALTER DATABASE " + bracket(db) + " SET MULTI_USER");
                } catch (SQLException e) {
                    log.error("Khong dua duoc CSDL ve MULTI_USER, hay chay tay: ALTER DATABASE {} SET MULTI_USER",
                            bracket(db), e);
                }
            }
            log.warn("Da phuc hoi CSDL {} tu {}", db, file);
        } catch (SQLException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Phuc hoi that bai: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ noi bo

    private static String databaseName(Statement s) throws SQLException {
        try (ResultSet rs = s.executeQuery("SELECT DB_NAME()")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private Path backupDir(Statement s) throws SQLException, IOException {
        if (!configuredDir.isEmpty()) {
            return Path.of(configuredDir).toAbsolutePath().normalize();
        }
        try (ResultSet rs = s.executeQuery("SELECT CAST(SERVERPROPERTY('InstanceDefaultBackupPath') AS NVARCHAR(500))")) {
            if (rs.next() && rs.getString(1) != null) {
                return Path.of(rs.getString(1));
            }
        }
        throw new IOException("Chua cau hinh app.backup.dir va SQL Server khong co thu muc sao luu mac dinh");
    }

    private static BackupInfo info(Path file, String name) throws IOException {
        Long size = Files.exists(file) ? Files.size(file) : null;
        LocalDateTime modified = Files.exists(file)
                ? LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault()) : null;
        return new BackupInfo(name, size, modified);
    }

    /** Ten doi tuong SQL Server: dat trong [ ], ] thanh ]]. */
    private static String bracket(String name) {
        return "[" + name.replace("]", "]]") + "]";
    }

    /** Duong dan dat trong N'...': ' thanh ''. (Ten file sao luu da duoc kiem tra theo mau nen khong co ky tu la.) */
    private static String literal(Path p) {
        return p.toString().replace("'", "''");
    }
}
