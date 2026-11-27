-- PATCH TONG HOP: chay 1 lan cho DB cu, gom patch_01 den patch_16, an toan chay lai nhieu lan.
-- Cuoi file la phan KIEM TRA: in ra tung muc con THIEU (khong co dong nao = DB da du cho cac use case hien tai).
-- Cach chay (PHAI dung thu muc db/ vi :r doc file theo duong dan tuong doi):
--   cd db
--   sqlcmd -S localhost -E -C -f 65001 -b -I -i patch_all.sql
-- Dung SSMS/Azure Data Studio: bat che do SQLCMD roi mo file nay tu thu muc db/.
-- Them patch moi: them 1 dong :r o duoi + 1 dong vao bang kiem tra.
:on error exit
USE [MiniBoardingHouseDB];
GO
:r patch_01_users_unique.sql
:r patch_02_checkin_inspections.sql
:r patch_03_must_change_password.sql
:r patch_04_fee_per_contract.sql
:r patch_05_incident_categories.sql
:r patch_06_room_types.sql
:r patch_07_assign_room_types.sql
:r patch_08_standard_furnishings.sql
:r patch_09_room_meter_changes.sql
:r patch_10_guest_booking_holds.sql
:r patch_11_room_cleaning_status.sql
:r patch_12_tenant_residence_dates.sql
:r patch_13_contract_service_terms.sql
:r patch_14_transfer_closing_reading.sql
:r patch_15_segmented_meter_readings.sql
:r patch_16_job_settings.sql
:r patch_17_resident_stay_info.sql
:r patch_18_resident_id_card.sql
:r patch_19_revoked_sessions.sql
:r patch_20_invoice_share_expiration.sql
:r patch_21_business_time_defaults.sql
GO
USE [MiniBoardingHouseDB];
GO

-- KIEM TRA: moi dong ket qua = 1 muc con thieu (lien quan patch nao).
SET NOCOUNT ON;
SELECT x.patch AS patch_con_thieu, x.tbl AS bang, x.col AS cot
FROM (VALUES
    ('02', 'checkin_inspections', NULL),
    ('03', 'users', 'must_change_password'),
    ('06', 'room_types', NULL),
    ('07', 'rooms', 'room_type_id'),
    ('08', 'standard_furnishings', NULL),
    ('09', 'room_meter_changes', NULL),
    ('10', 'booking_requests', 'guest_name'),
    ('10', 'booking_requests', 'guest_phone'),
    ('10', 'booking_requests', 'hold_until'),
    ('10', 'rooms', 'held_booking_id'),
    ('12', 'tenants', 'residence_start_date'),
    ('12', 'tenants', 'residence_end_date'),
    ('13', 'rental_contracts', 'service_terms'),
    ('14', 'meter_readings', 'closing_contract_id'),
    ('16', 'job_settings', NULL),
    ('17', 'room_residents', 'date_of_birth'),
    ('17', 'room_residents', 'permanent_address'),
    ('17', 'room_residents', 'temp_residence_registered_on'),
    ('18', 'room_residents', 'id_front_image_url'),
    ('18', 'room_residents', 'id_back_image_url'),
    ('19', 'revoked_sessions', NULL),
    ('20', 'invoices', 'share_expires_at')
) AS x(patch, tbl, col)
WHERE (x.col IS NULL AND OBJECT_ID('dbo.' + x.tbl, 'U') IS NULL)
   OR (x.col IS NOT NULL AND COL_LENGTH('dbo.' + x.tbl, x.col) IS NULL)
UNION ALL
SELECT '05', 'incident_categories', 'thieu nhom su co INCIDENT (can >= 7)'
WHERE (SELECT COUNT(*) FROM incident_categories WHERE category_type = 'INCIDENT') < 7
UNION ALL
SELECT '04', 'calculated_monthly_fees', 'thieu UQ_period_room_contract_calculated'
WHERE NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_period_room_contract_calculated')
UNION ALL
SELECT '15', 'meter_readings', 'thieu UQ_period_room_contract_reading'
WHERE NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_period_room_contract_reading');

IF @@ROWCOUNT = 0 PRINT N'DU: DB da co day du doi tuong cua patch 01-21.';
ELSE PRINT N'THIEU: xem cac dong o tren, roi bao Phi.';
GO
