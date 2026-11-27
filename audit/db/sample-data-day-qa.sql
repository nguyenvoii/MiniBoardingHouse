-- ============================================================================
-- DỮ LIỆU MẪU DÀY để test (chạy SAU db/group-7.sql và db/seed.sql). KHÔNG đụng bộ mẫu cũ (sample-data.sql).
-- Nạp thêm 3 khu trọ (tên có chữ "(DEMO)"), 100 phòng, 100 khách thuê kèm xe, đơn giá dịch vụ, ngân hàng,
-- 6 kỳ ghi chỉ số (06 đến 11/2026) cho mỗi khu với chỉ số từng phòng, và chi phí vận hành 6 tháng.
-- Hợp đồng, kỳ tính tiền, hóa đơn, thanh toán thì tạo bằng giao diện hoặc API như bộ mẫu cũ.
-- Chạy lại nhiều lần an toàn: đã có khu "(DEMO)" thì bỏ qua. Muốn xóa sạch: chạy db/sample-data-day-xoa.sql.
-- Mã phòng: H101.. (Hoa Sen), B101.. (Bình Minh), S101.. (Sao Mai). SĐT khách bắt đầu 0700, CCCD bắt đầu 0799.
-- ============================================================================
USE [MiniBoardingHouseQA_20261122];
GO
SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (SELECT 1 FROM [users] WHERE [email] = 'admin@minihouse.local')
    RAISERROR(N'Chưa có tài khoản admin, hãy chạy db/seed.sql trước.', 16, 1);
ELSE IF EXISTS (SELECT 1 FROM [properties] WHERE [property_name] LIKE N'%(DEMO)')
    PRINT N'Đã có dữ liệu DEMO dày, bỏ qua.';
ELSE
BEGIN
    SET XACT_ABORT ON;
    BEGIN TRAN;

    DECLARE @u INT = (SELECT TOP 1 [user_id] FROM [users] WHERE [email] = 'admin@minihouse.local');

    -- Bảng số 1..200 dùng để nhân dòng
    SELECT TOP 200 ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS n INTO #n FROM sys.all_objects a CROSS JOIN sys.all_objects b;

    -- 3 khu trọ: số tầng, số phòng mỗi tầng, mã chữ cái, giá thuê nền
    INSERT INTO [properties] ([landlord_id], [property_name], [address], [total_floors], [support_phone]) VALUES
    (@u, N'Khu trọ Hoa Sen (DEMO)',   N'45 Lê Văn Việt, TP. Thủ Đức',   5, '0281234001'),
    (@u, N'Khu trọ Bình Minh (DEMO)', N'88 Quang Trung, Quận Gò Vấp',   4, '0281234002'),
    (@u, N'Khu trọ Sao Mai (DEMO)',   N'12 Phan Văn Trị, Quận Bình Thạnh', 3, '0281234003');

    SELECT [property_id] AS pid, [total_floors] AS floors,
           CASE [support_phone] WHEN '0281234001' THEN 10 WHEN '0281234002' THEN 8 ELSE 6 END AS per_floor,
           CASE [support_phone] WHEN '0281234001' THEN 'H' WHEN '0281234002' THEN 'B' ELSE 'S' END AS pcode,
           CASE [support_phone] WHEN '0281234001' THEN 2700000 WHEN '0281234002' THEN 2300000 ELSE 3400000 END AS base_rent
    INTO #prop FROM [properties] WHERE [property_name] LIKE N'%(DEMO)';

    INSERT INTO [property_floors] ([property_id], [floor_number], [floor_name])
    SELECT p.pid, n.n, N'Tầng ' + CAST(n.n AS NVARCHAR(3)) FROM #prop p JOIN #n n ON n.n <= p.floors;

    -- Phòng: 100 phòng, diện tích 18-27 m2, giá tăng theo tầng. Cứ 10 phòng thì 1 phòng VACANT đang sửa/ngưng để có đủ trạng thái.
    INSERT INTO [rooms] ([property_id], [floor_id], [room_code], [area_sqm], [max_occupants], [base_rent_price],
                         [deposit_amount], [initial_electric_meter], [initial_water_meter], [status])
    SELECT p.pid, f.[floor_id],
           p.pcode + CAST(f.[floor_number] AS VARCHAR(2)) + RIGHT('0' + CAST(r.n AS VARCHAR(2)), 2),
           18 + (r.n % 4) * 3,
           CASE WHEN r.n % 4 >= 2 THEN 3 ELSE 2 END,
           p.base_rent + f.[floor_number] * 50000 + (r.n % 4) * 200000,
           p.base_rent + f.[floor_number] * 50000 + (r.n % 4) * 200000,
           100, 10,
           CASE WHEN r.n % 10 = 7 THEN 'REPAIRING' WHEN r.n % 10 = 9 AND f.[floor_number] = 1 THEN 'SUSPENDED' ELSE 'VACANT' END
    FROM #prop p
    JOIN [property_floors] f ON f.[property_id] = p.pid
    JOIN #n r ON r.n <= p.per_floor;

    INSERT INTO [room_furnishings] ([room_id], [item_name], [quantity], [condition_status])
    SELECT r.[room_id], v.item, v.qty, N'Mới / Tốt'
    FROM [rooms] r
    JOIN [properties] pr ON pr.[property_id] = r.[property_id] AND pr.[property_name] LIKE N'%(DEMO)'
    CROSS APPLY (VALUES (N'Giường', 1), (N'Tủ quần áo', 1), (N'Quạt trần', 1)) v(item, qty);
    INSERT INTO [room_furnishings] ([room_id], [item_name], [quantity], [condition_status])
    SELECT r.[room_id], N'Máy lạnh', 1, N'Đã dùng 1 năm'
    FROM [rooms] r JOIN [properties] pr ON pr.[property_id] = r.[property_id] AND pr.[property_name] LIKE N'%(DEMO)'
    WHERE r.[room_id] % 2 = 0;

    -- 100 khách thuê (tên ghép từ họ, đệm, tên)
    SELECT * INTO #ho  FROM (VALUES (0,N'Nguyễn'),(1,N'Trần'),(2,N'Lê'),(3,N'Phạm'),(4,N'Hoàng'),(5,N'Huỳnh'),(6,N'Võ'),(7,N'Đặng'),(8,N'Bùi'),(9,N'Đỗ')) v(i, s);
    SELECT * INTO #dem FROM (VALUES (0,N'Văn'),(1,N'Thị'),(2,N'Minh'),(3,N'Ngọc'),(4,N'Quốc'),(5,N'Thanh')) v(i, s);
    SELECT * INTO #ten FROM (VALUES (0,N'An'),(1,N'Bình'),(2,N'Châu'),(3,N'Dũng'),(4,N'Giang'),(5,N'Hà'),(6,N'Khang'),
        (7,N'Lan'),(8,N'Mai'),(9,N'Nam'),(10,N'Phúc'),(11,N'Quân'),(12,N'Trang'),(13,N'Vy')) v(i, s);

    INSERT INTO [tenants] ([full_name], [phone_number], [email], [dob], [identity_number], [permanent_address],
                           [occupation], [emergency_contact_name], [emergency_contact_phone], [is_verified])
    SELECT ho.s + N' ' + dem.s + N' ' + ten.s,
           '0700' + RIGHT('000000' + CAST(n.n AS VARCHAR(6)), 6),
           CASE WHEN n.n % 3 <> 0 THEN 'khach' + RIGHT('000' + CAST(n.n AS VARCHAR(3)), 3) + '.demo@example.com' END,
           DATEADD(DAY, -(6600 + (n.n * 173) % 4400), '2026-11-17'),
           '0799' + RIGHT('00000000' + CAST(n.n AS VARCHAR(8)), 8),
           CHOOSE(n.n % 5 + 1, N'Long An', N'Tiền Giang', N'Bến Tre', N'Đồng Nai', N'Bình Dương'),
           CHOOSE(n.n % 6 + 1, N'Sinh viên', N'Nhân viên văn phòng', N'Công nhân', N'Kỹ thuật viên', N'Bán hàng', N'Giáo viên'),
           CHOOSE(n.n % 4 + 1, N'Nguyễn Thị Lan', N'Trần Văn Hùng', N'Lê Thị Hoa', N'Phạm Văn Tài'),
           '0903' + RIGHT('000000' + CAST(n.n * 7 AS VARCHAR(6)), 6),
           CASE WHEN n.n % 4 = 0 THEN 0 ELSE 1 END
    FROM #n n
    JOIN #ho  ho  ON ho.i  = (n.n - 1) % 10
    JOIN #dem dem ON dem.i = (n.n * 3) % 6
    JOIN #ten ten ON ten.i = (n.n * 7) % 14
    WHERE n.n <= 100
    ORDER BY n.n;

    -- Xe: 4/5 khách có 1 xe, cứ 4 khách thì thêm xe thứ 2
    INSERT INTO [tenant_vehicles] ([tenant_id], [license_plate], [vehicle_type], [brand], [color])
    SELECT t.[tenant_id], '59DM-' + RIGHT('00000' + CAST(n.n AS VARCHAR(5)), 5),
           CHOOSE(n.n % 4 + 1, 'MOTORBIKE', 'MOTORBIKE', 'ELECTRIC_BIKE', 'BICYCLE'),
           CHOOSE(n.n % 3 + 1, N'Honda', N'Yamaha', N'VinFast'), CHOOSE(n.n % 4 + 1, N'Đen', N'Trắng', N'Xanh', N'Đỏ')
    FROM #n n JOIN [tenants] t ON t.[phone_number] = '0700' + RIGHT('000000' + CAST(n.n AS VARCHAR(6)), 6)
    WHERE n.n <= 100 AND n.n % 5 <> 0;
    INSERT INTO [tenant_vehicles] ([tenant_id], [license_plate], [vehicle_type])
    SELECT t.[tenant_id], '51DM-' + RIGHT('00000' + CAST(n.n AS VARCHAR(5)), 5), 'MOTORBIKE'
    FROM #n n JOIN [tenants] t ON t.[phone_number] = '0700' + RIGHT('000000' + CAST(n.n AS VARCHAR(6)), 6)
    WHERE n.n <= 100 AND n.n % 4 = 0 AND n.n % 5 <> 0;

    -- Đơn giá điện nước chung toàn hệ thống: chỉ thêm nếu DB chưa có bảng giá nào (không đổi giá của bộ cũ)
    IF NOT EXISTS (SELECT 1 FROM [service_rate_plans])
        INSERT INTO [service_rate_plans] ([plan_name], [electric_unit_price], [water_unit_price], [effective_start_date])
        VALUES (N'Bảng giá 2026', 3500, 15000, '2026-01-01');

    INSERT INTO [property_service_rates] ([property_id], [service_name], [charge_type], [unit_price])
    SELECT p.pid, v.name, v.ctype, v.price FROM #prop p
    CROSS APPLY (VALUES (N'Internet', 'FIXED_ROOM', 100000), (N'Gửi xe', 'PER_VEHICLE', 50000), (N'Vệ sinh', 'PER_PERSON', 30000)) v(name, ctype, price);

    INSERT INTO [bank_accounts] ([property_id], [bank_name], [account_number], [account_holder], [is_default], [is_active])
    SELECT p.pid, CHOOSE(p.pid % 3 + 1, N'Vietcombank', N'BIDV', N'MB Bank'),
           '00710' + RIGHT('00000' + CAST(p.pid AS VARCHAR(5)), 5) + '89', N'CHU TRO DEMO', 1, 1
    FROM #prop p;

    -- 6 kỳ ghi chỉ số (06-11/2026) mỗi khu; các tháng trước đã chốt, tháng 11 còn mở
    SELECT * INTO #mon FROM (VALUES
        (0, '2026-06-01', '2026-06-30', N'Tháng 06/2026'), (1, '2026-07-01', '2026-07-31', N'Tháng 07/2026'),
        (2, '2026-08-01', '2026-08-31', N'Tháng 08/2026'), (3, '2026-09-01', '2026-09-30', N'Tháng 09/2026'),
        (4, '2026-10-01', '2026-10-31', N'Tháng 10/2026'), (5, '2026-11-01', '2026-11-30', N'Tháng 11/2026')) v(mi, f, l, nm);

    INSERT INTO [utility_reading_periods] ([property_id], [period_name], [start_date], [end_date], [is_closed])
    SELECT p.pid, m.nm, m.f, m.l, CASE WHEN m.mi < 5 THEN 1 ELSE 0 END FROM #prop p CROSS JOIN #mon m;

    -- Chỉ số từng phòng, cộng dồn theo tháng. Phòng REPAIRING/SUSPENDED không có chỉ số.
    -- Mức dùng 45-114 kWh và 3-9 m3; riêng tháng 11 vài phòng (mỗi 23 phòng 1 phòng) dùng điện vọt lên để có cảnh báo bất thường;
    -- mỗi 17 phòng 1 phòng chưa có chỉ số tháng 11 (cảnh báo thiếu chỉ số).
    SELECT r.[room_id], r.[property_id], m.mi, m.f,
           45 + (r.[room_id] * 7 + m.mi * 11) % 70 + CASE WHEN m.mi = 5 AND r.[room_id] % 23 = 0 THEN 260 ELSE 0 END AS ue,
           3 + (r.[room_id] + m.mi * 2) % 7 AS uw
    INTO #use
    FROM [rooms] r
    JOIN [properties] pr ON pr.[property_id] = r.[property_id] AND pr.[property_name] LIKE N'%(DEMO)'
    CROSS JOIN #mon m
    WHERE r.[status] = 'VACANT' AND NOT (m.mi = 5 AND r.[room_id] % 17 = 0);

    INSERT INTO [meter_readings] ([period_id], [room_id], [old_electric], [new_electric], [old_water], [new_water],
                                  [reading_date], [recorded_by_user_id])
    SELECT per.[period_id], x.[room_id],
           100 + COALESCE(SUM(x.ue) OVER (PARTITION BY x.[room_id] ORDER BY x.mi ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0),
           100 + SUM(x.ue) OVER (PARTITION BY x.[room_id] ORDER BY x.mi),
           10 + COALESCE(SUM(x.uw) OVER (PARTITION BY x.[room_id] ORDER BY x.mi ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0),
           10 + SUM(x.uw) OVER (PARTITION BY x.[room_id] ORDER BY x.mi),
           CASE WHEN x.mi = 5 THEN '2026-11-15 09:00' ELSE DATEADD(HOUR, 18, CAST(EOMONTH(x.f) AS DATETIME2)) END,
           @u
    FROM #use x
    JOIN [utility_reading_periods] per ON per.[property_id] = x.[property_id] AND per.[start_date] = x.f;

    -- Chi phí vận hành 6 tháng x 3 khu
    INSERT INTO [operating_expenses] ([property_id], [expense_category], [description], [amount], [expense_date], [recorded_by])
    SELECT p.pid, v.cat, v.descr, v.amt + (p.pid * 13 + m.mi * 7) % 10 * 10000, DATEADD(DAY, v.d, m.f), @u
    FROM #prop p CROSS JOIN #mon m
    CROSS APPLY (VALUES
        (N'Điện nước chung', N'Điện hành lang, bơm nước', 450000, 5),
        (N'Vệ sinh', N'Thuê dọn cầu thang và rác', 400000, 4),
        (N'Bảo trì', N'Sửa chữa nhỏ trong tháng', 250000, 11)) v(cat, descr, amt, d)
    WHERE v.cat <> N'Bảo trì' OR m.mi % 2 = 0;
    INSERT INTO [operating_expenses] ([property_id], [expense_category], [description], [amount], [expense_date], [recorded_by])
    SELECT p.pid, N'Thuế', N'Thuế kinh doanh nhà trọ quý', 1200000, DATEADD(DAY, 19, m.f), @u
    FROM #prop p JOIN #mon m ON m.mi IN (2, 5);

    DECLARE @p INT = (SELECT COUNT(*) FROM [properties] WHERE [property_name] LIKE N'%(DEMO)');
    DECLARE @r INT = (SELECT COUNT(*) FROM [rooms] r JOIN [properties] pr ON pr.[property_id] = r.[property_id] WHERE pr.[property_name] LIKE N'%(DEMO)');
    DECLARE @t INT = (SELECT COUNT(*) FROM [tenants] WHERE [phone_number] LIKE '0700%');
    DECLARE @m INT = (SELECT COUNT(*) FROM [meter_readings] m JOIN [rooms] r ON r.[room_id] = m.[room_id] JOIN [properties] pr ON pr.[property_id] = r.[property_id] WHERE pr.[property_name] LIKE N'%(DEMO)');
    PRINT N'Đã nạp DEMO dày: ' + CAST(@p AS NVARCHAR(5)) + N' khu trọ, ' + CAST(@r AS NVARCHAR(5)) + N' phòng, '
        + CAST(@t AS NVARCHAR(5)) + N' khách thuê, ' + CAST(@m AS NVARCHAR(6)) + N' dòng chỉ số.';
    COMMIT;
END
GO
