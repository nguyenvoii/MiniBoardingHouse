-- ============================================================================
-- DỮ LIỆU ẢO BỔ SUNG cho các màn còn trắng sau khi giao diện có thêm chức năng
-- (chạy SAU sample-data.sql, có thể chạy lại nhiều lần: phần nào có rồi thì bỏ qua).
-- Nạp: loại phòng (+ gán cho phòng), mẫu hợp đồng, mẫu thông báo, phụ thu/giảm trừ,
--      yêu cầu thiết bị + phụ phí, đặt phòng, sự cố sửa chữa (+ nhật ký xử lý),
--      thông báo trên chuông, lịch sử nhắc nợ, người ở cùng.
-- KHÔNG đụng hợp đồng, hóa đơn, thanh toán, chỉ số điện nước; không đụng phòng 101 khu Bình An.
-- Không nạp ảnh (ảnh phòng, ảnh công tơ, ảnh CCCD cần file thật, hãy tải lên bằng giao diện).
-- ============================================================================
USE [MiniBoardingHouseDB];
GO
SET QUOTED_IDENTIFIER ON;
GO

DECLARE @p1 INT = (SELECT TOP 1 [property_id] FROM [properties] ORDER BY [property_id]);
DECLARE @pBA INT = (SELECT TOP 1 [property_id] FROM [properties] WHERE [property_name] = N'Khu trọ Bình An');
DECLARE @admin INT = (SELECT TOP 1 [user_id] FROM [users] WHERE [email] = 'admin@minihouse.local');
DECLARE @mgr INT = (SELECT TOP 1 [user_id] FROM [users] WHERE [email] = 'quanly@minihouse.local');
DECLARE @now DATETIME2 = SYSDATETIME();

IF @p1 IS NULL OR @admin IS NULL
BEGIN
    PRINT N'Chưa có khu trọ hoặc admin, hãy chạy seed.sql và sample-data.sql trước.';
    RETURN;
END

-- Phòng của khu 1 kèm người thuê đang ở (hợp đồng ACTIVE)
SELECT r.[room_id], r.[room_code], c.[tenant_id]
INTO #rt
FROM [rooms] r
LEFT JOIN [rental_contracts] c ON c.[room_id] = r.[room_id] AND c.[status] = 'ACTIVE'
WHERE r.[property_id] = @p1;

-- ---------------------------------------------------------------- 1. Loại phòng + gán cho phòng
INSERT INTO [room_types] ([property_id], [type_name], [description], [default_rent_price], [default_deposit_amount])
SELECT @p1, v.n, v.d, v.rent, v.dep FROM (VALUES
    (N'Phòng đơn',        N'Khoảng 20 m², 1 người, có máy lạnh',               2800000, 2800000),
    (N'Phòng đôi',        N'Khoảng 25 m², ở được 2 người, có ban công',        3100000, 3100000),
    (N'Studio có bếp',    N'Khoảng 30 m², có bếp riêng và tủ lạnh',            3400000, 3400000)
) v(n, d, rent, dep)
WHERE NOT EXISTS (SELECT 1 FROM [room_types] t WHERE t.[property_id] = @p1 AND t.[type_name] = v.n);

UPDATE r SET r.[room_type_id] = t.[room_type_id]
FROM [rooms] r
JOIN [room_types] t ON t.[property_id] = r.[property_id]
 AND t.[type_name] = CASE WHEN r.[base_rent_price] <= 2900000 THEN N'Phòng đơn' WHEN r.[base_rent_price] <= 3200000 THEN N'Phòng đôi' ELSE N'Studio có bếp' END
WHERE r.[property_id] = @p1 AND r.[room_type_id] IS NULL;

IF @pBA IS NOT NULL
BEGIN
    INSERT INTO [room_types] ([property_id], [type_name], [description], [default_rent_price], [default_deposit_amount])
    SELECT @pBA, v.n, v.d, v.rent, v.dep FROM (VALUES
        (N'Phòng thường',     N'Phòng tiêu chuẩn, cửa sổ thoáng',   2700000, 2700000),
        (N'Phòng ban công',   N'Có ban công riêng phơi đồ',          3100000, 3100000)
    ) v(n, d, rent, dep)
    WHERE NOT EXISTS (SELECT 1 FROM [room_types] t WHERE t.[property_id] = @pBA AND t.[type_name] = v.n);

    UPDATE r SET r.[room_type_id] = t.[room_type_id]
    FROM [rooms] r
    JOIN [room_types] t ON t.[property_id] = r.[property_id]
     AND t.[type_name] = CASE WHEN r.[base_rent_price] <= 2800000 THEN N'Phòng thường' ELSE N'Phòng ban công' END
    WHERE r.[property_id] = @pBA AND r.[room_type_id] IS NULL;
END

-- ---------------------------------------------------------------- 2. Mẫu hợp đồng
IF NOT EXISTS (SELECT 1 FROM [contract_templates])
BEGIN
    INSERT INTO [contract_templates] ([template_name], [terms_content], [is_default]) VALUES
    (N'Hợp đồng thuê phòng tiêu chuẩn',
     N'ĐIỀU 1. Bên A (chủ trọ) cho bên B (người thuê) thuê phòng đúng như thông tin ghi trong hợp đồng này.' + NCHAR(10) +
     N'ĐIỀU 2. Giá thuê, tiền cọc và kỳ thanh toán theo thỏa thuận ở phần đầu hợp đồng. Bên B thanh toán trước ngày 05 hằng tháng.' + NCHAR(10) +
     N'ĐIỀU 3. Tiền điện, nước tính theo chỉ số công tơ và đơn giá đang áp dụng; dịch vụ khác tính theo bảng dịch vụ của khu trọ.' + NCHAR(10) +
     N'ĐIỀU 4. Bên B giữ gìn tài sản, không tự ý sửa chữa kết cấu phòng, không gây ồn sau 22 giờ, không nuôi thú nếu chưa được đồng ý.' + NCHAR(10) +
     N'ĐIỀU 5. Chấm dứt hợp đồng phải báo trước ít nhất 30 ngày. Tiền cọc hoàn lại sau khi trừ các khoản còn nợ và hư hỏng (nếu có).', 1),
    (N'Hợp đồng ngắn hạn (dưới 6 tháng)',
     N'ĐIỀU 1. Hợp đồng có thời hạn ngắn, hết hạn thì tự chấm dứt nếu hai bên không gia hạn bằng văn bản.' + NCHAR(10) +
     N'ĐIỀU 2. Tiền cọc bằng 01 tháng tiền phòng, hoàn lại khi trả phòng đúng hạn và không còn nợ.' + NCHAR(10) +
     N'ĐIỀU 3. Không được cho thuê lại hoặc chuyển nhượng phòng cho người khác.' + NCHAR(10) +
     N'ĐIỀU 4. Điện nước và dịch vụ tính như hợp đồng tiêu chuẩn.', 0),
    (N'Hợp đồng nhiều người ở chung',
     N'ĐIỀU 1. Người đứng tên chịu trách nhiệm thanh toán toàn bộ tiền phòng và dịch vụ cho cả phòng.' + NCHAR(10) +
     N'ĐIỀU 2. Người ở cùng phải được khai báo với chủ trọ; số người không vượt quá số người tối đa của phòng.' + NCHAR(10) +
     N'ĐIỀU 3. Dịch vụ tính theo đầu người sẽ tăng giảm theo số người ở thực tế.' + NCHAR(10) +
     N'ĐIỀU 4. Khi có người ở cùng rời phòng, người đứng tên phải báo để chủ trọ cập nhật.', 0);
END

-- ---------------------------------------------------------------- 3. Mẫu thông báo ({{tên}} sẽ được thay khi gửi)
INSERT INTO [notification_templates] ([template_code], [channel], [subject], [content], [is_active])
SELECT v.c, v.ch, v.s, v.t, 1 FROM (VALUES
    ('INVOICE_ISSUED',    'EMAIL',      N'Hóa đơn {{invoice}} đã phát hành',
        N'Chào bạn, hóa đơn {{invoice}} có tổng {{amount}} đ, hạn thanh toán {{dueDate}}. Bạn có thể thanh toán bằng tiền mặt hoặc quét mã QR. Cảm ơn bạn.'),
    ('INVOICE_ADJUSTED',  'SYSTEM_APP', N'Hóa đơn của bạn đã được điều chỉnh',
        N'Hóa đơn {{invoice}} vừa được điều chỉnh. Ghi chú: {{note}}. Vui lòng kiểm tra lại số tiền cần thanh toán.'),
    ('DEBT_REMINDER_1',   'SYSTEM_APP', N'Nhắc thanh toán phòng {{room}}',
        N'Phòng {{room}} còn khoản chưa thanh toán {{amount}}, đã quá hạn {{overdueDays}} ngày. Bạn vui lòng thanh toán sớm nhé.'),
    ('DEBT_REMINDER_2',   'SYSTEM_APP', N'Nhắc thanh toán lần 2, phòng {{room}}',
        N'Khoản nợ {{amount}} của phòng {{room}} đã quá hạn {{overdueDays}} ngày. Mong bạn thanh toán trong tuần này để tránh phát sinh phí chậm trả.'),
    ('DEBT_FINAL_WARNING','SYSTEM_APP', N'Cảnh báo cuối cùng, phòng {{room}}',
        N'Phòng {{room}} nợ {{amount}}, quá hạn {{overdueDays}} ngày. Nếu không thanh toán, chủ trọ có thể xem xét chấm dứt hợp đồng theo điều khoản đã ký.'),
    ('CONTRACT_EXPIRING', 'SYSTEM_APP', N'Hợp đồng {{contract}} sắp hết hạn',
        N'Hợp đồng {{contract}} phòng {{room}} sẽ hết hạn ngày {{endDate}}. Nếu muốn gia hạn, bạn vui lòng liên hệ chủ trọ.'),
    ('CONTRACT_EXPIRED',  'SYSTEM_APP', N'Hợp đồng {{contract}} đã hết hạn',
        N'Hợp đồng {{contract}} phòng {{room}} đã hết hạn ngày {{endDate}}. Vui lòng liên hệ chủ trọ để gia hạn hoặc làm thủ tục trả phòng.')
) v(c, ch, s, t)
WHERE NOT EXISTS (SELECT 1 FROM [notification_templates] x WHERE x.[template_code] = v.c AND x.[channel] = v.ch);

-- ---------------------------------------------------------------- 4. Phụ thu / giảm trừ từng phòng
INSERT INTO [room_custom_rates] ([room_id], [rate_name], [custom_price], [notes])
SELECT rt.[room_id], v.n, v.p, v.note FROM (VALUES
    ('P101', N'Máy lạnh riêng',            150000, N'Khách tự lắp thêm máy lạnh thứ hai'),
    ('P102', N'Khuyến mãi khách lâu năm', -100000, N'Giảm cho khách thuê trên 12 tháng'),
    ('P201', N'Nuôi mèo',                  100000, N'Phụ thu vệ sinh khi nuôi thú cưng'),
    ('P302', N'Hỗ trợ giá tháng 12',       -50000, N'Chủ trọ hỗ trợ theo thỏa thuận miệng')
) v(code, n, p, note)
JOIN #rt rt ON rt.[room_code] = v.code
WHERE NOT EXISTS (SELECT 1 FROM [room_custom_rates] x WHERE x.[room_id] = rt.[room_id] AND x.[rate_name] = v.n);

-- ---------------------------------------------------------------- 5. Yêu cầu thiết bị + phụ phí
INSERT INTO [room_equipment_requests] ([room_id], [tenant_id], [category_id], [equipment_name], [request_type], [estimated_monthly_fee], [status], [reviewed_by], [note], [created_at])
SELECT rt.[room_id], rt.[tenant_id],
       (SELECT [category_id] FROM [incident_categories] WHERE [category_code] = v.cat),
       v.n, v.typ, v.fee, v.st, CASE WHEN v.st = 'PENDING' THEN NULL ELSE @admin END, v.note, DATEADD(DAY, -v.ago, @now)
FROM (VALUES
    ('P101', 'EQ_WASHER',          N'Máy giặt mini',        'INSTALL',    120000, 'PENDING',   N'Khách xin để máy giặt trong phòng',          2),
    ('P102', 'EQ_FRIDGE',          N'Tủ lạnh 90 lít',       'INSTALL',     80000, 'APPROVED',  N'Đồng ý, chờ khách mang tủ lạnh tới',         6),
    ('P201', 'EQ_WATER_HEATER',    N'Bình nước nóng 20 lít','INSTALL',     60000, 'INSTALLED', N'Đã lắp xong, tính phụ phí từ tháng 10',     35),
    ('P301', 'EQ_AIR_CONDITIONER', N'Máy lạnh thêm',        'EXTRA_USAGE', 200000, 'REJECTED',  N'Quá tải điện của dãy phòng tầng 3',          12),
    ('P304', 'EQ_FRIDGE',          N'Tủ lạnh mini',         'INSTALL',     70000, 'INSTALLED', N'Đã lắp, tính phụ phí từ tháng 11',          20)
) v(code, cat, n, typ, fee, st, note, ago)
JOIN #rt rt ON rt.[room_code] = v.code
WHERE rt.[tenant_id] IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM [room_equipment_requests] x WHERE x.[room_id] = rt.[room_id] AND x.[equipment_name] = v.n);

INSERT INTO [room_addon_fees] ([room_id], [equipment_request_id], [fee_name], [amount], [billing_cycle_months], [start_date], [end_date], [is_active])
SELECT q.[room_id], q.[equipment_request_id], N'Phụ phí ' + LOWER(LEFT(q.[equipment_name], 1)) + SUBSTRING(q.[equipment_name], 2, 100),
       q.[estimated_monthly_fee], 1, CAST(q.[created_at] AS DATE), NULL, 1
FROM [room_equipment_requests] q
WHERE q.[status] = 'INSTALLED'
  AND NOT EXISTS (SELECT 1 FROM [room_addon_fees] f WHERE f.[equipment_request_id] = q.[equipment_request_id]);

-- ---------------------------------------------------------------- 6. Đặt phòng (khách chưa có hồ sơ, đặt phòng trống ở khu Bình An)
IF @pBA IS NOT NULL AND NOT EXISTS (SELECT 1 FROM [booking_requests])
BEGIN
    INSERT INTO [booking_requests] ([room_id], [tenant_id], [expected_start_date], [occupants_count], [deposit_paid], [status], [rejection_reason], [created_at], [guest_name], [guest_phone])
    SELECT r.[room_id], NULL, v.start_d, v.occ, v.dep, v.st, v.reason, DATEADD(DAY, -v.ago, @now), v.g, v.ph
    FROM (VALUES
        ('203', '2026-12-05', 1,       0, 'PENDING',   NULL,                                   1, N'Nguyễn Hữu Tài', '0907000111'),
        ('204', '2026-12-10', 2, 1000000, 'PENDING',   NULL,                                   2, N'Lý Thanh Hà',    '0907000222'),
        ('203', '2026-11-20', 1,       0, 'REJECTED',  N'Phòng đã có người đặt trước đó',      8, N'Trương Quốc Việt','0907000333'),
        ('204', '2026-11-25', 1,       0, 'CANCELLED', NULL,                                  10, N'Đoàn Mỹ Linh',   '0907000444')
    ) v(code, start_d, occ, dep, st, reason, ago, g, ph)
    JOIN [rooms] r ON r.[property_id] = @pBA AND r.[room_code] = v.code;
END

-- ---------------------------------------------------------------- 7. Sự cố sửa chữa + nhật ký xử lý
IF (SELECT COUNT(*) FROM [maintenance_requests]) < 3
BEGIN
    INSERT INTO [maintenance_requests] ([room_id], [tenant_id], [category], [description], [priority], [status], [created_at])
    SELECT rt.[room_id], rt.[tenant_id], v.cat, v.d, v.pr, v.st, DATEADD(DAY, -v.ago, @now)
    FROM (VALUES
        ('P102', 'WATER_ISSUE',     N'Vòi nước bồn rửa chảy yếu, nghi bị nghẹt lọc',          'NORMAL', 'OPEN',        1),
        ('P303', 'METER_CHECK',     N'Nghi ngờ đồng hồ nước ghi nhiều hơn thực tế dùng',       'NORMAL', 'OPEN',        2),
        ('P201', 'AIR_CONDITIONER', N'Máy lạnh chảy nước ra sàn, ướt sàn gỗ',                  'URGENT', 'ASSIGNED',    3),
        ('P301', 'DOOR_LOCK',       N'Ổ khóa cửa chính khó mở, kẹt chìa',                      'NORMAL', 'IN_PROGRESS', 5),
        ('P302', 'INTERNET',        N'Mạng wifi chập chờn vào buổi tối',                       'LOW',    'RESOLVED',    9),
        ('P304', 'ELECTRIC_ISSUE',  N'Ổ cắm phòng ngủ bị cháy xém, có mùi khét',               'URGENT', 'CLOSED',     14),
        ('P103', 'OTHER_INCIDENT',  N'Bóng đèn hành lang trước phòng bị cháy',                 'LOW',    'CLOSED',     20)
    ) v(code, cat, d, pr, st, ago)
    JOIN #rt rt ON rt.[room_code] = v.code
    WHERE rt.[tenant_id] IS NOT NULL;

    INSERT INTO [maintenance_logs] ([request_id], [assigned_staff_id], [repair_cost], [tenant_rating], [resolution_notes], [completed_at])
    SELECT m.[request_id], COALESCE(@mgr, @admin), v.cost, v.rate, v.notes,
           CASE WHEN v.done IS NULL THEN NULL ELSE DATEADD(DAY, -v.done, @now) END
    FROM (VALUES
        (N'Máy lạnh chảy nước ra sàn, ướt sàn gỗ',         0,      NULL, NULL,                                          NULL),
        (N'Ổ khóa cửa chính khó mở, kẹt chìa',             0,      NULL, N'Đã gọi thợ khóa, hẹn sang vào chiều mai',    NULL),
        (N'Mạng wifi chập chờn vào buổi tối',              0,      4,    N'Đã đổi bộ phát wifi sang kênh ít nhiễu hơn', 7),
        (N'Ổ cắm phòng ngủ bị cháy xém, có mùi khét',      180000, 5,    N'Thay ổ cắm và đo lại dây, đã an toàn',       13),
        (N'Bóng đèn hành lang trước phòng bị cháy',        45000,  3,    N'Đã thay bóng LED mới',                       19)
    ) v(d, cost, rate, notes, done)
    JOIN [maintenance_requests] m ON m.[description] = v.d;
END

-- ---------------------------------------------------------------- 8. Thông báo trên chuông
IF NOT EXISTS (SELECT 1 FROM [user_notifications])
BEGIN
    INSERT INTO [user_notifications] ([user_id], [title], [content], [reference_type], [reference_id], [is_read], [created_at])
    SELECT u.[user_id], v.t, v.c, v.rt, v.rid, v.rd, DATEADD(HOUR, -v.hrs, @now)
    FROM (VALUES
        ('0901234567', N'Hóa đơn tháng 11 đã phát hành',        N'Hóa đơn tháng 11 của phòng P101 đã sẵn sàng, bạn có thể xem và thanh toán trong mục Hóa đơn.', 'DEBT', (SELECT [debt_id] FROM [tenant_debts] WHERE [tenant_id] = 1), 1, 200),
        ('0901234567', N'Nhắc thanh toán phòng P101',           N'Phòng P101 còn khoản chưa thanh toán, đã quá hạn 15 ngày. Bạn vui lòng thanh toán sớm nhé.',     'DEBT', (SELECT [debt_id] FROM [tenant_debts] WHERE [tenant_id] = 1), 0, 30),
        ('0901234567', N'Đã tiếp nhận yêu cầu sửa chữa',        N'Yêu cầu sự cố điện của phòng P101 đã được ghi nhận, chủ trọ sẽ liên hệ bạn.',                     'MAINTENANCE', NULL, 1, 240),
        ('0912345678', N'Nhắc thanh toán phòng P102',           N'Phòng P102 còn khoản chưa thanh toán, đã quá hạn 15 ngày. Bạn vui lòng thanh toán sớm nhé.',     'DEBT', (SELECT [debt_id] FROM [tenant_debts] WHERE [tenant_id] = 2), 0, 28),
        ('0988901234', N'Sự cố đã được xử lý xong',             N'Ổ cắm phòng ngủ P304 đã được thay mới. Bạn đánh giá giúp mình chất lượng xử lý nhé.',            'MAINTENANCE', NULL, 1, 310),
        ('0700000001', N'Yêu cầu kiểm tra công tơ đã được tiếp nhận', N'Chủ trọ sẽ kiểm tra đồng hồ nước phòng P303 trong thời gian sớm nhất.',                    'MAINTENANCE', NULL, 0, 40),
        ('0905000102', N'Hóa đơn tháng 11 đã phát hành',        N'Hóa đơn tháng 11 của phòng 102 đã sẵn sàng, hạn thanh toán trong tháng này.',                     'DEBT', (SELECT [debt_id] FROM [tenant_debts] WHERE [tenant_id] = 109), 0, 20)
    ) v(phone, t, c, rt, rid, rd, hrs)
    JOIN [users] u ON u.[phone_number] = v.phone;

    -- Thông báo cho nhân viên (chủ trọ + quản lý)
    INSERT INTO [user_notifications] ([user_id], [title], [content], [reference_type], [reference_id], [is_read], [created_at])
    SELECT u.[user_id], v.t, v.c, v.rt, NULL, v.rd, DATEADD(HOUR, -v.hrs, @now)
    FROM (VALUES
        (N'Có hóa đơn quá hạn',                   N'Phòng P101, P102 và 201 (khu Bình An) đang quá hạn 15 ngày, nên nhắc nợ trong mục Công nợ.', 'DEBT',        0, 12),
        (N'Yêu cầu sửa chữa mới: P102',           N'Vòi nước bồn rửa chảy yếu, chưa có người nhận xử lý.',                             'MAINTENANCE', 0, 24),
        (N'Yêu cầu thiết bị chờ duyệt: P101',     N'Khách xin để máy giặt mini trong phòng, phụ phí dự kiến 120.000 đ/tháng.',          'EQUIPMENT',   0, 48),
        (N'Có 2 yêu cầu đặt phòng mới',           N'Khu Bình An có 2 yêu cầu đặt phòng đang chờ duyệt.',                                'BOOKING',     1, 70)
    ) v(t, c, rt, rd, hrs)
    JOIN [users] u ON u.[user_id] IN (@admin, @mgr);
END

-- ---------------------------------------------------------------- 9. Lịch sử nhắc nợ
IF NOT EXISTS (SELECT 1 FROM [debt_reminders])
BEGIN
    INSERT INTO [debt_reminders] ([debt_id], [reminder_level], [message_content], [sent_at])
    SELECT d.[debt_id], v.lvl, v.msg, DATEADD(DAY, -v.ago, @now)
    FROM (VALUES
        (1, 'FIRST_REMINDER',  N'Phòng P101 còn khoản chưa thanh toán, đã quá hạn. Bạn vui lòng thanh toán sớm nhé.',                  10),
        (1, 'SECOND_REMINDER', N'Khoản nợ phòng P101 đã quá hạn 15 ngày. Mong bạn thanh toán trong tuần này để tránh phát sinh phí.',   3),
        (2, 'FIRST_REMINDER',  N'Phòng P102 còn khoản chưa thanh toán, đã quá hạn. Bạn vui lòng thanh toán sớm nhé.',                  10),
        (8, 'FIRST_REMINDER',  N'Phòng 201 còn khoản chưa thanh toán, đã quá hạn. Bạn vui lòng thanh toán sớm nhé.',                   9)
    ) v(did, lvl, msg, ago)
    JOIN [tenant_debts] d ON d.[debt_id] = v.did;
END

-- ---------------------------------------------------------------- 10. Người ở cùng (phòng tối đa 2 người trở lên)
INSERT INTO [room_residents] ([room_id], [tenant_id], [full_name], [phone_number], [relationship_with_primary], [start_date], [is_active])
SELECT rt.[room_id], NULL, v.n, v.ph, v.rel, v.sd, 1
FROM (VALUES
    ('P101', N'Nguyễn Thị Hoa',  '0911000101', N'Vợ',        '2026-03-01'),
    ('P102', N'Trần Văn Hùng',   NULL,         N'Em trai',   '2026-05-15'),
    ('P201', N'Phạm Thị Lệ',     '0911000201', N'Vợ',        '2026-06-01'),
    ('P302', N'Bùi Minh Quân',   NULL,         N'Con',       '2026-07-10')
) v(code, n, ph, rel, sd)
JOIN #rt rt ON rt.[room_code] = v.code
JOIN [rooms] r ON r.[room_id] = rt.[room_id] AND r.[max_occupants] >= 2
WHERE rt.[tenant_id] IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM [room_residents] x WHERE x.[room_id] = rt.[room_id] AND x.[full_name] = v.n);

DROP TABLE #rt;
PRINT N'Đã nạp dữ liệu bổ sung.';
GO
