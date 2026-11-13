-- ============================================================================
-- PATCH 01: cho phép nhiều user để trống email/SĐT (UNIQUE thường chỉ cho 1 NULL).
-- Dành cho DB ĐÃ TẠO TRƯỚC ĐÓ bằng group-7.sql cũ. DB mới tạo từ group-7.sql hiện tại không cần chạy.
-- Chạy lại nhiều lần vẫn an toàn.
-- ============================================================================
USE [MiniBoardingHouseDB];
GO
SET QUOTED_IDENTIFIER ON;
GO

DECLARE @sql NVARCHAR(MAX) = N'';
SELECT @sql += N'ALTER TABLE [users] DROP CONSTRAINT [' + kc.name + N'];'
FROM sys.key_constraints kc
JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE kc.parent_object_id = OBJECT_ID(N'dbo.users') AND kc.type = 'UQ'
  AND c.name IN (N'email', N'phone_number');
EXEC sp_executesql @sql;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_users_email' AND object_id = OBJECT_ID(N'dbo.users'))
    CREATE UNIQUE INDEX UX_users_email ON [users] ([email]) WHERE [email] IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_users_phone' AND object_id = OBJECT_ID(N'dbo.users'))
    CREATE UNIQUE INDEX UX_users_phone ON [users] ([phone_number]) WHERE [phone_number] IS NOT NULL;
GO
