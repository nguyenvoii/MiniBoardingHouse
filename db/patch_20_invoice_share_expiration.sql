-- Liên kết cũ chưa có hạn dùng sẽ hết hiệu lực. Chủ trọ có thể tạo liên kết mới.
IF COL_LENGTH(N'dbo.invoices', N'share_expires_at') IS NULL
    ALTER TABLE dbo.invoices ADD share_expires_at BIGINT NULL;
GO
