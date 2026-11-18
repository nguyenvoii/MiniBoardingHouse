-- ============================================================================
-- DỮ LIỆU ẢO THÁNG 12/2026 để thử luồng 5 bước trên giao diện (chạy SAU sample-data.sql).
-- Chỉ nạp ĐẦU VÀO: kỳ ghi chỉ số tháng 12, chỉ số điện nước từng phòng có hợp đồng, vài khoản chi phí.
-- KHÔNG tạo kỳ tính tiền, hóa đơn hay thanh toán: đó là 5 bước bạn tự làm trên giao diện.
-- Chạy lại nhiều lần vẫn an toàn (có kỳ tháng 12 rồi thì bỏ qua).
-- ============================================================================
USE [MiniBoardingHouseDB];
GO
SET QUOTED_IDENTIFIER ON;
GO

DECLARE @p INT = (SELECT TOP 1 [property_id] FROM [properties] ORDER BY [property_id]);
DECLARE @u INT = (SELECT TOP 1 [user_id] FROM [users] WHERE [email] = 'admin@minihouse.local');

IF @p IS NULL OR @u IS NULL
    PRINT N'Chưa có khu trọ hoặc admin, hãy chạy seed.sql và sample-data.sql trước.';
ELSE IF EXISTS (SELECT 1 FROM [utility_reading_periods] WHERE [property_id] = @p AND [start_date] = '2026-12-01')
    PRINT N'Đã có kỳ ghi chỉ số tháng 12, bỏ qua.';
ELSE
BEGIN
    DECLARE @nov INT = (SELECT [period_id] FROM [utility_reading_periods] WHERE [property_id] = @p AND [start_date] = '2026-11-01');

    INSERT INTO [utility_reading_periods] ([property_id], [period_name], [start_date], [end_date])
    VALUES (@p, N'Tháng 12/2026', '2026-12-01', '2026-12-31');
    DECLARE @dec INT = SCOPE_IDENTITY();

    -- Chỉ số tháng 12 = chỉ số cuối tháng 11 + mức dùng. Chỉ các phòng có hợp đồng còn hiệu lực trong tháng 12.
    -- P201 cố ý dùng điện rất cao (600 kWh) để thấy cảnh báo "tiền cao bất thường" ở bước duyệt.
    INSERT INTO [meter_readings] ([period_id], [room_id], [old_electric], [new_electric], [old_water], [new_water], [recorded_by_user_id])
    SELECT @dec, m.[room_id], m.[new_electric],
           m.[new_electric] + CASE WHEN r.[room_code] = 'P201' THEN 600 ELSE 62 + m.[room_id] * 5 END,
           m.[new_water], m.[new_water] + 4 + (m.[room_id] % 3), @u
    FROM [meter_readings] m
    JOIN [rooms] r ON r.[room_id] = m.[room_id]
    WHERE m.[period_id] = @nov
      AND EXISTS (SELECT 1 FROM [rental_contracts] c WHERE c.[room_id] = m.[room_id]
                  AND c.[status] IN ('ACTIVE', 'EXPIRING_SOON') AND c.[end_date] >= '2026-12-01');

    -- Chi phí tháng 12: khoản "Điện nước chung" được chia cho các phòng theo số người ở khi tính tiền.
    INSERT INTO [operating_expenses] ([property_id], [expense_category], [description], [amount], [expense_date], [recorded_by])
    VALUES
    (@p, N'Điện nước chung', N'Hóa đơn điện hành lang và bơm nước', 480000, '2026-12-06', @u),
    (@p, N'Bảo trì', N'Thay khóa cửa chính', 350000, '2026-12-09', @u),
    (@p, N'Vệ sinh', N'Thuê dọn cầu thang tháng 12', 400000, '2026-12-05', @u);

    PRINT N'Đã nạp chỉ số và chi phí tháng 12/2026.';
END
GO

-- Nhóm sự cố (7 mã khớp giao diện người thuê me.ts) và nhóm thiết bị; chạy lại an toàn. Xem thêm db/patch_05_incident_categories.sql
INSERT INTO [incident_categories] ([category_code], [category_name], [category_type], [description], [is_active])
SELECT v.code, v.name, v.type, v.descr, 1
FROM (VALUES
    ('ELECTRIC_ISSUE',  N'Điện: mất điện, chập, nhảy aptomat', 'INCIDENT',  N'Sự cố về điện trong phòng'),
    ('WATER_ISSUE',     N'Nước: yếu, rò rỉ, tắc',              'INCIDENT',  N'Sự cố về nước trong phòng'),
    ('AIR_CONDITIONER', N'Máy lạnh',                           'INCIDENT',  N'Máy lạnh không mát, chảy nước, kêu to'),
    ('DOOR_LOCK',       N'Cửa, ổ khóa',                        'INCIDENT',  N'Hỏng cửa, kẹt khóa, mất chìa'),
    ('INTERNET',        N'Mạng Internet',                      'INCIDENT',  N'Mất mạng, wifi yếu'),
    ('METER_CHECK',     N'Nghi ngờ công tơ tính sai',          'INCIDENT',  N'Đề nghị kiểm tra đồng hồ điện, nước'),
    ('OTHER_INCIDENT',  N'Sự cố khác',                         'INCIDENT',  N'Các sự cố không thuộc nhóm trên'),
    ('EQ_AIR_CONDITIONER', N'Máy lạnh (thiết bị)',             'EQUIPMENT', N'Lắp thêm hoặc dùng thêm máy lạnh'),
    ('EQ_FRIDGE',       N'Tủ lạnh',                            'EQUIPMENT', N'Lắp thêm hoặc dùng thêm tủ lạnh'),
    ('EQ_WASHER',       N'Máy giặt',                           'EQUIPMENT', N'Lắp thêm hoặc dùng thêm máy giặt'),
    ('EQ_WATER_HEATER', N'Bình nước nóng',                     'EQUIPMENT', N'Lắp thêm bình nước nóng'),
    ('EQ_OTHER',        N'Thiết bị khác',                      'EQUIPMENT', N'Thiết bị khác cần tính phụ phí')
) AS v(code, name, type, descr)
WHERE NOT EXISTS (SELECT 1 FROM [incident_categories] c WHERE c.[category_code] = v.code);
GO
