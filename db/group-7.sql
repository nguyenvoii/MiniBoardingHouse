-- ============================================================================
-- DATABASE CREATION: WEBSITE QUẢN LÝ NHÀ TRỌ MINI FOR RENT (56 TABLES)
-- Target RDBMS: SQL Server 2016+ / Azure SQL Database
-- ============================================================================

CREATE DATABASE [MiniBoardingHouseDB];
GO

USE [MiniBoardingHouseDB];
GO

-- ============================================================================
-- PHÂN HỆ 1: NGƯỜI DÙNG, PHÂN QUYỀN & NHẬT KÝ THAO TÁC (USER & RBAC) - 6 BẢNG
-- ============================================================================

-- 1. Bảng người dùng (Chủ nhà & Nhân viên)
CREATE TABLE [users] (
    [user_id] INT IDENTITY(1,1) PRIMARY KEY,
    [email] NVARCHAR(255) NULL UNIQUE,
    [phone_number] VARCHAR(20) NULL UNIQUE,
    [password_hash] NVARCHAR(255) NOT NULL,
    [full_name] NVARCHAR(100) NOT NULL,
    [avatar_url] NVARCHAR(500) NULL,
    [status] NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK ([status] IN ('ACTIVE', 'INACTIVE', 'LOCKED')),
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE(),
    [updated_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 2. Mã xác thực OTP Email / SĐT
CREATE TABLE [user_verifications] (
    [verification_id] INT IDENTITY(1,1) PRIMARY KEY,
    [user_id] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]) ON DELETE CASCADE,
    [type] NVARCHAR(20) NOT NULL CHECK ([type] IN ('EMAIL', 'PHONE', 'RECOVERY')),
    [code] VARCHAR(10) NOT NULL,
    [expires_at] DATETIME2 NOT NULL,
    [is_verified] BIT NOT NULL DEFAULT 0
);

-- 3. Vai trò hệ thống (LANDLORD, MANAGER, CASHIER, METER_READER)
CREATE TABLE [roles] (
    [role_id] INT IDENTITY(1,1) PRIMARY KEY,
    [role_code] VARCHAR(50) NOT NULL UNIQUE, 
    [role_name] NVARCHAR(100) NOT NULL,
    [description] NVARCHAR(255) NULL
);

-- 4. Quyền hệ thống chi tiết
CREATE TABLE [permissions] (
    [permission_id] INT IDENTITY(1,1) PRIMARY KEY,
    [permission_code] VARCHAR(100) NOT NULL UNIQUE,
    [permission_name] NVARCHAR(100) NOT NULL,
    [module] VARCHAR(50) NOT NULL
);

-- 5. Gán vai trò cho nhân viên
CREATE TABLE [user_roles] (
    [user_id] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]) ON DELETE CASCADE,
    [role_id] INT NOT NULL FOREIGN KEY REFERENCES [roles]([role_id]) ON DELETE CASCADE,
    [assigned_at] DATETIME2 NOT NULL DEFAULT GETDATE(),
    PRIMARY KEY ([user_id], [role_id])
);

-- 6. Xem lịch sử đăng nhập & Thao tác hệ thống
CREATE TABLE [user_activity_logs] (
    [log_id] INT IDENTITY(1,1) PRIMARY KEY,
    [user_id] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [action_type] VARCHAR(50) NOT NULL, -- LOGIN, LOGOUT, CREATE_INVOICE, UPDATE_METER...
    [description] NVARCHAR(MAX) NULL,
    [ip_address] VARCHAR(45) NULL,
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- ============================================================================
-- PHÂN HỆ 2: QUẢN LÝ KHU TRỌ, TẦNG, PHÒNG & THIẾT BỊ (PROPERTIES) - 5 BẢNG
-- ============================================================================

-- 7. Khu nhà trọ
CREATE TABLE [properties] (
    [property_id] INT IDENTITY(1,1) PRIMARY KEY,
    [landlord_id] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [property_name] NVARCHAR(150) NOT NULL,
    [address] NVARCHAR(255) NOT NULL,
    [total_floors] INT DEFAULT 1,
    [support_phone] VARCHAR(20) NULL,
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 8. Tầng trong khu nhà trọ
CREATE TABLE [property_floors] (
    [floor_id] INT IDENTITY(1,1) PRIMARY KEY,
    [property_id] INT NOT NULL FOREIGN KEY REFERENCES [properties]([property_id]) ON DELETE CASCADE,
    [floor_number] INT NOT NULL,
    [floor_name] NVARCHAR(50) NOT NULL,
    CONSTRAINT [UQ_property_floor] UNIQUE([property_id], [floor_number])
);

-- 9. Danh mục Phòng trọ
CREATE TABLE [rooms] (
    [room_id] INT IDENTITY(1,1) PRIMARY KEY,
    [property_id] INT NOT NULL FOREIGN KEY REFERENCES [properties]([property_id]),
    [floor_id] INT NOT NULL FOREIGN KEY REFERENCES [property_floors]([floor_id]),
    [room_code] VARCHAR(50) NOT NULL, -- e.g., P101, P202
    [area_sqm] DECIMAL(5,2) NOT NULL,
    [max_occupants] INT NOT NULL DEFAULT 2,
    [base_rent_price] DECIMAL(18,2) NOT NULL,
    [deposit_amount] DECIMAL(18,2) NOT NULL,
    [initial_electric_meter] INT NOT NULL DEFAULT 0,
    [initial_water_meter] INT NOT NULL DEFAULT 0,
    [status] NVARCHAR(30) NOT NULL DEFAULT 'VACANT' 
        CHECK ([status] IN ('VACANT', 'RENTED', 'REPAIRING', 'SUSPENDED')),
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE(),
    CONSTRAINT [UQ_property_room_code] UNIQUE([property_id], [room_code])
);

-- 10. Hình ảnh phòng trọ
CREATE TABLE [room_media] (
    [media_id] INT IDENTITY(1,1) PRIMARY KEY,
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]) ON DELETE CASCADE,
    [media_url] NVARCHAR(500) NOT NULL,
    [is_cover] BIT DEFAULT 0
);

-- 11. Ghi nhận trang thiết bị & Nội thất có sẵn trong phòng
CREATE TABLE [room_furnishings] (
    [furnishing_id] INT IDENTITY(1,1) PRIMARY KEY,
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]) ON DELETE CASCADE,
    [item_name] NVARCHAR(100) NOT NULL, -- Máy lạnh, Tủ lạnh, Giường, Bàn ghế...
    [quantity] INT DEFAULT 1,
    [condition_status] NVARCHAR(100) NOT NULL DEFAULT N'Mới / Tốt',
    [notes] NVARCHAR(255) NULL
);

-- ============================================================================
-- PHÂN HỆ 3: HỒ SƠ KHÁCH THUÊ & CƯ DÂN (TENANTS & RESIDENTS) - 4 BẢNG
-- ============================================================================

-- 12. Hồ sơ Khách thuê chính (Đứng tên hợp đồng)
CREATE TABLE [tenants] (
    [tenant_id] INT IDENTITY(1,1) PRIMARY KEY,
    [full_name] NVARCHAR(100) NOT NULL,
    [phone_number] VARCHAR(20) NOT NULL UNIQUE,
    [email] NVARCHAR(255) NULL,
    [dob] DATE NULL,
    [identity_number] VARCHAR(20) NOT NULL UNIQUE, -- CCCD/CMND
    [permanent_address] NVARCHAR(255) NULL,
    [occupation] NVARCHAR(100) NULL,
    [emergency_contact_name] NVARCHAR(100) NULL,
    [emergency_contact_phone] VARCHAR(20) NULL,
    [is_verified] BIT DEFAULT 0,
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 13. Ảnh CCCD mặt trước/sau của khách thuê
CREATE TABLE [tenant_id_cards] (
    [card_id] INT IDENTITY(1,1) PRIMARY KEY,
    [tenant_id] INT NOT NULL UNIQUE FOREIGN KEY REFERENCES [tenants]([tenant_id]) ON DELETE CASCADE,
    [front_image_url] NVARCHAR(500) NOT NULL,
    [back_image_url] NVARCHAR(500) NOT NULL,
    [uploaded_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 14. Danh sách Người ở cùng trong phòng
CREATE TABLE [room_residents] (
    [resident_id] INT IDENTITY(1,1) PRIMARY KEY,
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]),
    [tenant_id] INT NULL FOREIGN KEY REFERENCES [tenants]([tenant_id]), -- Trống nếu là người ở cùng không làm hợp đồng
    [full_name] NVARCHAR(100) NOT NULL,
    [phone_number] VARCHAR(20) NULL,
    [identity_number] VARCHAR(20) NULL,
    [relationship_with_primary] NVARCHAR(50) NULL, -- Bạn bè, Vợ/Chồng, Anh em...
    [start_date] DATE NOT NULL,
    [end_date] DATE NULL,
    [is_active] BIT DEFAULT 1
);

-- 15. Tra cứu Lịch sử cư trú / ở trọ của khách
CREATE TABLE [resident_stay_histories] (
    [history_id] INT IDENTITY(1,1) PRIMARY KEY,
    [tenant_id] INT NOT NULL FOREIGN KEY REFERENCES [tenants]([tenant_id]),
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]),
    [start_date] DATE NOT NULL,
    [end_date] DATE NULL,
    [checkout_reason] NVARCHAR(255) NULL
);

-- ============================================================================
-- PHÂN HỆ 4: TIẾP NHẬN YÊU CẦU & HỢP ĐỒNG THUÊ TRỌ (CONTRACTS) - 5 BẢNG
-- ============================================================================

-- 16. Yêu cầu đặt giữ chỗ / Thuê phòng
CREATE TABLE [booking_requests] (
    [booking_id] INT IDENTITY(1,1) PRIMARY KEY,
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]),
    [tenant_id] INT NOT NULL FOREIGN KEY REFERENCES [tenants]([tenant_id]),
    [expected_start_date] DATE NOT NULL,
    [occupants_count] INT DEFAULT 1,
    [deposit_paid] DECIMAL(18,2) DEFAULT 0,
    [status] NVARCHAR(20) NOT NULL DEFAULT 'PENDING' 
        CHECK ([status] IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    [rejection_reason] NVARCHAR(255) NULL,
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 17. Mẫu điều khoản hợp đồng thuê trọ
CREATE TABLE [contract_templates] (
    [template_id] INT IDENTITY(1,1) PRIMARY KEY,
    [template_name] NVARCHAR(100) NOT NULL,
    [terms_content] NVARCHAR(MAX) NOT NULL,
    [is_default] BIT DEFAULT 0
);

-- 18. Hợp đồng Thuê phòng chính
CREATE TABLE [rental_contracts] (
    [contract_id] INT IDENTITY(1,1) PRIMARY KEY,
    [contract_code] VARCHAR(50) NOT NULL UNIQUE,
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]),
    [tenant_id] INT NOT NULL FOREIGN KEY REFERENCES [tenants]([tenant_id]),
    [template_id] INT NULL FOREIGN KEY REFERENCES [contract_templates]([template_id]),
    [start_date] DATE NOT NULL,
    [end_date] DATE NOT NULL,
    [monthly_rent_price] DECIMAL(18,2) NOT NULL,
    [deposit_amount] DECIMAL(18,2) NOT NULL,
    [payment_cycle_months] INT NOT NULL DEFAULT 1, -- 1 tháng/lần, 3 tháng/lần...
    [payment_due_day] INT NOT NULL DEFAULT 5, -- Ngày hết hạn thanh toán hàng tháng (e.g., ngày 5)
    [late_fee_per_day] DECIMAL(18,2) DEFAULT 0,
    [electric_terms] NVARCHAR(255) NULL,
    [water_terms] NVARCHAR(255) NULL,
    [deposit_refund_conditions] NVARCHAR(MAX) NULL,
    [digital_signature_url] NVARCHAR(500) NULL,
    [pdf_contract_url] NVARCHAR(500) NULL,
    [status] NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE' 
        CHECK ([status] IN ('ACTIVE', 'EXPIRING_SOON', 'EXPIRED', 'TERMINATED')),
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 19. Lịch sử điều chỉnh hợp đồng (Đổi giá, Đổi người...)
CREATE TABLE [contract_adjustments] (
    [adjustment_id] INT IDENTITY(1,1) PRIMARY KEY,
    [contract_id] INT NOT NULL FOREIGN KEY REFERENCES [rental_contracts]([contract_id]) ON DELETE CASCADE,
    [adjustment_type] VARCHAR(50) NOT NULL, -- PRICE_CHANGE, TENANT_CHANGE, EXTENSION
    [old_value] NVARCHAR(255) NULL,
    [new_value] NVARCHAR(255) NULL,
    [adjusted_by] INT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [adjusted_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 20. Thông báo hợp đồng sắp hết hạn
CREATE TABLE [contract_notifications] (
    [notification_id] INT IDENTITY(1,1) PRIMARY KEY,
    [contract_id] INT NOT NULL FOREIGN KEY REFERENCES [rental_contracts]([contract_id]) ON DELETE CASCADE,
    [notification_type] VARCHAR(50) NOT NULL, -- EXPIRING_WARNING, EXPIRED_NOTICE
    [sent_at] DATETIME2 NOT NULL DEFAULT GETDATE(),
    [is_read] BIT DEFAULT 0
);

-- ============================================================================
-- PHÂN HỆ 5: CHỈ SỐ ĐIỆN NƯỚC & CẢNH BÁO TIÊU THỤ (UTILITY METERS) - 5 BẢNG
-- ============================================================================

-- 21. Kỳ ghi nhận chỉ số điện nước hàng tháng
CREATE TABLE [utility_reading_periods] (
    [period_id] INT IDENTITY(1,1) PRIMARY KEY,
    [property_id] INT NOT NULL FOREIGN KEY REFERENCES [properties]([property_id]),
    [period_name] NVARCHAR(50) NOT NULL, -- e.g., Tháng 03/2026
    [start_date] DATE NOT NULL,
    [end_date] DATE NOT NULL,
    [is_closed] BIT DEFAULT 0
);

-- 22. Ghi nhận chỉ số Điện & Nước từng phòng
CREATE TABLE [meter_readings] (
    [reading_id] INT IDENTITY(1,1) PRIMARY KEY,
    [period_id] INT NOT NULL FOREIGN KEY REFERENCES [utility_reading_periods]([period_id]),
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]),
    [old_electric] INT NOT NULL,
    [new_electric] INT NOT NULL,
    [used_electric] AS ([new_electric] - [old_electric]),
    [old_water] INT NOT NULL,
    [new_water] INT NOT NULL,
    [used_water] AS ([new_water] - [old_water]),
    [reading_date] DATETIME2 NOT NULL DEFAULT GETDATE(),
    [recorded_by_user_id] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [is_verified] BIT DEFAULT 1,
    CONSTRAINT [UQ_period_room_reading] UNIQUE([period_id], [room_id])
);

-- 23. Ảnh chụp công tơ điện & đồng hồ nước minh bạch
CREATE TABLE [meter_photos] (
    [photo_id] INT IDENTITY(1,1) PRIMARY KEY,
    [reading_id] INT NOT NULL FOREIGN KEY REFERENCES [meter_readings]([reading_id]) ON DELETE CASCADE,
    [meter_type] VARCHAR(10) NOT NULL CHECK ([meter_type] IN ('ELECTRIC', 'WATER')),
    [photo_url] NVARCHAR(500) NOT NULL
);

-- 24. Nhật ký điều chỉnh chỉ số điện nước
CREATE TABLE [meter_adjustment_logs] (
    [adj_id] INT IDENTITY(1,1) PRIMARY KEY,
    [reading_id] INT NOT NULL FOREIGN KEY REFERENCES [meter_readings]([reading_id]) ON DELETE CASCADE,
    [adjusted_field] VARCHAR(30) NOT NULL, -- NEW_ELECTRIC, NEW_WATER
    [old_val] INT NOT NULL,
    [new_val] INT NOT NULL,
    [reason] NVARCHAR(255) NOT NULL,
    [adjusted_by] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [adjusted_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 25. Cảnh báo chỉ số tiêu thụ bất thường (Tăng đột biến)
CREATE TABLE [meter_anomaly_alerts] (
    [alert_id] INT IDENTITY(1,1) PRIMARY KEY,
    [reading_id] INT NOT NULL FOREIGN KEY REFERENCES [meter_readings]([reading_id]) ON DELETE CASCADE,
    [utility_type] VARCHAR(10) NOT NULL CHECK ([utility_type] IN ('ELECTRIC', 'WATER')),
    [increase_percentage] DECIMAL(5,2) NOT NULL,
    [alert_message] NVARCHAR(255) NOT NULL,
    [is_resolved] BIT DEFAULT 0
);

-- ============================================================================
-- PHÂN HỆ 6: ĐƠN GIÁ BẢNG GIÁ & PHÍ DỊCH VỤ (PRICING & RATES) - 4 BẢNG
-- ============================================================================

-- 26. Gói đơn giá dịch vụ (Điện, Nước, Internet, Vệ sinh, Rác, Xe...)
CREATE TABLE [service_rate_plans] (
    [plan_id] INT IDENTITY(1,1) PRIMARY KEY,
    [plan_name] NVARCHAR(100) NOT NULL,
    [electric_unit_price] DECIMAL(18,2) NOT NULL, -- Giá 1 kWh
    [water_unit_price] DECIMAL(18,2) NOT NULL,    -- Giá 1 m3
    [effective_start_date] DATE NOT NULL,
    [effective_end_date] DATE NULL
);

-- 27. Đơn giá dịch vụ áp dụng riêng cho từng Khu trọ
CREATE TABLE [property_service_rates] (
    [rate_id] INT IDENTITY(1,1) PRIMARY KEY,
    [property_id] INT NOT NULL FOREIGN KEY REFERENCES [properties]([property_id]) ON DELETE CASCADE,
    [service_name] NVARCHAR(100) NOT NULL, -- Internet, Vệ sinh, Gửi xe, Rác thải...
    [charge_type] VARCHAR(30) NOT NULL 
        CHECK ([charge_type] IN ('FIXED_ROOM', 'PER_PERSON', 'PER_VEHICLE', 'PER_METER_UNIT')),
    [unit_price] DECIMAL(18,2) NOT NULL,
    [is_active] BIT DEFAULT 1
);

-- 28. Đơn giá dịch vụ / Giá thuê tùy chỉnh riêng cho từng Phòng
CREATE TABLE [room_custom_rates] (
    [custom_rate_id] INT IDENTITY(1,1) PRIMARY KEY,
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]) ON DELETE CASCADE,
    [rate_name] NVARCHAR(100) NOT NULL,
    [custom_price] DECIMAL(18,2) NOT NULL,
    [notes] NVARCHAR(255) NULL
);

-- 29. Tra cứu Lịch sử thay đổi đơn giá
CREATE TABLE [rate_change_histories] (
    [history_id] INT IDENTITY(1,1) PRIMARY KEY,
    [property_id] INT NOT NULL FOREIGN KEY REFERENCES [properties]([property_id]),
    [service_name] NVARCHAR(100) NOT NULL,
    [old_price] DECIMAL(18,2) NOT NULL,
    [new_price] DECIMAL(18,2) NOT NULL,
    [changed_by] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [changed_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- ============================================================================
-- PHÂN HỆ 7: TÍNH TIỀN HÀNG THÁNG & HÓA ĐƠN (CALCULATION & INVOICES) - 5 BẢNG
-- ============================================================================

-- 30. Kỳ tính tiền hàng tháng (Billing Period)
CREATE TABLE [monthly_billing_periods] (
    [billing_period_id] INT IDENTITY(1,1) PRIMARY KEY,
    [property_id] INT NOT NULL FOREIGN KEY REFERENCES [properties]([property_id]),
    [month_year] VARCHAR(7) NOT NULL, -- Format: YYYY-MM (e.g. 2026-03)
    [creation_date] DATETIME2 NOT NULL DEFAULT GETDATE(),
    [status] NVARCHAR(20) NOT NULL DEFAULT 'DRAFT' 
        CHECK ([status] IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'CLOSED'))
);

-- 31. Bảng tính tiền tổng hợp từng phòng trong kỳ
CREATE TABLE [calculated_monthly_fees] (
    [calculated_fee_id] INT IDENTITY(1,1) PRIMARY KEY,
    [billing_period_id] INT NOT NULL FOREIGN KEY REFERENCES [monthly_billing_periods]([billing_period_id]),
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]),
    [contract_id] INT NOT NULL FOREIGN KEY REFERENCES [rental_contracts]([contract_id]),
    [rent_amount] DECIMAL(18,2) NOT NULL,
    [electric_amount] DECIMAL(18,2) NOT NULL,
    [water_amount] DECIMAL(18,2) NOT NULL,
    [service_amount] DECIMAL(18,2) NOT NULL,
    [surcharge_amount] DECIMAL(18,2) DEFAULT 0,
    [discount_amount] DECIMAL(18,2) DEFAULT 0,
    [late_penalty_amount] DECIMAL(18,2) DEFAULT 0,
    [total_calculated_amount] DECIMAL(18,2) NOT NULL,
    CONSTRAINT [UQ_period_room_calculated] UNIQUE([billing_period_id], [room_id])
);

-- 32. Bảng chi tiết từng khoản phí trong kỳ tính tiền
CREATE TABLE [fee_item_details] (
    [detail_id] INT IDENTITY(1,1) PRIMARY KEY,
    [calculated_fee_id] INT NOT NULL FOREIGN KEY REFERENCES [calculated_monthly_fees]([calculated_fee_id]) ON DELETE CASCADE,
    [item_name] NVARCHAR(100) NOT NULL, -- Tiền phòng, Điện, Nước, Internet, Gửi xe 2 chiếc, Phụ thu...
    [quantity] DECIMAL(10,2) NOT NULL DEFAULT 1,
    [unit_price] DECIMAL(18,2) NOT NULL,
    [subtotal] DECIMAL(18,2) NOT NULL
);

-- 33. Hóa đơn tiền phòng hàng tháng (Issued Invoices)
CREATE TABLE [invoices] (
    [invoice_id] INT IDENTITY(1,1) PRIMARY KEY,
    [invoice_code] VARCHAR(50) NOT NULL UNIQUE,
    [calculated_fee_id] INT NOT NULL UNIQUE FOREIGN KEY REFERENCES [calculated_monthly_fees]([calculated_fee_id]),
    [tenant_id] INT NOT NULL FOREIGN KEY REFERENCES [tenants]([tenant_id]),
    [issue_date] DATE NOT NULL,
    [due_date] DATE NOT NULL,
    [total_amount] DECIMAL(18,2) NOT NULL,
    [paid_amount] DECIMAL(18,2) DEFAULT 0,
    [remaining_amount] AS ([total_amount] - [paid_amount]),
    [payment_status] NVARCHAR(20) NOT NULL DEFAULT 'UNPAID' 
        CHECK ([payment_status] IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED')),
    [share_link] NVARCHAR(500) NULL,
    [pdf_url] NVARCHAR(500) NULL
);

-- 34. Thông báo gửi hóa đơn qua Email / App
CREATE TABLE [invoice_notifications] (
    [notification_id] INT IDENTITY(1,1) PRIMARY KEY,
    [invoice_id] INT NOT NULL FOREIGN KEY REFERENCES [invoices]([invoice_id]) ON DELETE CASCADE,
    [channel] VARCHAR(20) NOT NULL CHECK ([channel] IN ('EMAIL', 'SMS', 'SYSTEM_APP')),
    [sent_at] DATETIME2 NOT NULL DEFAULT GETDATE(),
    [status] NVARCHAR(20) NOT NULL DEFAULT 'SENT'
);

-- ============================================================================
-- PHÂN HỆ 8: THANH TOÁN, THU TIỀN & CẤN TRỪ (PAYMENTS) - 5 BẢNG
-- ============================================================================

-- 35. Giao dịch Thanh toán Hóa đơn
CREATE TABLE [payment_transactions] (
    [transaction_id] INT IDENTITY(1,1) PRIMARY KEY,
    [invoice_id] INT NOT NULL FOREIGN KEY REFERENCES [invoices]([invoice_id]),
    [payment_method] VARCHAR(20) NOT NULL CHECK ([payment_method] IN ('CASH', 'BANK_TRANSFER', 'QR_CODE', 'GATEWAY')),
    [transaction_code] VARCHAR(100) NULL,
    [amount_paid] DECIMAL(18,2) NOT NULL,
    [payment_date] DATETIME2 NOT NULL DEFAULT GETDATE(),
    [received_by_user_id] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [status] NVARCHAR(20) NOT NULL DEFAULT 'SUCCESS' CHECK ([status] IN ('SUCCESS', 'FAILED', 'REVERSED'))
);

-- 36. Phiếu thu tiền phòng / Đặt cọc / Dịch vụ
CREATE TABLE [receipt_vouchers] (
    [voucher_id] INT IDENTITY(1,1) PRIMARY KEY,
    [voucher_code] VARCHAR(50) NOT NULL UNIQUE,
    [transaction_id] INT NULL FOREIGN KEY REFERENCES [payment_transactions]([transaction_id]),
    [voucher_type] VARCHAR(30) NOT NULL CHECK ([voucher_type] IN ('MONTHLY_RENT', 'DEPOSIT', 'EXTRA_SERVICE')),
    [amount] DECIMAL(18,2) NOT NULL,
    [note] NVARCHAR(255) NULL,
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 37. Ghi nhận thanh toán một phần / nhiều lần
CREATE TABLE [partial_payment_logs] (
    [partial_log_id] INT IDENTITY(1,1) PRIMARY KEY,
    [invoice_id] INT NOT NULL FOREIGN KEY REFERENCES [invoices]([invoice_id]) ON DELETE CASCADE,
    [installment_number] INT NOT NULL DEFAULT 1,
    [amount] DECIMAL(18,2) NOT NULL,
    [paid_date] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 38. Tài khoản dư tiền / Thanh toán thừa của Khách
CREATE TABLE [tenant_prepayments] (
    [prepayment_id] INT IDENTITY(1,1) PRIMARY KEY,
    [tenant_id] INT NOT NULL UNIQUE FOREIGN KEY REFERENCES [tenants]([tenant_id]),
    [excess_balance] DECIMAL(18,2) NOT NULL DEFAULT 0,
    [updated_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 39. Nhật ký cấn trừ tiền dư vào kỳ tiếp theo
CREATE TABLE [prepayment_deductions] (
    [deduction_id] INT IDENTITY(1,1) PRIMARY KEY,
    [prepayment_id] INT NOT NULL FOREIGN KEY REFERENCES [tenant_prepayments]([prepayment_id]),
    [target_invoice_id] INT NOT NULL FOREIGN KEY REFERENCES [invoices]([invoice_id]),
    [deducted_amount] DECIMAL(18,2) NOT NULL,
    [deducted_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- ============================================================================
-- PHÂN HỆ 9: THEO DÕI CÔNG NỢ & NHẮC NỢ (DEBT & RECEIVABLES) - 4 BẢNG
-- ============================================================================

-- 40. Sổ theo dõi Công nợ Khách thuê & Phòng trọ
CREATE TABLE [tenant_debts] (
    [debt_id] INT IDENTITY(1,1) PRIMARY KEY,
    [tenant_id] INT NOT NULL FOREIGN KEY REFERENCES [tenants]([tenant_id]),
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]),
    [invoice_id] INT NULL FOREIGN KEY REFERENCES [invoices]([invoice_id]),
    [opening_debt_balance] DECIMAL(18,2) DEFAULT 0, -- Nợ từ kỳ trước chuyển sang
    [current_period_debt] DECIMAL(18,2) DEFAULT 0,
    [total_outstanding_debt] DECIMAL(18,2) NOT NULL,
    [overdue_days] INT DEFAULT 0,
    [debt_status] NVARCHAR(30) NOT NULL DEFAULT 'NORMAL' 
        CHECK ([debt_status] IN ('NORMAL', 'WARNING_OVERDUE', 'LONG_TERM_DEBT', 'CLEARED')),
    [updated_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 41. Nhật ký phạt tiền chậm thanh toán
CREATE TABLE [debt_penalty_logs] (
    [penalty_id] INT IDENTITY(1,1) PRIMARY KEY,
    [debt_id] INT NOT NULL FOREIGN KEY REFERENCES [tenant_debts]([debt_id]) ON DELETE CASCADE,
    [overdue_days] INT NOT NULL,
    [daily_penalty_rate] DECIMAL(18,2) NOT NULL,
    [calculated_penalty_amount] DECIMAL(18,2) NOT NULL,
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 42. Thông báo nhắc nợ (Tự động 1st, 2nd, Cảnh báo quá hạn)
CREATE TABLE [debt_reminders] (
    [reminder_id] INT IDENTITY(1,1) PRIMARY KEY,
    [debt_id] INT NOT NULL FOREIGN KEY REFERENCES [tenant_debts]([debt_id]) ON DELETE CASCADE,
    [reminder_level] VARCHAR(20) NOT NULL CHECK ([reminder_level] IN ('FIRST_REMINDER', 'SECOND_REMINDER', 'FINAL_WARNING')),
    [message_content] NVARCHAR(MAX) NOT NULL,
    [sent_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 43. Ghi nhận lịch sử cam kết & Xử lý nợ
CREATE TABLE [debt_processing_logs] (
    [proc_log_id] INT IDENTITY(1,1) PRIMARY KEY,
    [debt_id] INT NOT NULL FOREIGN KEY REFERENCES [tenant_debts]([debt_id]) ON DELETE CASCADE,
    [promised_payment_date] DATE NULL,
    [delay_reason] NVARCHAR(500) NULL,
    [staff_note] NVARCHAR(500) NULL,
    [recorded_by] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [recorded_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- ============================================================================
-- PHÂN HỆ 10: TRẢ PHÒNG, HOÀN CỌC, SỰ CỐ & SỬA CHỮA (CHECKOUT & REPAIRS) - 5 BẢNG
-- ============================================================================

-- 44. Tiếp nhận Yêu cầu Trả phòng & Thanh lý Hợp đồng
CREATE TABLE [checkout_requests] (
    [checkout_id] INT IDENTITY(1,1) PRIMARY KEY,
    [contract_id] INT NOT NULL FOREIGN KEY REFERENCES [rental_contracts]([contract_id]),
    [expected_checkout_date] DATE NOT NULL,
    [actual_checkout_date] DATE NULL,
    [status] NVARCHAR(30) NOT NULL DEFAULT 'PENDING' 
        CHECK ([status] IN ('PENDING', 'INSPECTING', 'FINALIZED', 'CANCELLED')),
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 45. Biên bản kiểm tra phòng & Bồi thường hư hỏng tài sản khi trả
CREATE TABLE [checkout_inspections] (
    [inspection_id] INT IDENTITY(1,1) PRIMARY KEY,
    [checkout_id] INT NOT NULL UNIQUE FOREIGN KEY REFERENCES [checkout_requests]([checkout_id]) ON DELETE CASCADE,
    [final_electric_meter] INT NOT NULL,
    [final_water_meter] INT NOT NULL,
    [damaged_equipments_description] NVARCHAR(MAX) NULL,
    [damage_compensation_amount] DECIMAL(18,2) DEFAULT 0,
    [unpaid_utilities_amount] DECIMAL(18,2) DEFAULT 0,
    [inspection_photo_url] NVARCHAR(500) NULL,
    [inspected_by] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [inspected_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 46. Phiếu hoàn tiền đặt cọc (Sau cấn trừ công nợ & hư hỏng)
CREATE TABLE [deposit_refund_vouchers] (
    [refund_id] INT IDENTITY(1,1) PRIMARY KEY,
    [checkout_id] INT NOT NULL UNIQUE FOREIGN KEY REFERENCES [checkout_requests]([checkout_id]),
    [original_deposit_amount] DECIMAL(18,2) NOT NULL,
    [deducted_debt_amount] DECIMAL(18,2) DEFAULT 0,
    [deducted_damage_amount] DECIMAL(18,2) DEFAULT 0,
    [actual_refund_amount] DECIMAL(18,2) NOT NULL,
    [refund_date] DATETIME2 NULL,
    [status] NVARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK ([status] IN ('PENDING', 'REFUNDED'))
);

-- 47. Yêu cầu Sửa chữa Sự cố Điện / Nước / Thiết bị phòng
CREATE TABLE [maintenance_requests] (
    [request_id] INT IDENTITY(1,1) PRIMARY KEY,
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]),
    [tenant_id] INT NULL FOREIGN KEY REFERENCES [tenants]([tenant_id]),
    [category] NVARCHAR(50) NOT NULL, -- ELECTRIC_ISSUE, WATER_ISSUE, APPLIANCE_REPAIR...
    [description] NVARCHAR(MAX) NOT NULL,
    [issue_photo_url] NVARCHAR(500) NULL,
    [priority] NVARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK ([priority] IN ('LOW', 'NORMAL', 'URGENT')),
    [status] NVARCHAR(30) NOT NULL DEFAULT 'OPEN' 
        CHECK ([status] IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 48. Phân công Nhân viên & Chi phí Sửa chữa Sự cố
CREATE TABLE [maintenance_logs] (
    [maintenance_log_id] INT IDENTITY(1,1) PRIMARY KEY,
    [request_id] INT NOT NULL FOREIGN KEY REFERENCES [maintenance_requests]([request_id]) ON DELETE CASCADE,
    [assigned_staff_id] INT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [repair_cost] DECIMAL(18,2) DEFAULT 0,
    [tenant_rating] INT NULL CHECK ([tenant_rating] BETWEEN 1 AND 5),
    [resolution_notes] NVARCHAR(MAX) NULL,
    [completed_at] DATETIME2 NULL
);

-- ============================================================================
-- PHÂN HỆ 11: BỔ SUNG CHO CÁC USECASE CHƯA CÓ BẢNG (EXTENSIONS) - 8 BẢNG
-- ============================================================================

-- 49. Phương tiện của khách thuê (Usecase 34)
CREATE TABLE [tenant_vehicles] (
    [vehicle_id] INT IDENTITY(1,1) PRIMARY KEY,
    [tenant_id] INT NOT NULL FOREIGN KEY REFERENCES [tenants]([tenant_id]) ON DELETE CASCADE,
    [license_plate] VARCHAR(20) NOT NULL,
    [vehicle_type] NVARCHAR(20) NOT NULL CHECK ([vehicle_type] IN ('MOTORBIKE', 'BICYCLE', 'ELECTRIC_BIKE', 'CAR', 'OTHER')),
    [brand] NVARCHAR(50) NULL,
    [color] NVARCHAR(30) NULL,
    [is_active] BIT NOT NULL DEFAULT 1,
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE(),
    CONSTRAINT [UQ_vehicle_plate] UNIQUE([license_plate])
);

-- 50. Danh mục nhóm sự cố & nhóm thiết bị (Usecase 113)
CREATE TABLE [incident_categories] (
    [category_id] INT IDENTITY(1,1) PRIMARY KEY,
    [category_code] VARCHAR(50) NOT NULL UNIQUE, -- ELECTRIC_ISSUE, WATER_ISSUE, AIR_CONDITIONER...
    [category_name] NVARCHAR(100) NOT NULL,
    [category_type] VARCHAR(20) NOT NULL CHECK ([category_type] IN ('INCIDENT', 'EQUIPMENT')),
    [description] NVARCHAR(255) NULL,
    [is_active] BIT NOT NULL DEFAULT 1
);

-- 51. Yêu cầu sử dụng thêm / lắp đặt thiết bị trong phòng (Usecase 108)
CREATE TABLE [room_equipment_requests] (
    [equipment_request_id] INT IDENTITY(1,1) PRIMARY KEY,
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]),
    [tenant_id] INT NOT NULL FOREIGN KEY REFERENCES [tenants]([tenant_id]),
    [category_id] INT NULL FOREIGN KEY REFERENCES [incident_categories]([category_id]),
    [equipment_name] NVARCHAR(100) NOT NULL, -- Máy lạnh, Máy giặt, Bếp từ...
    [request_type] VARCHAR(20) NOT NULL CHECK ([request_type] IN ('INSTALL', 'EXTRA_USAGE')),
    [estimated_monthly_fee] DECIMAL(18,2) DEFAULT 0,
    [status] NVARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK ([status] IN ('PENDING', 'APPROVED', 'REJECTED', 'INSTALLED', 'REMOVED')),
    [reviewed_by] INT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [note] NVARCHAR(255) NULL,
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 52. Phí tiện ích bổ sung theo phòng (thiết bị điện, dịch vụ theo kỳ) (Usecase 109)
CREATE TABLE [room_addon_fees] (
    [addon_fee_id] INT IDENTITY(1,1) PRIMARY KEY,
    [room_id] INT NOT NULL FOREIGN KEY REFERENCES [rooms]([room_id]) ON DELETE CASCADE,
    [equipment_request_id] INT NULL FOREIGN KEY REFERENCES [room_equipment_requests]([equipment_request_id]),
    [fee_name] NVARCHAR(100) NOT NULL,
    [amount] DECIMAL(18,2) NOT NULL,
    [billing_cycle_months] INT NOT NULL DEFAULT 1,
    [start_date] DATE NOT NULL,
    [end_date] DATE NULL,
    [is_active] BIT NOT NULL DEFAULT 1
);

-- 53. Chi phí vận hành khu trọ (bảo trì, vệ sinh, thuế...) (Usecase 117, 118)
CREATE TABLE [operating_expenses] (
    [expense_id] INT IDENTITY(1,1) PRIMARY KEY,
    [property_id] INT NOT NULL FOREIGN KEY REFERENCES [properties]([property_id]),
    [expense_category] NVARCHAR(50) NOT NULL, -- Bảo trì, Vệ sinh, Thuế, Điện nước chung...
    [description] NVARCHAR(255) NULL,
    [amount] DECIMAL(18,2) NOT NULL,
    [expense_date] DATE NOT NULL,
    [receipt_url] NVARCHAR(500) NULL,
    [recorded_by] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]),
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- 54. Tài khoản ngân hàng & mã QR nhận tiền (Usecase 119)
CREATE TABLE [bank_accounts] (
    [bank_account_id] INT IDENTITY(1,1) PRIMARY KEY,
    [property_id] INT NOT NULL FOREIGN KEY REFERENCES [properties]([property_id]) ON DELETE CASCADE,
    [bank_name] NVARCHAR(100) NOT NULL,
    [account_number] VARCHAR(30) NOT NULL,
    [account_holder] NVARCHAR(100) NOT NULL,
    [qr_image_url] NVARCHAR(500) NULL,
    [is_default] BIT NOT NULL DEFAULT 0,
    [is_active] BIT NOT NULL DEFAULT 1
);

-- 55. Mẫu thông báo & kênh gửi (email, website) (Usecase 121)
CREATE TABLE [notification_templates] (
    [notif_template_id] INT IDENTITY(1,1) PRIMARY KEY,
    [template_code] VARCHAR(50) NOT NULL, -- INVOICE_ISSUED, DEBT_REMINDER_1, CONTRACT_EXPIRING...
    [channel] VARCHAR(20) NOT NULL CHECK ([channel] IN ('EMAIL', 'SMS', 'SYSTEM_APP')),
    [subject] NVARCHAR(200) NULL,
    [content] NVARCHAR(MAX) NOT NULL,
    [is_active] BIT NOT NULL DEFAULT 1,
    CONSTRAINT [UQ_notif_template_channel] UNIQUE([template_code], [channel])
);

-- 56. Trung tâm thông báo của người dùng (xem, đánh dấu đã đọc, xóa) (Usecase 123)
CREATE TABLE [user_notifications] (
    [user_notification_id] INT IDENTITY(1,1) PRIMARY KEY,
    [user_id] INT NOT NULL FOREIGN KEY REFERENCES [users]([user_id]) ON DELETE CASCADE,
    [notif_template_id] INT NULL FOREIGN KEY REFERENCES [notification_templates]([notif_template_id]),
    [title] NVARCHAR(200) NOT NULL,
    [content] NVARCHAR(MAX) NOT NULL,
    [reference_type] VARCHAR(30) NULL, -- INVOICE, CONTRACT, DEBT, MAINTENANCE...
    [reference_id] INT NULL,
    [is_read] BIT NOT NULL DEFAULT 0,
    [created_at] DATETIME2 NOT NULL DEFAULT GETDATE()
);
GO

-- ============================================================================
-- TẠO INDEXES TỐI ƯU HIỆU NĂNG CHO BẢNG TÍNH TIỀN, HÓA ĐƠN & CÔNG NỢ
-- ============================================================================

-- Index tra cứu chỉ số điện nước theo Kỳ và Phòng
CREATE INDEX IX_meter_readings_period_room ON [meter_readings] ([period_id], [room_id]);

-- Index tra cứu Hóa đơn theo Trạng thái thanh toán & Ngày đến hạn
CREATE INDEX IX_invoices_status_duedate ON [invoices] ([payment_status], [due_date]);

-- Index tra cứu Công nợ theo Khách thuê & Trạng thái nợ
CREATE INDEX IX_tenant_debts_status ON [tenant_debts] ([tenant_id], [debt_status]);

-- Index tra cứu Hợp đồng đang hiệu lực theo Phòng
CREATE INDEX IX_rental_contracts_room_status ON [rental_contracts] ([room_id], [status]);

-- Index phục vụ lọc và tra cứu phòng trọ nhanh
CREATE INDEX IX_rooms_property_status ON [rooms] ([property_id], [status]);
GO