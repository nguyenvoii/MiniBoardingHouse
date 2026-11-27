-- Chạy trên database Stayly trước khi nâng cấp ứng dụng. Có thể chạy lại.
IF OBJECT_ID(N'dbo.revoked_sessions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.revoked_sessions (
        session_id VARCHAR(36) NOT NULL PRIMARY KEY,
        expires_at BIGINT NOT NULL
    );
    CREATE INDEX IX_revoked_sessions_expires_at ON dbo.revoked_sessions (expires_at);
END;
GO
