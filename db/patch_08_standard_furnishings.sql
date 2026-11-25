-- Chạy trong cơ sở dữ liệu Stayly.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.standard_furnishings', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.standard_furnishings (
        standard_id INT IDENTITY(1,1) PRIMARY KEY,
        item_name NVARCHAR(100) NOT NULL,
        quantity INT NOT NULL CHECK (quantity > 0),
        condition_status NVARCHAR(100) NOT NULL,
        notes NVARCHAR(500) NULL
    );
    INSERT INTO dbo.standard_furnishings (item_name, quantity, condition_status, notes) VALUES
        (N'Máy lạnh Inverter 1.5 HP', 1, N'Mới / Hoạt động tốt', N'Kèm remote điều khiển chính hãng'),
        (N'Giường ngủ gỗ tự nhiên 1m6 x 2m', 1, N'Mới / Tốt', N'Khung giường chắc chắn, vạt phản phẳng'),
        (N'Tủ quần áo gỗ 2 cánh', 1, N'Mới / Tốt', N'Có ngăn kéo và chìa khóa tủ'),
        (N'Bình nóng lạnh Ariston 20L', 1, N'Mới / Hoạt động tốt', N'Có chống giật ELCB an toàn'),
        (N'Bàn ghế làm việc / học tập', 1, N'Mới / Tốt', N'Bàn gỗ chân sắt + ghế xoay êm'),
        (N'Tủ lạnh mini 90L', 1, N'Mới / Hoạt động tốt', N'Tiết kiệm điện năng');
END;
COMMIT;
