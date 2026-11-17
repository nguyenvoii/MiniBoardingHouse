-- ============================================================================
-- PATCH 02: thêm bảng checkin_inspections (UC124 Biên bản nhận phòng, entity CheckinInspection của Chi).
-- Dành cho DB ĐÃ TẠO TRƯỚC ĐÓ bằng group-7.sql cũ. DB mới tạo từ group-7.sql hiện tại đã có bảng này, không cần chạy.
-- Chạy lại nhiều lần vẫn an toàn.
-- ============================================================================
USE [MiniBoardingHouseDB];
GO

IF OBJECT_ID(N'dbo.checkin_inspections', N'U') IS NULL
BEGIN
    CREATE TABLE [checkin_inspections] (
        [inspection_id] INT IDENTITY(1,1) PRIMARY KEY,
        [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]),
        [tenant_id] INT NOT NULL FOREIGN KEY REFERENCES [tenants]([tenant_id]),
        [contract_id] INT NULL FOREIGN KEY REFERENCES [rental_contracts]([contract_id]),
        [checkin_date] DATE NOT NULL,
        [initial_electric_meter] INT NOT NULL,
        [initial_water_meter] INT NOT NULL,
        [key_handed_count] INT NOT NULL DEFAULT 1,
        [furnishings_condition] NVARCHAR(MAX) NULL,
        [inspection_photo_url] NVARCHAR(500) NULL,
        [notes] NVARCHAR(MAX) NULL,
        [tenant_confirmed] BIT NOT NULL DEFAULT 1,
        [inspected_by_name] NVARCHAR(100) NULL,
        [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
    );
    PRINT N'Đã tạo bảng checkin_inspections.';
END
ELSE
    PRINT N'Bảng checkin_inspections đã có, bỏ qua.';
GO
