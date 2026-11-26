package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.BackupInfo;
import com.group7.minihouse.shared.exception.BusinessException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;
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
 * app.backup.dir. Duong dan va lich su sao luu duoc doc tren SQL Server.
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
            String directory = backupDir(s);
            String file = backupPath(directory, name);
            s.execute("BACKUP DATABASE " + bracket(db) + " TO DISK = N'" + literal(file) + "' WITH INIT, CHECKSUM");
            activityLogService.log(actorId, "BACKUP_DB", "Sao luu co so du lieu: " + name, ip);
            log.info("Da sao luu CSDL vao {}", file);
            return backups(c, db, directory).getOrDefault(name, new BackupInfo(name, null, null));
        } catch (SQLException | IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Khong sao luu duoc, kiem tra quyen ghi cua SQL Server vao thu muc sao luu: " + e.getMessage());
        }
    }

    /** Cac ban sao luu cua CSDL hien tai, moi nhat truoc. */
    public List<BackupInfo> list() {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            return List.copyOf(backups(c, databaseName(s), backupDir(s)).values());
        } catch (SQLException | IOException e) {
            log.warn("Khong liet ke duoc lich su sao luu: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, BackupInfo> backups(Connection c, String db, String dir) throws SQLException {
        Map<String, BackupInfo> result = new LinkedHashMap<>();
        try (PreparedStatement query = c.prepareStatement("""
                SELECT media.physical_device_name, history.backup_size, history.backup_finish_date,
                       history.database_name
                FROM msdb.dbo.backupset history
                JOIN msdb.dbo.backupmediafamily media ON media.media_set_id = history.media_set_id
                WHERE history.type = 'D' AND media.device_type = 2
                ORDER BY history.backup_set_id DESC
                """);
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                String path = rows.getString(1);
                String name = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
                if (!FILE_NAME.matcher(name).matches() || !backupPath(dir, name).equals(path)) {
                    continue;
                }
                // A newer backup of another database may have overwritten this file.
                result.putIfAbsent(name, db.equals(rows.getString(4))
                        ? new BackupInfo(name, rows.getBigDecimal(2).longValue(), rows.getTimestamp(3).toLocalDateTime())
                        : new BackupInfo(name, null, null));
            }
        }
        result.values().removeIf(info -> info.sizeBytes() == null);
        return result;
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
        String file;
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            db = databaseName(s);
            if (!db.equals(confirm)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Phai gui confirm = ten co so du lieu de xac nhan phuc hoi (ghi de toan bo du lieu hien tai)");
            }
            String directory = backupDir(s);
            if (!backups(c, db, directory).containsKey(fileName)) {
                throw BusinessException.notFound("Ban sao luu");
            }
            file = backupPath(directory, fileName);
        } catch (SQLException | IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Khong doc duoc thong tin may chu CSDL");
        }
        try (Connection c = master.open(); Statement s = c.createStatement()) {
            try (ResultSet header = s.executeQuery("RESTORE HEADERONLY FROM DISK = N'" + literal(file) + "'")) {
                if (!header.next() || !db.equals(header.getString("DatabaseName")) || header.getInt("BackupType") != 1) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "Ban sao luu khong thuoc co so du lieu hien tai");
                }
            }
            s.execute("RESTORE VERIFYONLY FROM DISK = N'" + literal(file) + "' WITH CHECKSUM");
            String setMultiUser = "ALTER DATABASE " + bracket(db) + " SET MULTI_USER";
            s.execute("ALTER DATABASE " + bracket(db) + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            try {
                s.execute("RESTORE DATABASE " + bracket(db) + " FROM DISK = N'" + literal(file) + "' WITH REPLACE");
            } catch (SQLException restoreFailure) {
                try {
                    s.execute(setMultiUser);
                } catch (SQLException recoveryFailure) {
                    restoreFailure.addSuppressed(recoveryFailure);
                    log.error("Khong dua duoc CSDL ve MULTI_USER, hay chay tay: {}", setMultiUser, recoveryFailure);
                }
                throw restoreFailure;
            }
            s.execute(setMultiUser);
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

    private String backupDir(Statement s) throws SQLException, IOException {
        if (!configuredDir.isEmpty()) {
            return configuredDir;
        }
        try (ResultSet rs = s.executeQuery("SELECT CAST(SERVERPROPERTY('InstanceDefaultBackupPath') AS NVARCHAR(500))")) {
            if (rs.next() && rs.getString(1) != null) {
                return rs.getString(1);
            }
        }
        throw new IOException("Chua cau hinh app.backup.dir va SQL Server khong co thu muc sao luu mac dinh");
    }

    private static String backupPath(String dir, String name) {
        if (dir.endsWith("/") || dir.endsWith("\\")) {
            return dir + name;
        }
        return dir + (dir.contains("\\") || dir.matches("^[A-Za-z]:.*") ? "\\" : "/") + name;
    }

    /** Ten doi tuong SQL Server: dat trong [ ], ] thanh ]]. */
    private static String bracket(String name) {
        return "[" + name.replace("]", "]]") + "]";
    }

    /** Duong dan dat trong N'...': ' thanh ''. (Ten file sao luu da duoc kiem tra theo mau nen khong co ky tu la.) */
    private static String literal(String p) {
        return p.replace("'", "''");
    }
}
