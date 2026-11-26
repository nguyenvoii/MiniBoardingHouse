package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.BackupInfo;
import com.group7.minihouse.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

/** UC122 sao luu/phuc hoi DB SQL Server (mock JDBC, khong can DB that). */
class BackupServiceTest {

    @TempDir Path dir;
    DataSource ds = mock(DataSource.class);
    Connection conn = mock(Connection.class);
    Statement stmt = mock(Statement.class);
    PreparedStatement historyQuery = mock(PreparedStatement.class);
    ResultSet history = mock(ResultSet.class);
    ResultSet rs = mock(ResultSet.class);
    Connection masterConn = mock(Connection.class);
    Statement masterStmt = mock(Statement.class);
    ActivityLogService log = mock(ActivityLogService.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-11-16T03:05:09Z"), ZoneOffset.UTC);
    BackupService service;
    final List<String> masterSql = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        when(conn.prepareStatement(anyString())).thenReturn(historyQuery);
        when(historyQuery.executeQuery()).thenReturn(history);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery("SELECT DB_NAME()")).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("MiniBoardingHouseDB");
        when(masterConn.createStatement()).thenReturn(masterStmt);
        ResultSet header = mock(ResultSet.class);
        when(masterStmt.executeQuery(org.mockito.ArgumentMatchers.startsWith("RESTORE HEADERONLY"))).thenReturn(header);
        when(header.next()).thenReturn(true);
        when(header.getString("DatabaseName")).thenReturn("MiniBoardingHouseDB");
        when(header.getInt("BackupType")).thenReturn(1);
        when(masterStmt.execute(anyString())).thenAnswer(inv -> { masterSql.add(inv.getArgument(0)); return false; });
        service = new BackupService(ds, () -> masterConn, log, clock, dir.toString());
    }

    @Test
    void backupRunsBackupDatabaseIntoConfiguredDirectoryAndLogs() throws Exception {
        BackupInfo info = service.backup(2, "1.1.1.1");

        org.mockito.ArgumentCaptor<String> cap = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(stmt).execute(cap.capture());
        String sql = cap.getValue();
        assertTrue(sql.startsWith("BACKUP DATABASE [MiniBoardingHouseDB] TO DISK = N'"), sql);
        assertTrue(sql.contains(dir.resolve(info.name()).toString()), sql);
        assertTrue(sql.contains("INIT"), sql);
        verify(log).log(2, "BACKUP_DB", "Sao luu co so du lieu: " + info.name(), "1.1.1.1");
    }

    @Test
    void backupNameFollowsServerClock() {
        BackupInfo info = service.backup(2, "ip");
        assertTrue(info.name().matches("mbh-\\d{8}-\\d{6}\\.bak"), info.name());
    }

    @Test
    void listShowsOnlyBakFilesNewestFirst() throws Exception {
        when(history.next()).thenReturn(true, true, true, false);
        when(history.getString(1)).thenReturn(dir.resolve("mbh-20261116-030000.bak").toString(),
                dir.resolve("mbh-20261115-030000.bak").toString(), dir.resolve("ghi-chu.txt").toString());
        when(history.getString(4)).thenReturn("MiniBoardingHouseDB");
        when(history.getBigDecimal(2)).thenReturn(BigDecimal.valueOf(2), BigDecimal.ONE);
        when(history.getTimestamp(3)).thenReturn(Timestamp.from(clock.instant()));

        List<BackupInfo> list = service.list();

        assertEquals(List.of("mbh-20261116-030000.bak", "mbh-20261115-030000.bak"),
                list.stream().map(BackupInfo::name).toList());
        assertEquals(2L, list.get(0).sizeBytes());
    }

    private void backupInHistory() throws SQLException {
        when(history.next()).thenReturn(true, false);
        when(history.getString(1)).thenReturn(dir.resolve("mbh-20261116-030000.bak").toString());
        when(history.getString(4)).thenReturn("MiniBoardingHouseDB");
        when(history.getBigDecimal(2)).thenReturn(BigDecimal.ONE);
        when(history.getTimestamp(3)).thenReturn(Timestamp.from(clock.instant()));
    }

    @Test
    void restoreRunsSingleUserRestoreReplaceThenMultiUser() throws Exception {
        backupInHistory();

        service.restore("mbh-20261116-030000.bak", "MiniBoardingHouseDB");

        assertEquals(4, masterSql.size(), masterSql.toString());
        assertTrue(masterSql.get(0).startsWith("RESTORE VERIFYONLY FROM DISK = N'"));
        assertEquals("ALTER DATABASE [MiniBoardingHouseDB] SET SINGLE_USER WITH ROLLBACK IMMEDIATE", masterSql.get(1));
        assertTrue(masterSql.get(2).startsWith("RESTORE DATABASE [MiniBoardingHouseDB] FROM DISK = N'"));
        assertTrue(masterSql.get(2).endsWith("WITH REPLACE"));
        assertEquals("ALTER DATABASE [MiniBoardingHouseDB] SET MULTI_USER", masterSql.get(3));
    }

    @Test
    void restoreAlwaysReturnsDatabaseToMultiUserEvenWhenRestoreFails() throws Exception {
        backupInHistory();
        when(masterStmt.execute(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            masterSql.add(sql);
            if (sql.startsWith("RESTORE DATABASE")) throw new SQLException("hong");
            return false;
        });

        assertThrows(BusinessException.class, () -> service.restore("mbh-20261116-030000.bak", "MiniBoardingHouseDB"));

        assertEquals("ALTER DATABASE [MiniBoardingHouseDB] SET MULTI_USER", masterSql.get(masterSql.size() - 1));
    }

    @Test
    void invalidBackupDoesNotChangeDatabaseAccess() throws Exception {
        backupInHistory();
        org.mockito.Mockito.doAnswer(inv -> {
            masterSql.add(inv.getArgument(0));
            throw new SQLException("backup verification failed");
        }).when(masterStmt).execute(anyString());

        assertThrows(BusinessException.class, () -> service.restore("mbh-20261116-030000.bak", "MiniBoardingHouseDB"));

        assertEquals(1, masterSql.size());
        assertTrue(masterSql.get(0).startsWith("RESTORE VERIFYONLY"));
    }

    @Test
    void restoreFailsWhenDatabaseCannotReturnToMultiUser() throws Exception {
        backupInHistory();
        org.mockito.Mockito.doAnswer(inv -> {
            String sql = inv.getArgument(0);
            masterSql.add(sql);
            if (sql.endsWith("SET MULTI_USER")) throw new SQLException("multi-user failed");
            return false;
        }).when(masterStmt).execute(anyString());

        assertThrows(BusinessException.class, () -> service.restore("mbh-20261116-030000.bak", "MiniBoardingHouseDB"));

        assertEquals(4, masterSql.size());
        assertTrue(masterSql.get(3).endsWith("SET MULTI_USER"));
    }

    @Test
    void restoreRejectsBadNameWrongConfirmAndMissingFile() throws Exception {
        for (String bad : List.of("../evil.bak", "mbh-1.bak'; DROP DATABASE x;--", "a/b.bak", "x.txt")) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                    () -> service.restore(bad, "MiniBoardingHouseDB")).getStatus());
        }
        backupInHistory();
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.restore("mbh-20261116-030000.bak", "sai-ten")).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.restore("mbh-20200101-000000.bak", "MiniBoardingHouseDB")).getStatus());
        verify(masterStmt, never()).execute(anyString());
    }
}
