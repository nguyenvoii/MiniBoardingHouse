-- Xóa SẠCH dữ liệu do db/sample-data-day.sql nạp (khu trọ có chữ "(DEMO)" và khách có SĐT 0700...).
-- Không đụng bộ mẫu cũ. Nếu đã tạo hợp đồng/hóa đơn trên phòng DEMO thì phải xóa chúng trước (script này sẽ báo lỗi khóa ngoại).
USE [MiniBoardingHouseDB];
GO
SET XACT_ABORT ON;
BEGIN TRAN;
DELETE m FROM [meter_readings] m JOIN [rooms] r ON r.[room_id] = m.[room_id] JOIN [properties] p ON p.[property_id] = r.[property_id] WHERE p.[property_name] LIKE N'%(DEMO)';
DELETE FROM [utility_reading_periods] WHERE [property_id] IN (SELECT [property_id] FROM [properties] WHERE [property_name] LIKE N'%(DEMO)');
DELETE FROM [operating_expenses] WHERE [property_id] IN (SELECT [property_id] FROM [properties] WHERE [property_name] LIKE N'%(DEMO)');
DELETE FROM [tenant_vehicles] WHERE [tenant_id] IN (SELECT [tenant_id] FROM [tenants] WHERE [phone_number] LIKE '0700%');
DELETE FROM [tenants] WHERE [phone_number] LIKE '0700%';
DELETE FROM [room_furnishings] WHERE [room_id] IN (SELECT r.[room_id] FROM [rooms] r JOIN [properties] p ON p.[property_id] = r.[property_id] WHERE p.[property_name] LIKE N'%(DEMO)');
DELETE FROM [rooms] WHERE [property_id] IN (SELECT [property_id] FROM [properties] WHERE [property_name] LIKE N'%(DEMO)');
DELETE FROM [property_floors] WHERE [property_id] IN (SELECT [property_id] FROM [properties] WHERE [property_name] LIKE N'%(DEMO)');
DELETE FROM [property_service_rates] WHERE [property_id] IN (SELECT [property_id] FROM [properties] WHERE [property_name] LIKE N'%(DEMO)');
DELETE FROM [bank_accounts] WHERE [property_id] IN (SELECT [property_id] FROM [properties] WHERE [property_name] LIKE N'%(DEMO)');
DELETE FROM [properties] WHERE [property_name] LIKE N'%(DEMO)';
COMMIT;
PRINT N'Đã xóa dữ liệu DEMO dày.';
GO
