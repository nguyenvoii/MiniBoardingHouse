-- Nạp sẵn nhóm sự cố (INCIDENT) và nhóm thiết bị (EQUIPMENT). Trước đây không có dòng nào nên nút "Báo sự cố" của người thuê
-- luôn lỗi "Nhóm sự cố ... không tồn tại hoặc đã tắt". 7 mã INCIDENT khớp danh sách trong giao diện người thuê (me.ts).
-- Chạy lại nhiều lần an toàn (chỉ thêm mã chưa có, không sửa mã đã có). Chạy 1 lần trên mỗi máy đã có DB cũ.
USE [MiniBoardingHouseQA_20261122];
GO
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
