-- Anh CCCD (mat truoc / mat sau) cua nguoi o cung. Chay lai an toan. Chay voi -d MiniBoardingHouseDB.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.room_residents', N'id_front_image_url') IS NULL
    ALTER TABLE dbo.room_residents ADD id_front_image_url NVARCHAR(500) NULL;
IF COL_LENGTH(N'dbo.room_residents', N'id_back_image_url') IS NULL
    ALTER TABLE dbo.room_residents ADD id_back_image_url NVARCHAR(500) NULL;

COMMIT TRANSACTION;
