-- ============================================================================
-- SEED DỮ LIỆU MẪU (chạy SAU db/group-7.sql). Chạy lại nhiều lần vẫn an toàn.
-- Tài khoản mặc định (CHỈ DÙNG DEV/DEMO): admin@minihouse.local / Admin@123
-- ============================================================================
USE [MiniBoardingHouseQA_20261122];
GO
SET QUOTED_IDENTIFIER ON; -- bắt buộc khi bảng có filtered index (sqlcmd mặc định là OFF)
GO

-- Vai trò hệ thống
INSERT INTO [roles] ([role_code], [role_name], [description])
SELECT v.code, v.name, v.descr
FROM (VALUES
    ('LANDLORD',     N'Chủ nhà',            N'Toàn quyền hệ thống'),
    ('MANAGER',      N'Quản lý',            N'Quản lý phòng, khách thuê, hợp đồng, sự cố'),
    ('CASHIER',      N'Thu ngân',           N'Lập hóa đơn, thu tiền, theo dõi công nợ'),
    ('METER_READER', N'Nhân viên ghi số',   N'Ghi chỉ số điện nước'),
    ('TENANT',       N'Người thuê',         N'Xem phòng, hợp đồng, hóa đơn, công nợ của chính mình')
) AS v(code, name, descr)
WHERE NOT EXISTS (SELECT 1 FROM [roles] r WHERE r.[role_code] = v.code);
GO

-- Quyền chi tiết (xem / quản lý theo từng phân hệ)
INSERT INTO [permissions] ([permission_code], [permission_name], [module])
SELECT v.code, v.name, v.module
FROM (VALUES
    ('USER_VIEW',        N'Xem người dùng',            'USER'),
    ('USER_MANAGE',      N'Quản lý người dùng',        'USER'),
    ('PROPERTY_VIEW',    N'Xem khu trọ',               'PROPERTY'),
    ('PROPERTY_MANAGE',  N'Quản lý khu trọ, tầng',     'PROPERTY'),
    ('ROOM_VIEW',        N'Xem phòng',                 'ROOM'),
    ('ROOM_MANAGE',      N'Quản lý phòng',             'ROOM'),
    ('TENANT_VIEW',      N'Xem khách thuê',            'TENANT'),
    ('TENANT_MANAGE',    N'Quản lý khách thuê',        'TENANT'),
    ('CONTRACT_VIEW',    N'Xem hợp đồng',              'CONTRACT'),
    ('CONTRACT_MANAGE',  N'Quản lý hợp đồng',          'CONTRACT'),
    ('METER_VIEW',       N'Xem chỉ số điện nước',      'METER'),
    ('METER_WRITE',      N'Ghi chỉ số điện nước',      'METER'),
    ('RATE_MANAGE',      N'Quản lý đơn giá, phí',      'RATE'),
    ('BILLING_MANAGE',   N'Tính tiền hàng tháng',      'BILLING'),
    ('INVOICE_VIEW',     N'Xem hóa đơn',               'INVOICE'),
    ('INVOICE_MANAGE',   N'Lập, gửi hóa đơn',          'INVOICE'),
    ('PAYMENT_MANAGE',   N'Thu tiền, cấn trừ',         'PAYMENT'),
    ('DEBT_MANAGE',      N'Quản lý công nợ',           'DEBT'),
    ('CHECKOUT_MANAGE',  N'Trả phòng, hoàn cọc',       'CHECKOUT'),
    ('MAINTENANCE_MANAGE', N'Quản lý sự cố sửa chữa',  'MAINTENANCE'),
    ('EXPENSE_MANAGE',   N'Quản lý chi phí vận hành',  'EXPENSE'),
    ('REPORT_VIEW',      N'Xem báo cáo, dashboard',    'REPORT')
) AS v(code, name, module)
WHERE NOT EXISTS (SELECT 1 FROM [permissions] p WHERE p.[permission_code] = v.code);
GO

-- Tài khoản chủ nhà mặc định (mật khẩu Admin@123, băm BCrypt)
INSERT INTO [users] ([email], [password_hash], [full_name], [status])
SELECT 'admin@minihouse.local',
       '$2a$10$Pc6uubpMhGt6uuP/TqMCyulqTZc2GQVvfgNqWPYx7ciLqNtJzs3uy',
       N'Chủ nhà (admin)', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM [users] WHERE [email] = 'admin@minihouse.local');
GO

INSERT INTO [user_roles] ([user_id], [role_id])
SELECT u.[user_id], r.[role_id]
FROM [users] u CROSS JOIN [roles] r
WHERE u.[email] = 'admin@minihouse.local' AND r.[role_code] = 'LANDLORD'
  AND NOT EXISTS (SELECT 1 FROM [user_roles] ur WHERE ur.[user_id] = u.[user_id] AND ur.[role_id] = r.[role_id]);
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
