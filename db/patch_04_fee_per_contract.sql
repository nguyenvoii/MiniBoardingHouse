-- Phòng đổi khách giữa tháng: cho phép mỗi hợp đồng có 1 bảng tính trong cùng kỳ (trước đây 1 bảng tính / phòng / kỳ).
-- Chạy lại nhiều lần an toàn. Chạy 1 lần trên mỗi máy đã có DB cũ, TRƯỚC khi chạy app bản mới.
USE [MiniBoardingHouseDB];
GO
IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'UQ_period_room_calculated'
           AND parent_object_id = OBJECT_ID('dbo.calculated_monthly_fees'))
BEGIN
    ALTER TABLE [dbo].[calculated_monthly_fees] DROP CONSTRAINT [UQ_period_room_calculated];
    PRINT N'Đã bỏ ràng buộc UQ_period_room_calculated.';
END
GO
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'UQ_period_room_contract_calculated'
               AND parent_object_id = OBJECT_ID('dbo.calculated_monthly_fees'))
BEGIN
    ALTER TABLE [dbo].[calculated_monthly_fees] ADD CONSTRAINT [UQ_period_room_contract_calculated]
        UNIQUE ([billing_period_id], [room_id], [contract_id]);
    PRINT N'Đã thêm ràng buộc UQ_period_room_contract_calculated.';
END
ELSE
    PRINT N'Ràng buộc UQ_period_room_contract_calculated đã có, bỏ qua.';
GO
