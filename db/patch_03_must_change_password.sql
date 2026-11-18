-- Mật khẩu tạm (cấp tài khoản người thuê, thêm nhân viên, cấp lại mật khẩu) bắt đổi ở lần đăng nhập đầu.
-- Chạy lại nhiều lần an toàn. Chạy 1 lần trên mỗi máy đã có DB cũ, TRƯỚC khi chạy app bản mới.
USE [MiniBoardingHouseDB];
GO
IF COL_LENGTH('dbo.users', 'must_change_password') IS NULL
BEGIN
    ALTER TABLE [dbo].[users] ADD [must_change_password] BIT NOT NULL
        CONSTRAINT [DF_users_must_change_password] DEFAULT 0;
    PRINT N'Đã thêm cột users.must_change_password.';
END
ELSE
    PRINT N'Cột users.must_change_password đã có, bỏ qua.';
GO
