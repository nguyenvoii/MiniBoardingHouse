-- Cấp tài khoản chủ trọ đầu tiên sau khi nạp dữ liệu nền ở chế độ máy chủ.
-- Chạy trong phiên quản trị SQL đã chọn cơ sở dữ liệu Stayly đích.
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;

DECLARE @expected_db SYSNAME = TRY_CONVERT(SYSNAME, SESSION_CONTEXT(N'stayly_owner_database'));
DECLARE @email NVARCHAR(4000) = TRIM(TRY_CONVERT(NVARCHAR(4000), SESSION_CONTEXT(N'stayly_owner_email')));
DECLARE @name NVARCHAR(4000) = TRIM(TRY_CONVERT(NVARCHAR(4000), SESSION_CONTEXT(N'stayly_owner_name')));
DECLARE @hash NVARCHAR(4000) = TRY_CONVERT(NVARCHAR(4000), SESSION_CONTEXT(N'stayly_owner_bcrypt'));
EXEC sys.sp_set_session_context @key = N'stayly_owner_bcrypt', @value = NULL;

IF @expected_db IS NULL OR @expected_db <> DB_NAME()
    THROW 50001, N'Chọn và xác nhận đúng cơ sở dữ liệu Stayly trước khi cấp tài khoản.', 1;
IF @email IS NULL OR @email NOT LIKE N'%_@_%' OR LEN(@email) > 255 OR LOWER(@email) = N'admin@minihouse.local'
   OR @name IS NULL OR LEN(@name) NOT BETWEEN 1 AND 100
    THROW 50002, N'Cần email và tên riêng của chủ trọ.', 1;
IF @hash IS NULL OR DATALENGTH(@hash) <> 120 OR @hash COLLATE Latin1_General_100_BIN2 NOT LIKE N'$2[aby]$[0-3][0-9]$%'
   OR SUBSTRING(@hash, 8, 53) COLLATE Latin1_General_100_BIN2 LIKE N'%[^./0-9A-Za-z]%'
   OR TRY_CONVERT(INT, SUBSTRING(@hash, 5, 2)) NOT BETWEEN 10 AND 31
   OR @hash COLLATE Latin1_General_100_BIN2 = N'$2a$10$Pc6uubpMhGt6uuP/TqMCyulqTZc2GQVvfgNqWPYx7ciLqNtJzs3uy'
    THROW 50003, N'Cần băm BCrypt của một mật khẩu mới, riêng cho chủ trọ.', 1;

BEGIN TRANSACTION;

DECLARE @role_id INT = (SELECT [role_id] FROM dbo.[roles] WHERE [role_code] = N'LANDLORD');
IF @role_id IS NULL
    THROW 50004, N'Nạp dữ liệu nền trước khi cấp tài khoản.', 1;
IF EXISTS (SELECT 1 FROM dbo.[users] WITH (UPDLOCK, HOLDLOCK))
    THROW 50005, N'Chỉ dùng lệnh này cho cơ sở dữ liệu Stayly chưa có tài khoản.', 1;

INSERT INTO dbo.[users] ([email], [password_hash], [full_name], [status])
VALUES (@email, @hash, @name, N'ACTIVE');

INSERT INTO dbo.[user_roles] ([user_id], [role_id])
VALUES (CONVERT(INT, SCOPE_IDENTITY()), @role_id);

COMMIT TRANSACTION;

PRINT N'Đã cấp tài khoản chủ trọ đầu tiên.';
