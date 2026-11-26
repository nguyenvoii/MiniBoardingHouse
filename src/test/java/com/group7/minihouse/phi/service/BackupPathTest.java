package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Duong dan sao luu SQL Server ghi trong msdb khac kieu viet cua app.backup.dir (dau gach cheo, hoa thuong). */
class BackupPathTest {

    @Test
    void windowsPathsMatchIgnoringSeparatorStyleAndCase() {
        assertTrue(BackupService.samePath("C:/backups\\mbh-1.bak", "C:\\Backups\\mbh-1.bak"));
        assertTrue(BackupService.samePath("c:\\mbh\\a.bak", "C:/mbh/a.bak"));
    }

    @Test
    void differentFileOrFolderDoesNotMatch() {
        assertFalse(BackupService.samePath("C:\\backups\\a.bak", "C:\\backups\\b.bak"));
        assertFalse(BackupService.samePath("C:\\backups\\a.bak", "C:\\other\\a.bak"));
    }

    @Test
    void unixPathsStayCaseSensitive() {
        assertTrue(BackupService.samePath("/var/opt/mssql/a.bak", "/var/opt/mssql/a.bak"));
        assertFalse(BackupService.samePath("/var/opt/mssql/a.bak", "/var/opt/MSSQL/a.bak"));
    }
}
