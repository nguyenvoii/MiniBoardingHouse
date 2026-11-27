-- Thong tin luu tru day du cho nguoi o ghep (khong dung ten hop dong): ngay sinh, dia chi thuong tru, ngay dang ky tam tru.
-- Chay lai an toan. Chay voi -d MiniBoardingHouseDB.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.room_residents', N'date_of_birth') IS NULL
    ALTER TABLE dbo.room_residents ADD date_of_birth DATE NULL;
IF COL_LENGTH(N'dbo.room_residents', N'permanent_address') IS NULL
    ALTER TABLE dbo.room_residents ADD permanent_address NVARCHAR(255) NULL;
IF COL_LENGTH(N'dbo.room_residents', N'temp_residence_registered_on') IS NULL
    ALTER TABLE dbo.room_residents ADD temp_residence_registered_on DATE NULL;

COMMIT TRANSACTION;
