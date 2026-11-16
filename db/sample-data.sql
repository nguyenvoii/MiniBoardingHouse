-- ============================================================================
-- DỮ LIỆU MẪU CHO DEV/DEMO (chạy SAU db/group-7.sql và db/seed.sql).
-- Nạp 1 khu trọ, 3 tầng, 12 phòng, 8 khách thuê, đơn giá điện nước, dịch vụ, tài khoản ngân hàng,
-- 2 kỳ ghi chỉ số (10 và 11/2026) kèm chỉ số từng phòng, vài khoản chi phí.
-- Chỉ chạy khi DB CHƯA có khu trọ nào, nên chạy lại nhiều lần vẫn an toàn.
-- Mã phòng và mã khách khớp với dữ liệu mẫu của giao diện (frontend/src/data/sample.ts).
-- Hợp đồng, kỳ tính tiền, hóa đơn, thanh toán thì tạo bằng giao diện hoặc API, không nạp ở đây.
-- ============================================================================
USE [MiniBoardingHouseDB];
GO
SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (SELECT 1 FROM [properties])
BEGIN
    DECLARE @u INT = (SELECT TOP 1 [user_id] FROM [users] WHERE [email] = 'admin@minihouse.local');
    IF @u IS NULL
    BEGIN
        RAISERROR(N'Chưa có tài khoản admin, hãy chạy db/seed.sql trước.', 16, 1);
        RETURN;
    END

    INSERT INTO [properties] ([landlord_id], [property_name], [address])
    VALUES (@u, N'Khu trọ Stayly', N'12 Nguyễn Trãi, Quận 1');
    DECLARE @p INT = SCOPE_IDENTITY();

    INSERT INTO [property_floors] ([property_id], [floor_number], [floor_name])
    VALUES (@p, 1, N'Tầng 1'), (@p, 2, N'Tầng 2'), (@p, 3, N'Tầng 3');
    DECLARE @f1 INT = (SELECT [floor_id] FROM [property_floors] WHERE [property_id] = @p AND [floor_number] = 1);
    DECLARE @f2 INT = (SELECT [floor_id] FROM [property_floors] WHERE [property_id] = @p AND [floor_number] = 2);
    DECLARE @f3 INT = (SELECT [floor_id] FROM [property_floors] WHERE [property_id] = @p AND [floor_number] = 3);

    INSERT INTO [rooms] ([property_id], [floor_id], [room_code], [area_sqm], [max_occupants], [base_rent_price],
                         [deposit_amount], [initial_electric_meter], [initial_water_meter], [status])
    VALUES
    (@p, @f1, 'P101', 22, 2, 3000000, 3000000, 100, 10, 'VACANT'),
    (@p, @f1, 'P102', 20, 2, 2800000, 2800000, 100, 10, 'VACANT'),
    (@p, @f1, 'P103', 20, 2, 2800000, 2800000, 100, 10, 'VACANT'),
    (@p, @f1, 'P104', 25, 3, 3300000, 3300000, 100, 10, 'VACANT'),
    (@p, @f2, 'P201', 22, 2, 3100000, 3100000, 100, 10, 'VACANT'),
    (@p, @f2, 'P202', 20, 2, 2900000, 2900000, 100, 10, 'REPAIRING'),
    (@p, @f2, 'P203', 20, 2, 2900000, 2900000, 100, 10, 'VACANT'),
    (@p, @f2, 'P204', 25, 3, 3400000, 3400000, 100, 10, 'VACANT'),
    (@p, @f3, 'P301', 22, 2, 3200000, 3200000, 100, 10, 'VACANT'),
    (@p, @f3, 'P302', 20, 2, 3000000, 3000000, 100, 10, 'VACANT'),
    (@p, @f3, 'P303', 20, 2, 3000000, 3000000, 100, 10, 'VACANT'),
    (@p, @f3, 'P304', 25, 3, 3500000, 3500000, 100, 10, 'VACANT');

    INSERT INTO [tenants] ([full_name], [phone_number], [identity_number], [email]) VALUES
    (N'Nguyễn Văn An',    '0901234567', '079201001234', 'an.nguyen@example.com'),
    (N'Trần Thị Bích',    '0912345678', '079301002345', NULL),
    (N'Lê Minh Châu',     '0933456789', '079202003456', 'chau.le@example.com'),
    (N'Phạm Quốc Dũng',   '0944567890', '079203004567', NULL),
    (N'Võ Thị Hà',        '0955678901', '079304005678', NULL),
    (N'Đặng Hoàng Khang', '0966789012', '079205006789', NULL),
    (N'Bùi Ngọc Lan',     '0977890123', '079306007890', NULL),
    (N'Hồ Thanh Mai',     '0988901234', '079307008901', 'mai.ho@example.com');

    INSERT INTO [tenant_vehicles] ([tenant_id], [license_plate], [vehicle_type])
    SELECT [tenant_id], '59A1-0000' + CAST([tenant_id] AS VARCHAR(5)), 'MOTORBIKE'
    FROM [tenants] WHERE [tenant_id] IN (1, 2, 4, 5, 7, 8);

    INSERT INTO [service_rate_plans] ([plan_name], [electric_unit_price], [water_unit_price], [effective_start_date])
    VALUES (N'Bảng giá 2026', 3500, 15000, '2026-01-01');

    INSERT INTO [property_service_rates] ([property_id], [service_name], [charge_type], [unit_price])
    VALUES (@p, N'Internet', 'FIXED_ROOM', 100000), (@p, N'Gửi xe', 'PER_VEHICLE', 50000), (@p, N'Vệ sinh', 'PER_PERSON', 30000);

    INSERT INTO [bank_accounts] ([property_id], [bank_name], [account_number], [account_holder], [is_default], [is_active])
    VALUES (@p, N'Vietcombank', '0071001234567', N'CHU TRO STAYLY', 1, 1);

    INSERT INTO [utility_reading_periods] ([property_id], [period_name], [start_date], [end_date])
    VALUES (@p, N'Tháng 10/2026', '2026-10-01', '2026-10-31'), (@p, N'Tháng 11/2026', '2026-11-01', '2026-11-30');
    DECLARE @oct INT = (SELECT [period_id] FROM [utility_reading_periods] WHERE [property_id] = @p AND [start_date] = '2026-10-01');
    DECLARE @nov INT = (SELECT [period_id] FROM [utility_reading_periods] WHERE [property_id] = @p AND [start_date] = '2026-11-01');

    -- Chỉ số tháng 10: mỗi phòng dùng 60 + 7 x (mã số phòng) kWh; tháng 11 nhiều hơn một chút. P303 chưa có chỉ số tháng 11 (để thấy cảnh báo thiếu chỉ số).
    INSERT INTO [meter_readings] ([period_id], [room_id], [old_electric], [new_electric], [old_water], [new_water], [recorded_by_user_id])
    SELECT @oct, [room_id], 100, 100 + 60 + [room_id] * 7, 10, 10 + 3 + ([room_id] % 4), @u FROM [rooms] WHERE [property_id] = @p;
    INSERT INTO [meter_readings] ([period_id], [room_id], [old_electric], [new_electric], [old_water], [new_water], [recorded_by_user_id])
    SELECT @nov, [room_id], 100 + 60 + [room_id] * 7, 100 + 60 + [room_id] * 7 + 70 + [room_id] * 6,
           10 + 3 + ([room_id] % 4), 10 + 3 + ([room_id] % 4) + 4 + ([room_id] % 3), @u
    FROM [rooms] WHERE [property_id] = @p AND [room_code] <> 'P303';

    INSERT INTO [operating_expenses] ([property_id], [expense_category], [description], [amount], [expense_date], [recorded_by])
    VALUES
    (@p, N'Điện nước chung', N'Hóa đơn điện hành lang', 420000, '2026-11-08', @u),
    (@p, N'Bảo trì', N'Sửa máy bơm', 650000, '2026-11-12', @u),
    (@p, N'Bảo trì', N'Thay bóng đèn', 180000, '2026-09-15', @u),
    (@p, N'Vệ sinh', N'Thuê dọn cầu thang', 400000, '2026-10-05', @u);

    PRINT N'Đã nạp dữ liệu mẫu: 1 khu trọ, 12 phòng, 8 khách thuê.';
END
ELSE
    PRINT N'DB đã có khu trọ, bỏ qua dữ liệu mẫu.';
GO

-- Nhóm sự cố để người thuê chọn khi báo sửa chữa (giao diện người thuê dùng đúng các mã này). Chạy lại an toàn.
IF NOT EXISTS (SELECT 1 FROM [incident_categories])
BEGIN
    INSERT INTO [incident_categories] ([category_code], [category_name], [category_type], [description]) VALUES
    ('ELECTRIC_ISSUE',   N'Điện: mất điện, chập, nhảy aptomat', 'INCIDENT', NULL),
    ('WATER_ISSUE',      N'Nước: yếu, rò rỉ, tắc',               'INCIDENT', NULL),
    ('AIR_CONDITIONER',  N'Máy lạnh',                            'INCIDENT', NULL),
    ('DOOR_LOCK',        N'Cửa, ổ khóa',                         'INCIDENT', NULL),
    ('INTERNET',         N'Mạng Internet',                       'INCIDENT', NULL),
    ('METER_CHECK',      N'Nghi ngờ công tơ điện/nước tính sai', 'INCIDENT', NULL),
    ('OTHER_INCIDENT',   N'Sự cố khác',                          'INCIDENT', NULL);
    PRINT N'Đã nạp 7 nhóm sự cố.';
END
GO
