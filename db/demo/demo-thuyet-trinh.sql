-- ============================================================================
-- DỮ LIỆU DEMO THUYẾT TRÌNH 5 PHÚT (Nhóm 7, Stayly). Chạy SAU group-7.sql + seed.sql:
--   sqlcmd -S localhost -E -C -d MiniBoardingHouseDB -I -f 65001 -i db\demo\demo-thuyet-trinh.sql
-- Thêm khu "Khu trọ Bình An": 8 phòng, 5 khách đang ở, PHÒNG 101 TRỐNG, chị An CHƯA có trong hệ thống.
-- Hóa đơn tháng 10 và tháng 11 đủ trạng thái. Anh Bình (phòng 102) có tài khoản người thuê
-- (SĐT 0905000102, mật khẩu Admin@123), hóa đơn tháng 11 của anh CHƯA TRẢ.
-- Chạy lại nhiều lần an toàn (đã có khu "Bình An" thì bỏ qua). Muốn xóa: db\demo\demo-thuyet-trinh-xoa.sql
-- ============================================================================
SET QUOTED_IDENTIFIER ON;
GO

DECLARE @demo_hash NVARCHAR(60) = N'$2a$10$Pc6uubpMhGt6uuP/TqMCyulqTZc2GQVvfgNqWPYx7ciLqNtJzs3uy';
DECLARE @u INT = (
    SELECT [user_id] FROM [users] WHERE [email] = 'admin@minihouse.local'
        AND [password_hash] COLLATE Latin1_General_100_BIN2 = @demo_hash AND [status] = 'ACTIVE'
);
IF SESSION_CONTEXT(N'stayly_skip_demo_owner') IS NOT NULL OR @u IS NULL
    THROW 50006, N'Chỉ nạp bộ trình chiếu trên database demo còn tài khoản seed ban đầu.', 1;
ELSE IF EXISTS (SELECT 1 FROM [properties] WHERE [property_name] = N'Khu trọ Bình An')
    PRINT N'Đã có khu trọ Bình An, bỏ qua.';
ELSE
BEGIN
    SET XACT_ABORT ON;
    BEGIN TRAN;

    -- ---- Khu trọ, 2 tầng
    INSERT INTO [properties] ([landlord_id], [property_name], [address], [total_floors], [support_phone])
    VALUES (@u, N'Khu trọ Bình An', N'12 Nguyễn Văn Linh, Quận 7, TP.HCM', 2, '0901234567');
    DECLARE @p INT = SCOPE_IDENTITY();
    INSERT INTO [property_floors] ([property_id], [floor_number], [floor_name])
    VALUES (@p, 1, N'Tầng 1'), (@p, 2, N'Tầng 2');

    -- ---- Một dòng cho mỗi phòng (nguồn chung cho phòng, khách, hợp đồng, chỉ số)
    CREATE TABLE #r ([code] VARCHAR(10), [fl] INT, [rent] DECIMAL(18,2), [occ] BIT, [name] NVARCHAR(100), [phone] VARCHAR(20),
                     [cccd] VARCHAR(20), [start] DATE, [e0] INT, [e10] INT, [e11] INT, [w0] INT, [w10] INT, [w11] INT, [veh] INT,
                     [plate] VARCHAR(20), [oct] VARCHAR(20), [nov] VARCHAR(20));
    INSERT INTO #r VALUES
    ('101', 1, 2950000, 0, NULL,              NULL,         NULL,           NULL,         0,   0,   0,  0,  0, 0, 0, NULL,           NULL,      NULL),
    ('102', 1, 2700000, 1, N'Trần Văn Bình',  '0905000102', '079300000102', '2026-08-01', 120, 98,  91, 15, 6, 5, 1, '59A1-102.01', 'PAID',    'UNPAID'),
    ('103', 1, 2500000, 1, N'Lê Thị Cúc',     '0905000103', '079300000103', '2026-09-01', 80,  74,  69, 10, 5, 5, 1, '59B2-103.02', 'PAID',    'PAID'),
    ('104', 1, 2800000, 1, N'Phạm Quốc Dũng', '0905000104', '079300000104', '2026-07-01', 300, 112, 120, 40, 7, 8, 2, '59C3-104.03', 'PAID',    'PARTIAL'),
    ('201', 2, 3000000, 1, N'Võ Thị Em',      '0905000201', '079300000201', '2026-06-01', 210, 130, 142, 25, 8, 7, 0, NULL,           'OVERDUE', 'UNPAID'),
    ('202', 2, 3100000, 1, N'Đặng Minh Phúc', '0905000202', '079300000202', '2026-10-01', 15,  85,  90,  2, 6, 6, 1, '59D4-202.04', 'PAID',    'PAID'),
    ('203', 2, 3000000, 0, NULL,              NULL,         NULL,           NULL,         0,   0,   0,  0,  0, 0, 0, NULL,           NULL,      NULL),
    ('204', 2, 3200000, 0, NULL,              NULL,         NULL,           NULL,         0,   0,   0,  0,  0, 0, 0, NULL,           NULL,      NULL);

    INSERT INTO [rooms] ([property_id], [floor_id], [room_code], [area_sqm], [max_occupants], [base_rent_price], [deposit_amount],
                         [initial_electric_meter], [initial_water_meter], [status])
    SELECT @p, f.[floor_id], r.[code], 22 + (CAST(r.[code] AS INT) % 5), 3, r.[rent], r.[rent], r.[e0], r.[w0],
           CASE WHEN r.[occ] = 1 THEN 'RENTED' ELSE 'VACANT' END
    FROM #r r JOIN [property_floors] f ON f.[property_id] = @p AND f.[floor_number] = r.[fl];

    -- ---- Khách đang ở, xe, hợp đồng, lịch sử ở, biên bản nhận phòng
    INSERT INTO [tenants] ([full_name], [phone_number], [identity_number], [dob], [permanent_address], [occupation], [is_verified])
    SELECT r.[name], r.[phone], r.[cccd], DATEFROMPARTS(1994 + CAST(RIGHT(r.[code], 1) AS INT), 3, 15), N'Tỉnh Long An', N'Nhân viên văn phòng', 1
    FROM #r r WHERE r.[occ] = 1;

    INSERT INTO [tenant_vehicles] ([tenant_id], [license_plate], [vehicle_type], [brand], [color])
    SELECT t.[tenant_id], r.[plate], 'MOTORBIKE', N'Honda', N'Đen' FROM #r r JOIN [tenants] t ON t.[phone_number] = r.[phone] WHERE r.[veh] >= 1;
    INSERT INTO [tenant_vehicles] ([tenant_id], [license_plate], [vehicle_type], [brand], [color])
    SELECT t.[tenant_id], r.[plate] + 'B', 'MOTORBIKE', N'Yamaha', N'Xanh' FROM #r r JOIN [tenants] t ON t.[phone_number] = r.[phone] WHERE r.[veh] >= 2;

    INSERT INTO [rental_contracts] ([contract_code], [room_id], [tenant_id], [start_date], [end_date], [monthly_rent_price], [deposit_amount],
                                    [payment_cycle_months], [payment_due_day], [late_fee_per_day], [status])
    SELECT 'HD-BA-' + r.[code], rm.[room_id], t.[tenant_id], r.[start], DATEADD(DAY, -1, DATEADD(MONTH, 12, r.[start])),
           r.[rent], r.[rent], 1, 5, 20000, 'ACTIVE'
    FROM #r r JOIN [rooms] rm ON rm.[property_id] = @p AND rm.[room_code] = r.[code]
              JOIN [tenants] t ON t.[phone_number] = r.[phone];

    INSERT INTO [resident_stay_histories] ([tenant_id], [room_id], [start_date])
    SELECT c.[tenant_id], c.[room_id], c.[start_date] FROM [rental_contracts] c WHERE c.[contract_code] LIKE 'HD-BA-%';

    INSERT INTO [checkin_inspections] ([room_id], [tenant_id], [contract_id], [checkin_date], [initial_electric_meter], [initial_water_meter],
                                       [key_handed_count], [furnishings_condition], [notes], [tenant_confirmed], [inspected_by_name])
    SELECT c.[room_id], c.[tenant_id], c.[contract_id], c.[start_date], rm.[initial_electric_meter], rm.[initial_water_meter], 2,
           N'Giường, tủ quần áo, máy lạnh, bình nóng lạnh: tốt', N'Tường sạch, không hư hỏng', 1, N'Chủ trọ'
    FROM [rental_contracts] c JOIN [rooms] rm ON rm.[room_id] = c.[room_id] WHERE c.[contract_code] LIKE 'HD-BA-%';

    -- 1 người ở cùng phòng 103 (tính phí đầu người)
    INSERT INTO [room_residents] ([room_id], [tenant_id], [full_name], [phone_number], [relationship_with_primary], [start_date], [is_active])
    SELECT rm.[room_id], NULL, N'Lê Văn Cường', '0905000999', N'Anh em', '2026-09-01', 1
    FROM [rooms] rm WHERE rm.[property_id] = @p AND rm.[room_code] = '103';

    -- ---- Tiền cọc đã thu đủ (phiếu thu DEPOSIT, ghi chú đúng mẫu để hệ thống đếm cọc)
    INSERT INTO [receipt_vouchers] ([voucher_code], [voucher_type], [amount], [note], [created_at])
    SELECT 'PT-COC-BA-' + r.[code], 'DEPOSIT', r.[rent], N'Thu cọc hợp đồng HD-BA-' + r.[code] + N' phòng ' + r.[code],
           DATEADD(DAY, -1, CAST(r.[start] AS DATETIME2))
    FROM #r r WHERE r.[occ] = 1;

    -- ---- Đơn giá điện nước (dùng bảng giá đang có), dịch vụ, ngân hàng
    IF NOT EXISTS (SELECT 1 FROM [service_rate_plans] WHERE [effective_start_date] <= '2026-06-01')
        INSERT INTO [service_rate_plans] ([plan_name], [electric_unit_price], [water_unit_price], [effective_start_date])
        VALUES (N'Bảng giá 2026', 3500, 15000, '2026-01-01');
    DECLARE @ep DECIMAL(18,2) = (SELECT TOP 1 [electric_unit_price] FROM [service_rate_plans] WHERE [effective_start_date] <= '2026-10-01' ORDER BY [effective_start_date] DESC);
    DECLARE @wp DECIMAL(18,2) = (SELECT TOP 1 [water_unit_price] FROM [service_rate_plans] WHERE [effective_start_date] <= '2026-10-01' ORDER BY [effective_start_date] DESC);
    INSERT INTO [property_service_rates] ([property_id], [service_name], [charge_type], [unit_price])
    VALUES (@p, N'Internet', 'FIXED_ROOM', 100000), (@p, N'Rác và vệ sinh', 'FIXED_ROOM', 50000), (@p, N'Gửi xe', 'PER_VEHICLE', 70000);
    INSERT INTO [bank_accounts] ([property_id], [bank_name], [account_number], [account_holder], [is_default])
    VALUES (@p, N'Vietcombank', '0071001234567', N'NGUYEN VAN CHU TRO', 1);

    -- ---- Kỳ chỉ số điện nước tháng 10 và 11 (đã đóng)
    INSERT INTO [utility_reading_periods] ([property_id], [period_name], [start_date], [end_date], [is_closed])
    VALUES (@p, N'Tháng 10/2026', '2026-10-01', '2026-10-31', 1), (@p, N'Tháng 11/2026', '2026-11-01', '2026-11-30', 1);
    DECLARE @pr10 INT = (SELECT [period_id] FROM [utility_reading_periods] WHERE [property_id] = @p AND [start_date] = '2026-10-01');
    DECLARE @pr11 INT = (SELECT [period_id] FROM [utility_reading_periods] WHERE [property_id] = @p AND [start_date] = '2026-11-01');
    INSERT INTO [meter_readings] ([period_id], [room_id], [old_electric], [new_electric], [old_water], [new_water], [recorded_by_user_id])
    SELECT @pr10, rm.[room_id], r.[e0], r.[e0] + r.[e10], r.[w0], r.[w0] + r.[w10], @u
    FROM #r r JOIN [rooms] rm ON rm.[property_id] = @p AND rm.[room_code] = r.[code] WHERE r.[occ] = 1;
    INSERT INTO [meter_readings] ([period_id], [room_id], [old_electric], [new_electric], [old_water], [new_water], [recorded_by_user_id])
    SELECT @pr11, rm.[room_id], r.[e0] + r.[e10], r.[e0] + r.[e10] + r.[e11], r.[w0] + r.[w10], r.[w0] + r.[w10] + r.[w11], @u
    FROM #r r JOIN [rooms] rm ON rm.[property_id] = @p AND rm.[room_code] = r.[code] WHERE r.[occ] = 1;

    -- ---- Kỳ tính tiền 10 (đóng) và 11 (đã phát hành)
    INSERT INTO [monthly_billing_periods] ([property_id], [month_year], [creation_date], [status])
    VALUES (@p, '2026-10', '2026-10-31', 'CLOSED'), (@p, '2026-11', '2026-11-18', 'PUBLISHED');

    CREATE TABLE #f ([ym] VARCHAR(7), [code] VARCHAR(10), [rent] DECIMAL(18,2), [elec] DECIMAL(18,2), [water] DECIMAL(18,2), [svc] DECIMAL(18,2),
                     [ue] INT, [uw] INT, [veh] INT, [status] VARCHAR(20), [issue] DATE, [due] DATE);
    INSERT INTO #f
    SELECT '2026-10', r.[code], r.[rent], r.[e10] * @ep, r.[w10] * @wp, 150000 + 70000 * r.[veh], r.[e10], r.[w10], r.[veh], r.[oct], '2026-11-01', '2026-11-05'
    FROM #r r WHERE r.[occ] = 1;
    INSERT INTO #f
    SELECT '2026-11', r.[code], r.[rent], r.[e11] * @ep, r.[w11] * @wp, 150000 + 70000 * r.[veh], r.[e11], r.[w11], r.[veh], r.[nov], '2026-11-18', '2026-11-28'
    FROM #r r WHERE r.[occ] = 1;

    INSERT INTO [calculated_monthly_fees] ([billing_period_id], [room_id], [contract_id], [rent_amount], [electric_amount], [water_amount], [service_amount],
                                           [surcharge_amount], [discount_amount], [late_penalty_amount], [total_calculated_amount])
    SELECT bp.[billing_period_id], c.[room_id], c.[contract_id], f.[rent], f.[elec], f.[water], f.[svc], 0, 0, 0, f.[rent] + f.[elec] + f.[water] + f.[svc]
    FROM #f f JOIN [monthly_billing_periods] bp ON bp.[property_id] = @p AND bp.[month_year] = f.[ym]
              JOIN [rental_contracts] c ON c.[contract_code] = 'HD-BA-' + f.[code];

    INSERT INTO [fee_item_details] ([calculated_fee_id], [item_name], [quantity], [unit_price], [subtotal])
    SELECT cf.[calculated_fee_id], i.[n], i.[q], i.[up], i.[q] * i.[up]
    FROM #f f
    JOIN [monthly_billing_periods] bp ON bp.[property_id] = @p AND bp.[month_year] = f.[ym]
    JOIN [rental_contracts] c ON c.[contract_code] = 'HD-BA-' + f.[code]
    JOIN [calculated_monthly_fees] cf ON cf.[billing_period_id] = bp.[billing_period_id] AND cf.[contract_id] = c.[contract_id]
    CROSS APPLY (VALUES (N'Tiền phòng', CAST(1 AS DECIMAL(10,2)), f.[rent]),
                        (N'Điện', CAST(f.[ue] AS DECIMAL(10,2)), @ep),
                        (N'Nước', CAST(f.[uw] AS DECIMAL(10,2)), @wp),
                        (N'Internet', CAST(1 AS DECIMAL(10,2)), CAST(100000 AS DECIMAL(18,2))),
                        (N'Rác và vệ sinh', CAST(1 AS DECIMAL(10,2)), CAST(50000 AS DECIMAL(18,2))),
                        (N'Gửi xe', CAST(f.[veh] AS DECIMAL(10,2)), CAST(70000 AS DECIMAL(18,2)))) i([n], [q], [up])
    WHERE i.[q] > 0;

    -- ---- Hóa đơn: PAID = đủ, PARTIAL = 1.000.000, UNPAID/OVERDUE = chưa trả
    INSERT INTO [invoices] ([invoice_code], [calculated_fee_id], [tenant_id], [issue_date], [due_date], [total_amount], [paid_amount], [payment_status])
    SELECT 'BA' + REPLACE(f.[ym], '-', '') + '-' + f.[code], cf.[calculated_fee_id], c.[tenant_id], f.[issue], f.[due], cf.[total_calculated_amount],
           CASE f.[status] WHEN 'PAID' THEN cf.[total_calculated_amount] WHEN 'PARTIAL' THEN 1000000 ELSE 0 END,
           CASE f.[status] WHEN 'PARTIAL' THEN 'PARTIALLY_PAID' WHEN 'UNPAID' THEN 'UNPAID' WHEN 'OVERDUE' THEN 'OVERDUE' ELSE 'PAID' END
    FROM #f f
    JOIN [monthly_billing_periods] bp ON bp.[property_id] = @p AND bp.[month_year] = f.[ym]
    JOIN [rental_contracts] c ON c.[contract_code] = 'HD-BA-' + f.[code]
    JOIN [calculated_monthly_fees] cf ON cf.[billing_period_id] = bp.[billing_period_id] AND cf.[contract_id] = c.[contract_id];

    INSERT INTO [payment_transactions] ([invoice_id], [payment_method], [transaction_code], [amount_paid], [payment_date], [received_by_user_id], [status])
    SELECT i.[invoice_id], CASE WHEN RIGHT(i.[invoice_code], 1) IN ('2', '4') THEN 'CASH' ELSE 'QR_CODE' END,
           'GD-' + i.[invoice_code], i.[paid_amount], DATEADD(DAY, 1, CAST(i.[issue_date] AS DATETIME2)), @u, 'SUCCESS'
    FROM [invoices] i WHERE i.[paid_amount] > 0 AND i.[invoice_code] LIKE 'BA2026__-%';
    INSERT INTO [receipt_vouchers] ([voucher_code], [transaction_id], [voucher_type], [amount], [note])
    SELECT 'PT-' + i.[invoice_code], pt.[transaction_id], 'MONTHLY_RENT', pt.[amount_paid], N'Thu tiền phòng ' + i.[invoice_code]
    FROM [payment_transactions] pt JOIN [invoices] i ON i.[invoice_id] = pt.[invoice_id] WHERE pt.[transaction_code] LIKE 'GD-BA2026%';

    -- ---- Công nợ: phòng 201 quá hạn tháng 10, phòng 104 còn nợ phần chưa trả tháng 11
    INSERT INTO [tenant_debts] ([tenant_id], [room_id], [invoice_id], [opening_debt_balance], [current_period_debt], [total_outstanding_debt], [overdue_days], [debt_status])
    SELECT i.[tenant_id], c.[room_id], i.[invoice_id], 0, i.[remaining_amount], i.[remaining_amount],
           CASE WHEN i.[payment_status] = 'OVERDUE' THEN DATEDIFF(DAY, i.[due_date], '2026-11-19') ELSE 0 END,
           CASE WHEN i.[payment_status] = 'OVERDUE' THEN 'WARNING_OVERDUE' ELSE 'NORMAL' END
    FROM [invoices] i JOIN [calculated_monthly_fees] cf ON cf.[calculated_fee_id] = i.[calculated_fee_id]
         JOIN [rental_contracts] c ON c.[contract_id] = cf.[contract_id]
    WHERE i.[invoice_code] IN ('BA202610-201', 'BA202611-104');

    -- ---- Tài khoản người thuê trình chiếu cho anh Bình
    INSERT INTO [users] ([phone_number], [password_hash], [full_name], [status], [must_change_password])
    VALUES ('0905000102', @demo_hash, N'Trần Văn Bình', 'ACTIVE', 0);
    INSERT INTO [user_roles] ([user_id], [role_id])
    SELECT u2.[user_id], ro.[role_id] FROM [users] u2 CROSS JOIN [roles] ro WHERE u2.[phone_number] = '0905000102' AND ro.[role_code] = 'TENANT';

    DROP TABLE #f;
    DROP TABLE #r;
    COMMIT;
    PRINT N'Đã nạp khu trọ demo Bình An: 8 phòng, 5 khách, hóa đơn tháng 10 và 11.';
END
GO
