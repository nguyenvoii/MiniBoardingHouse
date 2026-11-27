#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sinh db/sample-data-test.sql: bộ dữ liệu giả định DÀY để test toàn bộ luồng của Stayly,
rồi ghép db/minihouse-full.sql = group-7.sql + seed.sql + sample-data-test.sql.

Chạy:  python db/tools/gen_sample_data.py
Dữ liệu cố định (random có hạt giống), mốc "hôm nay" là 2026-11-27, nên chạy lại ra đúng file cũ.
Số liệu hóa đơn được tính đúng như BillingService: tiền phòng theo ngày, điện nước theo bảng giá của kỳ,
dịch vụ theo phòng/người/xe, phụ thu - giảm trừ theo giá riêng phòng, phụ phí thiết bị, phạt trễ hạn.
Sửa schema thì chỉnh lại script này rồi sinh lại.
"""
import datetime as dt
import os
import random
import sys
import unicodedata
import uuid

sys.stdout.reconfigure(encoding="utf-8")
R = random.Random(20261127)
TODAY = dt.date(2026, 11, 27)
HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.dirname(HERE)

D = dt.date
DT = dt.datetime


def eom(d):
    nxt = (d.replace(day=28) + dt.timedelta(days=4)).replace(day=1)
    return nxt - dt.timedelta(days=1)


def bom(d):
    return d.replace(day=1)


def add_months(d, n):
    y, m = divmod(d.year * 12 + d.month - 1 + n, 12)
    m += 1
    day = min(d.day, [31, 29 if y % 4 == 0 and (y % 100 or y % 400 == 0) else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][m - 1])
    return D(y, m, day)


def at(d, h=9, mi=0):
    return DT(d.year, d.month, d.day, h, mi, 0)


# ------------------------------------------------------------------ định dạng SQL
class Raw(str):
    """Biểu thức SQL giữ nguyên, không bọc N'...'."""


def q(v):
    if isinstance(v, Raw):
        return str(v)
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int,)):
        return str(v)
    if isinstance(v, float):
        return repr(round(v, 2))
    if isinstance(v, dt.datetime):
        return "'" + v.strftime("%Y-%m-%d %H:%M:%S") + "'"
    if isinstance(v, dt.date):
        return "'" + v.isoformat() + "'"
    return "N'" + str(v).replace("'", "''") + "'"


class Table:
    def __init__(self, name, cols, pk=None):
        self.name, self.cols, self.pk, self.rows, self.next = name, cols, pk, [], 1

    def add(self, **kw):
        row = dict(kw)
        if self.pk and self.pk not in row:
            row[self.pk] = self.next
        if self.pk:
            self.next = max(self.next, row[self.pk]) + 1
        unknown = set(row) - set(self.cols) - {self.pk}
        if unknown:
            raise KeyError(f"{self.name}: cột lạ {unknown}")
        self.rows.append(row)
        return row[self.pk] if self.pk else None

    def sql(self):
        if not self.rows:
            return ""
        cols = ([self.pk] if self.pk else []) + [c for c in self.cols if c != self.pk]
        out = []
        if self.pk:
            out.append(f"SET IDENTITY_INSERT [{self.name}] ON;")
        head = f"INSERT INTO [{self.name}] ({', '.join('[' + c + ']' for c in cols)}) VALUES"
        for i in range(0, len(self.rows), 200):
            chunk = self.rows[i:i + 200]
            vals = ",\n".join("(" + ", ".join(q(r.get(c)) for c in cols) + ")" for r in chunk)
            out.append(head + "\n" + vals + ";")
        if self.pk:
            out.append(f"SET IDENTITY_INSERT [{self.name}] OFF;")
        return "\n".join(out)


def T(name, pk, cols):
    return Table(name, cols, pk)


users = T("users", "user_id", ["email", "phone_number", "password_hash", "full_name", "status", "must_change_password", "created_at", "updated_at"])
user_roles = Table("user_roles", ["user_id", "role_id"])  # role_id lấy theo role_code lúc chạy, xem phần phát SQL
properties = T("properties", "property_id", ["landlord_id", "property_name", "address", "total_floors", "support_phone", "created_at"])
floors = T("property_floors", "floor_id", ["property_id", "floor_number", "floor_name"])
room_types = T("room_types", "room_type_id", ["property_id", "type_name", "description", "default_rent_price", "default_deposit_amount"])
rooms = T("rooms", "room_id", ["property_id", "floor_id", "room_code", "area_sqm", "max_occupants", "base_rent_price", "deposit_amount",
                               "initial_electric_meter", "initial_water_meter", "status", "created_at", "room_type_id"])
furn = T("room_furnishings", "furnishing_id", ["room_id", "item_name", "quantity", "condition_status", "notes"])
templates = T("contract_templates", "template_id", ["template_name", "terms_content", "is_default"])
tenants = T("tenants", "tenant_id", ["full_name", "phone_number", "email", "dob", "identity_number", "permanent_address", "occupation",
                                     "emergency_contact_name", "emergency_contact_phone", "residence_start_date", "residence_end_date",
                                     "is_verified", "created_at"])
bookings = T("booking_requests", "booking_id", ["room_id", "tenant_id", "expected_start_date", "occupants_count", "deposit_paid", "status",
                                                "rejection_reason", "created_at", "guest_name", "guest_phone", "hold_until"])
contracts = T("rental_contracts", "contract_id", ["contract_code", "room_id", "tenant_id", "template_id", "start_date", "end_date",
                                                  "monthly_rent_price", "deposit_amount", "payment_cycle_months", "payment_due_day", "late_fee_per_day",
                                                  "electric_terms", "water_terms", "service_terms", "deposit_refund_conditions",
                                                  "digital_signature_url", "status", "created_at"])
adjustments = T("contract_adjustments", "adjustment_id", ["contract_id", "adjustment_type", "old_value", "new_value", "adjusted_by", "adjusted_at"])
c_notifs = T("contract_notifications", "notification_id", ["contract_id", "notification_type", "sent_at", "is_read"])
residents = T("room_residents", "resident_id", ["room_id", "tenant_id", "full_name", "phone_number", "identity_number", "relationship_with_primary",
                                                "date_of_birth", "permanent_address", "temp_residence_registered_on", "start_date", "end_date", "is_active"])
stays = T("resident_stay_histories", "history_id", ["tenant_id", "room_id", "start_date", "end_date", "checkout_reason"])
vehicles = T("tenant_vehicles", "vehicle_id", ["tenant_id", "license_plate", "vehicle_type", "brand", "color", "is_active", "created_at"])
checkins = T("checkin_inspections", "inspection_id", ["room_id", "tenant_id", "contract_id", "checkin_date", "initial_electric_meter",
                                                      "initial_water_meter", "key_handed_count", "furnishings_condition", "notes",
                                                      "tenant_confirmed", "inspected_by_name", "created_at"])
plans = T("service_rate_plans", "plan_id", ["plan_name", "electric_unit_price", "water_unit_price", "effective_start_date", "effective_end_date"])
svc_rates = T("property_service_rates", "rate_id", ["property_id", "service_name", "charge_type", "unit_price", "is_active"])
rate_hist = T("rate_change_histories", "history_id", ["property_id", "service_name", "old_price", "new_price", "changed_by", "changed_at"])
custom_rates = T("room_custom_rates", "custom_rate_id", ["room_id", "rate_name", "custom_price", "notes"])
rperiods = T("utility_reading_periods", "period_id", ["property_id", "period_name", "start_date", "end_date", "is_closed"])
readings = T("meter_readings", "reading_id", ["period_id", "room_id", "old_electric", "new_electric", "old_water", "new_water", "reading_date",
                                               "recorded_by_user_id", "is_verified"])
meter_adj = T("meter_adjustment_logs", "adj_id", ["reading_id", "adjusted_field", "old_val", "new_val", "reason", "adjusted_by", "adjusted_at"])
anomalies = T("meter_anomaly_alerts", "alert_id", ["reading_id", "utility_type", "increase_percentage", "alert_message", "is_resolved"])
bperiods = T("monthly_billing_periods", "billing_period_id", ["property_id", "month_year", "creation_date", "status"])
fees = T("calculated_monthly_fees", "calculated_fee_id", ["billing_period_id", "room_id", "contract_id", "rent_amount", "electric_amount", "water_amount",
                                                           "service_amount", "surcharge_amount", "discount_amount", "late_penalty_amount",
                                                           "total_calculated_amount"])
items = T("fee_item_details", "detail_id", ["calculated_fee_id", "item_name", "quantity", "unit_price", "subtotal"])
invoices = T("invoices", "invoice_id", ["invoice_code", "calculated_fee_id", "tenant_id", "issue_date", "due_date", "total_amount", "paid_amount",
                                         "payment_status", "share_link"])
inv_notifs = T("invoice_notifications", "notification_id", ["invoice_id", "channel", "sent_at", "status"])
txns = T("payment_transactions", "transaction_id", ["invoice_id", "payment_method", "transaction_code", "amount_paid", "payment_date",
                                                     "received_by_user_id", "status"])
vouchers = T("receipt_vouchers", "voucher_id", ["voucher_code", "transaction_id", "voucher_type", "amount", "note", "created_at"])
partials = T("partial_payment_logs", "partial_log_id", ["invoice_id", "installment_number", "amount", "paid_date"])
prepay = T("tenant_prepayments", "prepayment_id", ["tenant_id", "excess_balance", "updated_at"])
prepay_ded = T("prepayment_deductions", "deduction_id", ["prepayment_id", "target_invoice_id", "deducted_amount", "deducted_at"])
debts = T("tenant_debts", "debt_id", ["tenant_id", "room_id", "invoice_id", "opening_debt_balance", "current_period_debt", "total_outstanding_debt",
                                       "overdue_days", "debt_status", "updated_at"])
penalties = T("debt_penalty_logs", "penalty_id", ["debt_id", "overdue_days", "daily_penalty_rate", "calculated_penalty_amount", "created_at"])
reminders = T("debt_reminders", "reminder_id", ["debt_id", "reminder_level", "message_content", "sent_at"])
proc_logs = T("debt_processing_logs", "proc_log_id", ["debt_id", "promised_payment_date", "delay_reason", "staff_note", "recorded_by", "recorded_at"])
checkouts = T("checkout_requests", "checkout_id", ["contract_id", "expected_checkout_date", "actual_checkout_date", "status", "created_at"])
co_insp = T("checkout_inspections", "inspection_id", ["checkout_id", "final_electric_meter", "final_water_meter", "damaged_equipments_description",
                                                       "damage_compensation_amount", "unpaid_utilities_amount", "inspected_by", "inspected_at"])
refunds = T("deposit_refund_vouchers", "refund_id", ["checkout_id", "original_deposit_amount", "deducted_debt_amount", "deducted_damage_amount",
                                                      "actual_refund_amount", "refund_date", "status"])
tickets = T("maintenance_requests", "request_id", ["room_id", "tenant_id", "category", "description", "priority", "status", "created_at"])
m_logs = T("maintenance_logs", "maintenance_log_id", ["request_id", "assigned_staff_id", "repair_cost", "tenant_rating", "resolution_notes", "completed_at"])
eq_req = T("room_equipment_requests", "equipment_request_id", ["room_id", "tenant_id", "category_id", "equipment_name", "request_type",
                                                                "estimated_monthly_fee", "status", "reviewed_by", "note", "created_at"])
addons = T("room_addon_fees", "addon_fee_id", ["room_id", "equipment_request_id", "fee_name", "amount", "billing_cycle_months", "start_date", "end_date", "is_active"])
expenses = T("operating_expenses", "expense_id", ["property_id", "expense_category", "description", "amount", "expense_date", "recorded_by", "created_at"])
banks = T("bank_accounts", "bank_account_id", ["property_id", "bank_name", "account_number", "account_holder", "is_default", "is_active"])
notif_tpl = T("notification_templates", "notif_template_id", ["template_code", "channel", "subject", "content", "is_active"])
user_notifs = T("user_notifications", "user_notification_id", ["user_id", "notif_template_id", "title", "content", "reference_type", "reference_id", "is_read", "created_at"])
logs = T("user_activity_logs", "log_id", ["user_id", "action_type", "description", "ip_address", "created_at"])

# ================================================================== dữ liệu gốc
PWD_HASH_SQL = Raw("@pwd")  # băm mật khẩu Admin@123 lấy từ tài khoản admin lúc chạy (mọi tài khoản mẫu dùng chung mật khẩu này)
ADMIN, MANAGER, CASHIER, READER = 1, 2, 3, 4  # user_id (admin là dòng đầu do seed.sql nạp)

MONTHS = [D(2026, m, 1) for m in range(6, 12)]  # 06..11/2026
YM = lambda d: d.strftime("%Y-%m")


def unaccent(s):
    s = s.replace("đ", "d").replace("Đ", "D")
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")


LAST = ["Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý"]
MMID = ["Văn", "Minh", "Quốc", "Hữu", "Gia", "Đức", "Anh", "Công"]
FMID = ["Thị", "Ngọc", "Thanh", "Kim", "Thùy", "Mai", "Phương", "Hoài"]
MGIVEN = ["An", "Bình", "Cường", "Dũng", "Đạt", "Hải", "Hiếu", "Hùng", "Khang", "Khoa", "Long", "Nam", "Phúc", "Quân", "Sơn", "Tài",
          "Thắng", "Tuấn", "Việt", "Vinh", "Trung", "Kiên", "Lộc", "Huy", "Nghĩa", "Toàn", "Đông", "Hậu", "Lâm", "Phong"]
FGIVEN = ["Anh", "Chi", "Dung", "Giang", "Hà", "Hạnh", "Hoa", "Hương", "Lan", "Linh", "Mai", "My", "Ngân", "Nhung", "Oanh", "Quyên", "Thảo",
          "Trang", "Trinh", "Uyên", "Vy", "Yến", "Châu", "Diệu", "Kiều", "Loan", "Nga", "Thu", "Tuyết", "Xuân"]
JOBS = ["Nhân viên văn phòng", "Sinh viên", "Công nhân", "Nhân viên bán hàng", "Lập trình viên", "Giáo viên", "Kỹ thuật viên", "Tài xế công nghệ",
        "Nhân viên y tế", "Kế toán", "Thợ làm tóc", "Phục vụ nhà hàng", "Thiết kế đồ họa", "Nhân viên kho"]
HOME = ["Ấp 3, xã Tân Lập, huyện Mộc Hóa, tỉnh Long An", "Xã Phú Mỹ, huyện Châu Thành, tỉnh Tiền Giang", "Xã An Khánh, huyện Châu Thành, tỉnh Bến Tre",
        "Phường 2, thành phố Cao Lãnh, tỉnh Đồng Tháp", "Quận Ninh Kiều, thành phố Cần Thơ", "Xã Vĩnh Trạch, thành phố Long Xuyên, tỉnh An Giang",
        "Xã Tịnh Hà, huyện Sơn Tịnh, tỉnh Quảng Ngãi", "Xã Cát Tường, huyện Phù Cát, tỉnh Bình Định", "Xã Diễn Hồng, huyện Diễn Châu, tỉnh Nghệ An",
        "Xã Hoằng Quang, thành phố Thanh Hóa, tỉnh Thanh Hóa", "Xã Ea Kao, thành phố Buôn Ma Thuột, tỉnh Đắk Lắk", "Phường 7, thành phố Đà Lạt, tỉnh Lâm Đồng",
        "Xã Long Thành Bắc, thị xã Hòa Thành, tỉnh Tây Ninh", "Phường Lái Thiêu, thành phố Thuận An, tỉnh Bình Dương"]
used_names, used_phones, used_cccd = set(), set(), set()


def person(i, female=None):
    for k in range(200):
        fem = (i + k) % 2 == 1 if female is None else female
        last = LAST[(i * 5 + k) % len(LAST)]
        mid = (FMID if fem else MMID)[(i * 3 + k) % 8]
        given = (FGIVEN if fem else MGIVEN)[(i * 7 + i // 3 + k) % 30]
        name = f"{last} {mid} {given}"
        if name not in used_names:
            used_names.add(name)
            return name, fem
    raise RuntimeError("hết tên")


def phone():
    while True:
        p = "09" + "".join(str(R.randrange(10)) for _ in range(8))
        if p not in used_phones:
            used_phones.add(p)
            return p


def cccd():
    while True:
        c = "079" + "".join(str(R.randrange(10)) for _ in range(9))
        if c not in used_cccd:
            used_cccd.add(c)
            return c


# ------------------------------------------------------------------ nhân viên và tài khoản
users.next = 2  # user_id 1 là admin do seed.sql nạp
STAFF = [
    (2, "quanly@minihouse.local", "0903000002", "Lê Thị Thu Hà", "ACTIVE", "MANAGER"),
    (3, "thungan@minihouse.local", "0903000003", "Nguyễn Văn Tài", "ACTIVE", "CASHIER"),
    (4, "ghidien@minihouse.local", "0903000004", "Trần Minh Khôi", "ACTIVE", "METER_READER"),
    (5, "quanly2@minihouse.local", "0903000005", "Phạm Quốc Bảo", "LOCKED", "MANAGER"),
    (6, "nvcu@minihouse.local", "0903000006", "Võ Thị Kim Ngân", "INACTIVE", "CASHIER"),
]
role_rows = []
for uid, em, ph, nm, st, role in STAFF:
    users.add(user_id=uid, email=em, phone_number=ph, password_hash=PWD_HASH_SQL, full_name=nm, status=st, must_change_password=False,
              created_at=at(D(2026, 5, 20)), updated_at=at(D(2026, 5, 20)))
    role_rows.append((uid, role))

# ------------------------------------------------------------------ khu trọ, tầng, loại phòng, phòng
LANDLORD = ADMIN
pA = properties.add(landlord_id=LANDLORD, property_name="Khu trọ Hoa Sen", address="45 Huỳnh Tấn Phát, phường Tân Thuận Đông, Quận 7, TP.HCM",
                    total_floors=3, support_phone="0901234567", created_at=at(D(2025, 5, 1)))
pB = properties.add(landlord_id=LANDLORD, property_name="Khu trọ Bình Minh", address="18 đường số 9, phường Linh Trung, TP. Thủ Đức, TP.HCM",
                    total_floors=2, support_phone="0907654321", created_at=at(D(2025, 5, 1)))
floor_id = {}
for p, n in ((pA, 3), (pB, 2)):
    for f in range(1, n + 1):
        floor_id[(p, f)] = floors.add(property_id=p, floor_number=f, floor_name=f"Tầng {f}")

tA = [room_types.add(property_id=pA, type_name=n, description=d, default_rent_price=r, default_deposit_amount=r)
      for n, d, r in (("Phòng đơn", "20 m², 1 giường, ban công nhỏ", 2800000), ("Phòng đôi", "28 m², 2 giường, bếp riêng", 3600000),
                      ("Studio gác lửng", "32 m², gác lửng, bếp và tủ lạnh", 4300000))]
tB = [room_types.add(property_id=pB, type_name=n, description=d, default_rent_price=r, default_deposit_amount=r)
      for n, d, r in (("Phòng thường", "18 m², 1 giường", 2500000), ("Phòng rộng", "26 m², ban công, bếp", 3200000))]

ROOM = {}  # mã phòng -> dict
VACANT, REPAIR, CLEAN, SUSP = {"H104", "H207", "H308", "B104", "B206"}, {"H206"}, {"H303"}, {"B203"}
for p, nfl, per, prefix in ((pA, 3, 8, "H"), (pB, 2, 6, "B")):
    for f in range(1, nfl + 1):
        for n in range(1, per + 1):
            code = f"{prefix}{f}{n:02d}"
            if p == pA:
                kind = 0 if n <= 4 else (1 if n <= 6 else 2)
                area, mx, rent = ((20, 2, 2800000), (28, 4, 3600000), (32, 3, 4300000))[kind]
                rent += 100000 * (f - 1)
                type_id = tA[kind]
            else:
                kind = 0 if n <= 4 else 1
                area, mx, rent = ((18, 2, 2500000), (26, 3, 3200000))[kind]
                type_id = tB[kind]
            idx = len(ROOM)
            status = ("VACANT" if code in VACANT else "REPAIRING" if code in REPAIR else "CLEANING" if code in CLEAN
                      else "SUSPENDED" if code in SUSP else "RENTED")
            rid = rooms.add(property_id=p, floor_id=floor_id[(p, f)], room_code=code, area_sqm=area + (n % 3), max_occupants=mx,
                            base_rent_price=rent, deposit_amount=rent, initial_electric_meter=1000 + (idx * 37) % 600,
                            initial_water_meter=100 + (idx * 11) % 90, status=status, created_at=at(D(2025, 5, 10)), room_type_id=type_id)
            ROOM[code] = dict(id=rid, prop=p, code=code, floor=f, kind=kind, rent=rent, status=status, e0=1000 + (idx * 37) % 600,
                              w0=100 + (idx * 11) % 90, mx=mx, idx=idx)

# đồ đạc từng phòng: giường, tủ, bàn luôn có; máy lạnh/nóng lạnh/tủ lạnh tùy loại
STD = {"Giường ngủ gỗ 1m6": ("Mới / Tốt", None), "Tủ quần áo gỗ 2 cánh": ("Mới / Tốt", None), "Bàn ghế làm việc": ("Mới / Tốt", None),
       "Máy lạnh Inverter 1.5 HP": ("Mới / Hoạt động tốt", "Kèm remote"), "Bình nóng lạnh Ariston 20L": ("Mới / Hoạt động tốt", None),
       "Tủ lạnh mini 90L": ("Mới / Hoạt động tốt", None)}
FURN = {}
for code, r in ROOM.items():
    names = ["Giường ngủ gỗ 1m6", "Tủ quần áo gỗ 2 cánh", "Bàn ghế làm việc", "Bình nóng lạnh Ariston 20L"]
    if r["prop"] == pA or r["kind"] == 1:
        names.append("Máy lạnh Inverter 1.5 HP")
    if r["prop"] == pA and r["kind"] == 2:
        names.append("Tủ lạnh mini 90L")
    FURN[code] = names
    for n in names:
        cond, note = STD[n]
        if (r["idx"] + len(n)) % 11 == 0:
            cond, note = "Đã qua sử dụng, hoạt động tốt", "Trầy xước nhẹ"
        furn.add(room_id=r["id"], item_name=n, quantity=2 if n.startswith("Giường") and r["kind"] == 1 and r["prop"] == pA else 1,
                 condition_status=cond, notes=note)

# ------------------------------------------------------------------ mẫu hợp đồng
TERMS_STD = ("1. Bên A cho Bên B thuê phòng đúng mã phòng ghi trong hợp đồng, thời hạn theo hợp đồng.\n2. Bên B đặt cọc một tháng tiền phòng, "
             "hoàn lại khi trả phòng sau khi trừ công nợ và hư hỏng.\n3. Tiền phòng trả hằng tháng trước hạn ghi trong hợp đồng; trễ hạn phạt theo ngày.\n"
             "4. Điện nước tính theo công tơ riêng, dịch vụ chung theo bảng giá của khu trọ.\n5. Không nuôi thú cưng, không gây ồn sau 22 giờ, "
             "không sửa chữa phòng khi chưa báo chủ trọ.\n6. Báo trước 30 ngày khi muốn trả phòng.")
TERMS_LONG = TERMS_STD + "\n7. Hợp đồng 24 tháng: giá thuê giữ nguyên 12 tháng đầu, điều chỉnh tối đa 5% từ tháng 13 và báo trước 60 ngày."
TERMS_SHARE = TERMS_STD + "\n7. Phòng ở ghép: Bên B đứng tên chịu trách nhiệm khai báo đầy đủ người ở cùng và đăng ký tạm trú trong 30 ngày."
T_STD = templates.add(template_name="Hợp đồng thuê phòng trọ tiêu chuẩn", terms_content=TERMS_STD, is_default=True)
T_LONG = templates.add(template_name="Hợp đồng thuê dài hạn 24 tháng", terms_content=TERMS_LONG, is_default=False)
T_SHARE = templates.add(template_name="Hợp đồng phòng ở ghép", terms_content=TERMS_SHARE, is_default=False)

# ------------------------------------------------------------------ hợp đồng: bảng nguồn
# (phòng, bắt đầu, số tháng, trạng thái, hạn đóng, giá riêng hoặc None, đã ký, mẫu)
CUR = [
    ("H101", D(2025, 11, 16), 12, "EXPIRED", 5, None, True, T_STD), ("H102", D(2025, 12, 11), 12, "EXPIRING_SOON", 5, None, True, T_STD),
    ("H103", D(2026, 2, 1), 12, "ACTIVE", 5, None, True, T_STD), ("H105", D(2026, 4, 10), 12, "ACTIVE", 10, None, True, T_SHARE),
    ("H106", D(2025, 8, 1), 24, "ACTIVE", 5, None, True, T_LONG), ("H107", D(2026, 11, 3), 12, "ACTIVE", 5, None, False, T_STD),
    ("H108", D(2026, 1, 1), 12, "ACTIVE", 5, None, True, T_STD), ("H201", D(2025, 12, 2), 12, "EXPIRING_SOON", 5, None, True, T_STD),
    ("H202", D(2026, 8, 15), 12, "ACTIVE", 5, None, True, T_STD), ("H203", D(2026, 5, 1), 12, "ACTIVE", 10, None, True, T_STD),
    ("H204", D(2026, 3, 15), 12, "ACTIVE", 5, None, True, T_SHARE), ("H205", D(2025, 9, 1), 24, "ACTIVE", 5, 3400000, True, T_LONG),
    ("H208", D(2026, 6, 1), 6, "EXPIRING_SOON", 5, None, True, T_STD), ("H301", D(2026, 7, 1), 12, "ACTIVE", 5, None, True, T_STD),
    ("H302", D(2026, 10, 5), 12, "ACTIVE", 5, None, False, T_STD), ("H304", D(2026, 2, 20), 12, "ACTIVE", 5, None, True, T_STD),
    ("H305", D(2025, 12, 21), 12, "EXPIRING_SOON", 5, None, True, T_STD), ("H306", D(2026, 6, 15), 12, "ACTIVE", 10, None, True, T_STD),
    ("H307", D(2026, 9, 1), 12, "ACTIVE", 5, None, True, T_STD),
    ("B101", D(2025, 11, 11), 12, "EXPIRED", 5, None, True, T_STD), ("B102", D(2026, 7, 10), 12, "ACTIVE", 5, None, True, T_STD),
    ("B103", D(2026, 3, 1), 12, "ACTIVE", 5, None, True, T_STD), ("B105", D(2026, 5, 15), 12, "ACTIVE", 10, None, True, T_STD),
    ("B106", D(2026, 9, 15), 12, "ACTIVE", 5, None, True, T_STD), ("B201", D(2026, 1, 10), 12, "ACTIVE", 5, None, True, T_STD),
    ("B202", D(2025, 12, 16), 12, "EXPIRING_SOON", 5, None, True, T_STD), ("B204", D(2026, 8, 1), 12, "ACTIVE", 5, None, True, T_STD),
    ("B205", D(2026, 10, 1), 12, "ACTIVE", 10, None, False, T_STD),
]
# hợp đồng đã kết thúc: (phòng, bắt đầu, ngày kết thúc, lý do)
HIST = [
    ("H104", D(2026, 1, 1), D(2026, 10, 31), "Chuyển về quê làm việc"), ("H206", D(2025, 12, 1), D(2026, 9, 30), "Hết hạn, không gia hạn"),
    ("H303", D(2026, 3, 1), D(2026, 11, 24), "Trả phòng trước hạn, chuyển công tác"), ("B104", D(2026, 2, 1), D(2026, 8, 31), "Chuyển sang khu khác"),
    ("B206", D(2026, 4, 1), D(2026, 7, 31), "Hết hạn hợp đồng 4 tháng"), ("H202", D(2025, 10, 1), D(2026, 7, 31), "Mua nhà riêng"),
    ("H302", D(2026, 1, 1), D(2026, 9, 30), "Đổi chỗ ở gần nơi làm việc"), ("B102", D(2025, 12, 1), D(2026, 6, 30), "Hết hạn, không gia hạn"),
]
LATE_FEE = {pA: 20000, pB: 15000}
SIGN_SVG = ("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='120' height='50'><path d='M6 34 C 20 4, 34 52, 52 24 "
            "S 86 34, 112 12' stroke='black' stroke-width='2' fill='none'/></svg>")

TEN = {}   # tenant_id -> dict
CT = []    # danh sách hợp đồng (dict)
TENANT_OF_ROOM = {}
ti = 0


def new_tenant(created, verified=True, with_email=True, residence=None, female=None):
    global ti
    ti += 1
    name, fem = person(ti, female)
    dob = D(1986 + (ti * 7) % 18, 1 + (ti * 5) % 12, 1 + (ti * 11) % 27)
    em = f"{unaccent(name.split()[-1]).lower()}.{unaccent(name.split()[0]).lower()}{ti}@gmail.com" if with_email else None
    emname, _ = person(ti + 500)
    res_start = res_end = None
    if residence:
        res_start, res_end = residence
    tid = tenants.add(full_name=name, phone_number=phone(), email=em, dob=dob, identity_number=cccd(), permanent_address=HOME[ti % len(HOME)],
                      occupation=JOBS[ti % len(JOBS)], emergency_contact_name=emname, emergency_contact_phone=phone(),
                      residence_start_date=res_start, residence_end_date=res_end, is_verified=verified, created_at=at(created, 10))
    TEN[tid] = dict(id=tid, name=name, phone=tenants.rows[-1]["phone_number"], fem=fem)
    return tid


def make_contract(room, start, end, status, due, rent, signed, tmpl, tenant_id, term_note=None):
    r = ROOM[room]
    rent = rent or r["rent"]
    code = f"HD{start.strftime('%Y%m%d')}-{room}"
    cid = contracts.add(contract_code=code, room_id=r["id"], tenant_id=tenant_id, template_id=tmpl, start_date=start, end_date=end,
                        monthly_rent_price=rent, deposit_amount=rent, payment_cycle_months=1, payment_due_day=due, late_fee_per_day=LATE_FEE[r["prop"]],
                        electric_terms="Tính theo công tơ riêng của phòng, theo bảng giá của khu trọ",
                        water_terms="Tính theo đồng hồ nước riêng của phòng, theo bảng giá của khu trọ",
                        service_terms="Internet, rác và vệ sinh, gửi xe theo bảng giá dịch vụ của khu trọ",
                        deposit_refund_conditions="Hoàn cọc trong 7 ngày sau khi trả phòng, sau khi trừ công nợ và tiền hư hỏng thiết bị",
                        digital_signature_url=SIGN_SVG if signed else None, status=status, created_at=at(start - dt.timedelta(days=2), 10))
    c = dict(id=cid, code=code, room=room, prop=r["prop"], tenant=tenant_id, start=start, end=end, status=status, rent=rent, due=due,
             late=LATE_FEE[r["prop"]], signed=signed, term_note=term_note)
    CT.append(c)
    TENANT_OF_ROOM.setdefault(room, []).append(c)
    return c


for room, start, months, status, due, rent, signed, tmpl in CUR:
    end = add_months(start, months) - dt.timedelta(days=1)
    tid = new_tenant(start - dt.timedelta(days=5), residence=(start, end) if (start.month + start.day) % 3 == 0 else None)
    make_contract(room, start, end, status, due, rent, signed, tmpl, tid)
hist_ct = []
for room, start, end, why in HIST:
    tid = new_tenant(start - dt.timedelta(days=5), female=None)
    hist_ct.append(make_contract(room, start, end, "TERMINATED", 5, None, True, T_STD, tid, why))
CUR_CT = {c["room"]: c for c in CT if c["status"] != "TERMINATED"}
HIST_CT = {c["room"] + str(c["start"]): c for c in CT if c["status"] == "TERMINATED"}

# khách tiềm năng (chưa có hợp đồng): đã hỏi thuê, có phiếu đặt phòng hoặc chỉ có hồ sơ
PROSPECT = [new_tenant(D(2026, 11, 1 + (i * 2) % 25), verified=i % 3 == 0, with_email=i % 2 == 0) for i in range(10)]

# ------------------------------------------------------------------ giá dịch vụ, bảng giá điện nước
plans.add(plan_name="Bảng giá 2026", electric_unit_price=3500, water_unit_price=15000, effective_start_date=D(2026, 1, 1), effective_end_date=D(2026, 9, 30))
plans.add(plan_name="Bảng giá từ 10/2026", electric_unit_price=3800, water_unit_price=16000, effective_start_date=D(2026, 10, 1), effective_end_date=None)
PLAN = lambda ym: (3500, 15000) if ym < D(2026, 10, 1) else (3800, 16000)
SERV = {  # (tên, kiểu, đơn giá theo tháng)
    pA: [("Internet", "FIXED_ROOM", lambda m: 100000), ("Rác và vệ sinh", "FIXED_ROOM", lambda m: 50000),
         ("Gửi xe", "PER_VEHICLE", lambda m: 70000), ("Vệ sinh khu chung", "PER_PERSON", lambda m: 30000)],
    pB: [("Internet", "FIXED_ROOM", lambda m: 80000 if m < D(2026, 10, 1) else 90000), ("Rác và vệ sinh", "FIXED_ROOM", lambda m: 40000),
         ("Gửi xe", "PER_VEHICLE", lambda m: 60000)],
}
for p in (pA, pB):
    for n, kind, fn in SERV[p]:
        svc_rates.add(property_id=p, service_name=n, charge_type=kind, unit_price=fn(D(2026, 11, 1)), is_active=True)
rate_hist.add(property_id=pB, service_name="Internet", old_price=80000, new_price=90000, changed_by=ADMIN, changed_at=at(D(2026, 9, 28)))
rate_hist.add(property_id=pA, service_name="Điện", old_price=3500, new_price=3800, changed_by=ADMIN, changed_at=at(D(2026, 9, 28)))
rate_hist.add(property_id=pB, service_name="Điện", old_price=3500, new_price=3800, changed_by=ADMIN, changed_at=at(D(2026, 9, 28)))
rate_hist.add(property_id=pA, service_name="Nước", old_price=15000, new_price=16000, changed_by=ADMIN, changed_at=at(D(2026, 9, 28)))
CUSTOM = [("H106", "Ưu đãi khách lâu năm", -100000, "Khách ở trên 12 tháng"), ("H205", "Ưu đãi khách lâu năm", -100000, "Khách ở trên 12 tháng"),
          ("H108", "Nuôi thú cưng", 100000, "Một chú mèo, đã thỏa thuận"), ("B103", "Máy giặt dùng chung", 50000, "Dùng máy giặt chung ở tầng 1")]
for room, n, price, note in CUSTOM:
    custom_rates.add(room_id=ROOM[room]["id"], rate_name=n, custom_price=price, notes=note)

# phụ phí thiết bị gắn vào hóa đơn: (phòng, tên thiết bị, số tiền, bắt đầu)
ADDON = [("H103", "Máy giặt", 80000, D(2026, 9, 1)), ("B201", "Tủ lạnh thêm", 60000, D(2026, 8, 1)), ("H204", "Máy lạnh thêm", 150000, D(2026, 10, 1))]

# ------------------------------------------------------------------ người ở cùng và xe
# (phòng, họ tên, quan hệ, vào ở, đã khai tạm trú, còn ở)
RES = [("H103", "Vợ", D(2026, 2, 1), D(2026, 2, 20), True), ("H105", "Bạn cùng phòng", D(2026, 4, 10), D(2026, 4, 25), True),
       ("H106", "Con", D(2025, 8, 1), D(2025, 8, 15), True), ("H108", "Anh em", D(2026, 1, 1), None, True),
       ("H204", "Bạn cùng phòng", D(2026, 3, 15), D(2026, 4, 1), True), ("H204", "Bạn cùng phòng", D(2026, 6, 1), None, True),
       ("H301", "Vợ", D(2026, 7, 1), D(2026, 7, 20), True), ("H304", "Em gái", D(2026, 11, 2), None, True),
       ("H306", "Chồng", D(2026, 10, 20), None, True), ("H307", "Con", D(2026, 9, 1), D(2026, 9, 15), True),
       ("H201", "Bạn", D(2026, 5, 1), D(2026, 5, 20), False), ("B103", "Vợ", D(2026, 3, 1), D(2026, 3, 18), True),
       ("B201", "Em họ", D(2026, 1, 10), D(2026, 2, 1), True), ("B205", "Bạn cùng phòng", D(2026, 11, 8), None, True)]
RES_BY_ROOM = {}
for i, (room, rel, start, reg, active) in enumerate(RES):
    ct = CUR_CT[room]
    fem = rel in ("Vợ", "Em gái", "Con") if rel != "Con" else i % 2 == 0
    name, _ = person(900 + i, fem)
    adult = rel != "Con"
    end = None if active else None
    rid = residents.add(room_id=ROOM[room]["id"], tenant_id=None, full_name=name, phone_number=phone() if adult else None,
                        identity_number=cccd() if adult else None, relationship_with_primary=rel,
                        date_of_birth=D(1990 + (i * 3) % 12, 1 + i % 12, 3 + i) if adult else D(2016 + i % 5, 6, 10 + i),
                        permanent_address=HOME[(i + 3) % len(HOME)], temp_residence_registered_on=reg, start_date=start, end_date=end, is_active=active)
    RES_BY_ROOM.setdefault(room, []).append(dict(start=start, end=end))
# hai người đã rời phòng (lịch sử)
for room, rel, start, endd in (("H108", "Bạn", D(2026, 1, 1), D(2026, 6, 30)), ("B201", "Anh trai", D(2026, 1, 10), D(2026, 4, 30))):
    name, _ = person(950 + len(RES_BY_ROOM), False)
    residents.add(room_id=ROOM[room]["id"], tenant_id=None, full_name=name, phone_number=phone(), identity_number=cccd(), relationship_with_primary=rel,
                  date_of_birth=D(1994, 5, 5), permanent_address=HOME[5], temp_residence_registered_on=start + dt.timedelta(days=12), start_date=start,
                  end_date=endd, is_active=False)
    RES_BY_ROOM[room].append(dict(start=start, end=endd))

PLATE_CITY = ["59", "50", "51", "60", "61"]
used_plates = set()
VEH = {}  # tenant -> số xe đang dùng


def plate():
    while True:
        p = f"{R.choice(PLATE_CITY)}{R.choice('ABCDEFGHKLMNP')}{R.randrange(1, 10)}-{R.randrange(100, 1000)}.{R.randrange(10, 100)}"
        if p not in used_plates:
            used_plates.add(p)
            return p


for c in CT:
    if c["status"] == "TERMINATED" and c["room"] in ("B206",):
        n = 1
    else:
        n = (c["id"] * 7) % 4 // 1
        n = [1, 1, 2, 0][n % 4]
    for k in range(n):
        typ = "MOTORBIKE"
        brand, color = R.choice([("Honda", "Đen"), ("Yamaha", "Xanh"), ("Honda", "Trắng"), ("Suzuki", "Đỏ"), ("Honda", "Xám")])
        if k == 1 and c["id"] % 3 == 0:
            typ, brand, color = "ELECTRIC_BIKE", "VinFast", "Trắng"
        if c["room"] == "H201" and k == 0:
            typ, brand, color = "CAR", "Kia", "Bạc"
        active = not (c["id"] % 9 == 0 and k == 1)  # một vài xe đã bán
        vehicles.add(tenant_id=c["tenant"], license_plate=plate(), vehicle_type=typ, brand=brand, color=color, is_active=active,
                     created_at=at(c["start"], 11))
        if active:
            VEH[c["tenant"]] = VEH.get(c["tenant"], 0) + 1

# ================================================================== lịch sử ở, biên bản nhận phòng, cọc
for c in CT:
    stays.add(tenant_id=c["tenant"], room_id=ROOM[c["room"]]["id"], start_date=c["start"],
              end_date=c["end"] if c["status"] == "TERMINATED" else None, checkout_reason=c["term_note"])
    lines = []
    for k, n in enumerate(FURN[c["room"]]):
        if (c["id"] + k) % 13 == 0:
            lines.append(f"✖ {n}: trầy xước nhẹ, đã ghi nhận lúc bàn giao")
        else:
            lines.append(f"✔ {n}" + (" (x2)" if n.startswith("Giường") and ROOM[c["room"]]["kind"] == 1 and c["prop"] == pA else ""))
    c["checkin_lines"] = lines
    c["e0"], c["w0"] = None, None  # điền sau khi biết chỉ số công tơ lúc nhận phòng

# ================================================================== hợp đồng: điều chỉnh, thông báo
for c in CT:
    if c["signed"]:
        adjustments.add(contract_id=c["id"], adjustment_type="SIGNED", old_value=None, new_value="Ký điện tử", adjusted_by=MANAGER,
                        adjusted_at=at(c["start"] - dt.timedelta(days=1), 15))
adjustments.add(contract_id=CUR_CT["H205"]["id"], adjustment_type="PRICE_CHANGE", old_value="3500000", new_value="3400000", adjusted_by=ADMIN,
                adjusted_at=at(D(2026, 3, 1), 10))
adjustments.add(contract_id=CUR_CT["B103"]["id"], adjustment_type="EXTENSION", old_value="2026-08-31", new_value="2027-02-28", adjusted_by=ADMIN,
                adjusted_at=at(D(2026, 8, 10), 10))
adjustments.add(contract_id=CUR_CT["B201"]["id"], adjustment_type="TENANT_CHANGE", old_value="Đỗ Văn Lợi", new_value=TEN[CUR_CT["B201"]["tenant"]]["name"],
                adjusted_by=MANAGER, adjusted_at=at(D(2026, 5, 2), 14))
adjustments.add(contract_id=CUR_CT["H106"]["id"], adjustment_type="PDF_GENERATED", old_value=None, new_value="Bản PDF hợp đồng", adjusted_by=MANAGER,
                adjusted_at=at(D(2025, 7, 30), 9))
for c in CT:
    if c["status"] == "TERMINATED":
        adjustments.add(contract_id=c["id"], adjustment_type="TERMINATED", old_value="ACTIVE", new_value=c["term_note"], adjusted_by=ADMIN,
                        adjusted_at=at(c["end"], 16))
for c in CT:
    if c["status"] == "EXPIRING_SOON":
        c_notifs.add(contract_id=c["id"], notification_type="EXPIRING_WARNING", sent_at=at(max(c["end"] - dt.timedelta(days=30), D(2026, 10, 28)), 8), is_read=False)
    if c["status"] == "EXPIRED":
        c_notifs.add(contract_id=c["id"], notification_type="EXPIRED_NOTICE", sent_at=at(c["end"] + dt.timedelta(days=1), 8), is_read=False)


# ================================================================== trả phòng
def eff_end(c, checkout_by_contract):
    end = c["end"]
    if c["status"] != "TERMINATED" and end < TODAY:
        end = TODAY
    co = checkout_by_contract.get(c["id"])
    if co and co < end:
        end = co
    if c["status"] == "TERMINATED" and c["end"] < end:
        end = c["end"]
    return end


CO = {}  # contract_id -> ngày dùng để chặn thời hạn tính tiền
H303 = HIST_CT["H303" + str(D(2026, 3, 1))]
co_fin = checkouts.add(contract_id=H303["id"], expected_checkout_date=D(2026, 11, 24), actual_checkout_date=D(2026, 11, 24), status="FINALIZED",
                       created_at=at(D(2026, 11, 10), 9))
CO[H303["id"]] = D(2026, 11, 24)
c203, c306, cb105 = CUR_CT["H203"], CUR_CT["H306"], CUR_CT["B105"]
co_pend = checkouts.add(contract_id=c203["id"], expected_checkout_date=D(2026, 12, 5), actual_checkout_date=None, status="PENDING", created_at=at(D(2026, 11, 20), 9))
co_inspecting = checkouts.add(contract_id=c306["id"], expected_checkout_date=D(2026, 11, 30), actual_checkout_date=None, status="INSPECTING", created_at=at(D(2026, 11, 15), 9))
checkouts.add(contract_id=cb105["id"], expected_checkout_date=D(2026, 11, 20), actual_checkout_date=None, status="CANCELLED", created_at=at(D(2026, 11, 5), 9))
CO[c203["id"]] = D(2026, 12, 5)
CO[c306["id"]] = D(2026, 11, 30)

# ================================================================== chỉ số điện nước
SEASON = {6: 1.15, 7: 1.2, 8: 1.2, 9: 1.05, 10: 0.95, 11: 0.9}
BASE_E = {0: 100, 1: 170, 2: 150}
OVERRIDE_E = {("H204", 9): 410, ("H306", 10): 360}
OVERRIDE_W = {("B101", 10): 31}
READ = {}   # (phòng, tháng) -> dict
for code, r in ROOM.items():
    cum_e, cum_w = r["e0"], r["w0"]
    for m in MONTHS:
        covering = [c for c in TENANT_OF_ROOM.get(code, []) if c["start"] <= eom(m) and eff_end(c, CO) >= m]
        if not covering:
            continue
        c = covering[-1]
        s, e = max(c["start"], m), min(eff_end(c, CO), eom(m))
        frac = ((e - s).days + 1) / eom(m).day
        base = BASE_E[r["kind"]] if r["prop"] == pA else (90 if r["kind"] == 0 else 140)
        ue = OVERRIDE_E.get((code, m.month)) or max(8, round(base * SEASON[m.month] * (0.8 + ((r["idx"] * 17 + m.month * 13) % 45) / 100) * frac))
        uw = OVERRIDE_W.get((code, m.month)) or max(1, round((4 + (r["idx"] * 3 + m.month * 5) % 6) * frac))
        READ[(code, m.month)] = dict(room=code, month=m, old_e=cum_e, new_e=cum_e + ue, old_w=cum_w, new_w=cum_w + uw, ue=ue, uw=uw,
                                    contract=c, days=(e - s).days + 1)
        cum_e += ue
        cum_w += uw
for c in CT:  # công tơ lúc nhận phòng = chỉ số cuối kỳ trước đó của phòng
    first = [READ[(c["room"], m.month)] for m in MONTHS if (c["room"], m.month) in READ and READ[(c["room"], m.month)]["contract"] is c]
    c["e0"] = first[0]["old_e"] if first else ROOM[c["room"]]["e0"]
    c["w0"] = first[0]["old_w"] if first else ROOM[c["room"]]["w0"]
    if c["start"] < D(2026, 6, 1):  # nhận phòng trước kỳ dữ liệu: công tơ lúc nhận thấp hơn chỉ số đầu kỳ 6/2026
        c["e0"] = max(0, c["e0"] - 350)
        c["w0"] = max(0, c["w0"] - 25)

period_id = {}
for p in (pA, pB):
    for m in MONTHS:
        closed = not (p == pA and m.month == 11)
        period_id[(p, m.month)] = rperiods.add(property_id=p, period_name=f"Tháng {m.month:02d}/2026", start_date=m, end_date=eom(m), is_closed=closed)
unverified = {("H103", 11), ("H202", 11), ("H307", 11)}
reading_id = {}
for (code, mo), rd in sorted(READ.items(), key=lambda kv: (ROOM[kv[0][0]]["id"], kv[0][1])):
    r = ROOM[code]
    when = at(D(2026, 11, 25), 9, 30) if mo == 11 else at(eom(rd["month"]), 9, 0)
    reading_id[(code, mo)] = readings.add(period_id=period_id[(r["prop"], mo)], room_id=r["id"], old_electric=rd["old_e"], new_electric=rd["new_e"],
                                         old_water=rd["old_w"], new_water=rd["new_w"], reading_date=when, recorded_by_user_id=READER,
                                         is_verified=(code, mo) not in unverified)
for (code, mo), label, pct in ((("H204", 9), "ELECTRIC", 118.5), (("H306", 10), "ELECTRIC", 96.0), (("B101", 10), "WATER", 210.0)):
    rd = READ[(code, mo)]
    anomalies.add(reading_id=reading_id[(code, mo)], utility_type=label, increase_percentage=pct,
                  alert_message=f"Phòng {code}: {'điện' if label == 'ELECTRIC' else 'nước'} tháng {mo} tăng {pct:.0f}% so với trung bình 3 kỳ trước",
                  is_resolved=(code == "H204"))
rd = READ[("H105", 8)]
meter_adj.add(reading_id=reading_id[("H105", 8)], adjusted_field="NEW_ELECTRIC", old_val=rd["new_e"] + 40, new_val=rd["new_e"],
              reason="Ghi nhầm số, đã đối chiếu lại ảnh công tơ", adjusted_by=MANAGER, adjusted_at=at(D(2026, 9, 2), 10))
rd = READ[("B102", 9)]
meter_adj.add(reading_id=reading_id[("B102", 9)], adjusted_field="NEW_WATER", old_val=rd["new_w"] - 6, new_val=rd["new_w"],
              reason="Khách báo số nước thực tế cao hơn, đã kiểm tra lại đồng hồ", adjusted_by=MANAGER, adjusted_at=at(D(2026, 10, 3), 10))


# ================================================================== tính tiền (theo đúng BillingService)
def prorate(rent, days, dim):
    if days >= dim:
        return rent
    return int(rent * days / dim + 0.5)


def res_count(room, m):
    first, last = m, eom(m)
    return sum(1 for x in RES_BY_ROOM.get(room, []) if x["start"] <= last and (x["end"] is None or x["end"] >= first))


# trạng thái hóa đơn: mặc định ĐÃ TRẢ; ngoại lệ ghi rõ theo (phòng, tháng)
EXC = {
    ("H304", 9): "UNPAID", ("H304", 10): "UNPAID", ("B106", 10): "UNPAID", ("H208", 10): "UNPAID", ("H301", 10): "UNPAID",
    ("H307", 10): "PARTIAL", ("B205", 10): "PARTIAL", ("H201", 9): "LATE_PAID", ("H206", 9): "CANCELLED", ("H105", 10): "REVERSED_THEN_PAID",
    ("H108", 10): "UNPAID", ("B204", 10): "UNPAID", ("H202", 10): "PARTIAL",
    ("H102", 10): "PREPAY", ("B101", 10): "LATE_PAID", ("H203", 10): "LATE_PAID", ("H106", 8): "LATE_PAID",
    ("B101", 11): "PAID", ("B102", 11): "PAID", ("B103", 11): "PAID", ("B105", 11): "PARTIAL",
}
for k in [("B106", 11), ("B201", 11), ("B202", 11), ("B204", 11), ("B205", 11)]:
    EXC[k] = "UNPAID_NEW"
METHODS = ["CASH", "BANK_TRANSFER", "QR_CODE", "QR_CODE", "BANK_TRANSFER", "CASH", "GATEWAY"]
vcount = {}


def vcode(d):
    k = d.strftime("%Y%m%d")
    vcount[k] = vcount.get(k, 0) + 1
    return f"PT-{k}-{vcount[k]:04d}"


cp_by = {}
INV = {}  # (phòng, tháng) -> dict
bill_ids = {}
for p in (pA, pB):
    for m in MONTHS:
        if p == pA and m.month == 11:
            continue  # khu Hoa Sen tháng 11: để trống cho bạn tự chạy "Tính tiền tháng"
        bill_ids[(p, m.month)] = bperiods.add(property_id=p, month_year=YM(m), creation_date=at(eom(m) + dt.timedelta(days=1) if m.month < 11 else D(2026, 11, 26), 9),
                                              status="CLOSED" if m.month < 11 else "PUBLISHED")
oldest_unpaid = {}
for m in MONTHS:
    for p in (pA, pB):
        if (p, m.month) not in bill_ids:
            continue
        cands = [c for c in CT if c["prop"] == p and c["start"] <= eom(m) and (c["end"] >= m or c["status"] != "TERMINATED")]
        cands = [c for c in cands if (eff_end(c, CO) - max(c["start"], m)).days + 1 > 0 and min(eff_end(c, CO), eom(m)) >= max(c["start"], m)]
        cands.sort(key=lambda c: (c["room"], c["start"]))
        issue = eom(m) + dt.timedelta(days=1) if m.month < 11 else D(2026, 11, 26)
        elec_p, water_p = PLAN(m)
        for c in cands:
            code = c["room"]
            s, e = max(c["start"], m), min(eff_end(c, CO), eom(m))
            days = (e - s).days + 1
            lines = [("Tiền phòng", 1, prorate(c["rent"], days, eom(m).day))]
            rd = READ.get((code, m.month))
            if rd and rd["contract"] is c:
                lines += [("Điện", rd["ue"], elec_p), ("Nước", rd["uw"], water_p)]
            people = 1 + res_count(code, m)
            for n, kind, fn in SERV[p]:
                if kind == "FIXED_ROOM":
                    lines.append((n, 1, fn(m)))
                elif kind == "PER_PERSON":
                    lines.append((n, people, fn(m)))
                elif VEH.get(c["tenant"], 0) > 0:
                    lines.append((n, VEH[c["tenant"]], fn(m)))
            for room, n, price, _ in CUSTOM:
                if room == code:
                    lines.append((("Phụ thu: " if price > 0 else "Giảm trừ: ") + n, 1, price))
            if (code, m.month) == ("H304", 10):
                lines.append(("Phạt trễ hạn", 27, c["late"]))  # Hóa đơn tháng 9 chưa trả, quá hạn 27 ngày tính đến lúc lập kỳ
            for room, n, amt, st in ADDON:
                if room == code and st <= eom(m):
                    lines.append(("Thiết bị: " + n, 1, amt))
            rent = elec = water = svc = sur = dis = pen = 0
            for n, qn, up in lines:
                sub = qn * up
                if n == "Tiền phòng":
                    rent += sub
                elif n == "Điện":
                    elec += sub
                elif n == "Nước":
                    water += sub
                elif n.startswith("Phụ thu"):
                    sur += sub
                elif n.startswith("Giảm trừ"):
                    dis += -sub
                elif n == "Phạt trễ hạn":
                    pen += sub
                else:
                    svc += sub
            total = rent + elec + water + svc + sur - dis + pen
            fid = fees.add(billing_period_id=bill_ids[(p, m.month)], room_id=ROOM[code]["id"], contract_id=c["id"], rent_amount=rent, electric_amount=elec,
                           water_amount=water, service_amount=svc, surcharge_amount=sur, discount_amount=dis, late_penalty_amount=pen,
                           total_calculated_amount=total)
            for n, qn, up in lines:
                items.add(calculated_fee_id=fid, item_name=n, quantity=qn, unit_price=up, subtotal=qn * up)
            due_day = min(c["due"], 28)
            due = issue.replace(day=due_day) if m.month < 11 else D(2026, 12, c["due"])
            exc = EXC.get((code, m.month), "PAID")
            invoice_code = f"INV-{m.strftime('%Y%m')}-{code}-{fid}"
            paid = {"PAID": total, "LATE_PAID": total, "PREPAY": total, "REVERSED_THEN_PAID": total, "UNPAID": 0, "UNPAID_NEW": 0, "CANCELLED": 0,
                    "PARTIAL": (total // 2 // 10000) * 10000}[exc]
            if exc == "CANCELLED":
                status = "CANCELLED"
            elif paid >= total:
                status = "PAID"
            elif exc == "PARTIAL":
                status = "PARTIALLY_PAID"
            elif exc == "UNPAID_NEW":
                status = "UNPAID"
            else:
                status = "OVERDUE"
            iid = invoices.add(invoice_code=invoice_code, calculated_fee_id=fid, tenant_id=c["tenant"], issue_date=issue, due_date=due, total_amount=total,
                               paid_amount=paid, payment_status=status, share_link="/api/invoices/share/" + str(uuid.UUID(int=R.getrandbits(128))))
            inv = dict(id=iid, code=invoice_code, room=code, month=m, tenant=c["tenant"], total=total, paid=paid, status=status, issue=issue, due=due,
                       exc=exc, contract=c, fee=fid)
            INV[(code, m.month, c["id"])] = inv
            inv_notifs.add(invoice_id=iid, channel="SYSTEM_APP", sent_at=at(issue, 9, 5), status="SENT")
            if iid % 3 == 0:
                inv_notifs.add(invoice_id=iid, channel="EMAIL", sent_at=at(issue, 9, 6), status="SENT")
            # giao dịch thanh toán
            def pay(amount, when, method, status_="SUCCESS", tag="", iid=iid, code=code, m=m):
                if when.date() > TODAY:
                    when = at(TODAY, 8, 15)
                code_ = f"GD{when.strftime('%y%m%d')}{iid:04d}{tag}"
                tid_ = txns.add(invoice_id=iid, payment_method=method, transaction_code=code_, amount_paid=amount, payment_date=when,
                                received_by_user_id=CASHIER if method in ("CASH",) else MANAGER, status=status_)
                if status_ == "SUCCESS":
                    vouchers.add(voucher_code=vcode(when.date()), transaction_id=tid_, voucher_type="MONTHLY_RENT", amount=amount,
                                 note=f"Thu tiền phòng {code} tháng {m.month:02d}/2026", created_at=when)
                return tid_
            method = METHODS[iid % len(METHODS)]
            if exc in ("PAID", "UNPAID_NEW") and paid > 0:
                pay(paid, at(issue + dt.timedelta(days=1 + iid % 4), 10 + iid % 6, iid % 50), method)
            elif exc == "LATE_PAID":
                pay(paid, at(due + dt.timedelta(days=3 + iid % 5), 14, 20), method)
            elif exc == "PREPAY":
                pay(total - 300000, at(issue + dt.timedelta(days=2), 10), "CASH")
            elif exc == "REVERSED_THEN_PAID":
                pay(total, at(issue + dt.timedelta(days=1), 9, 40), "BANK_TRANSFER", "REVERSED", "R")
                pay(total, at(issue + dt.timedelta(days=2), 15, 10), "CASH")
            elif exc == "PARTIAL":
                first = (paid // 2 // 10000) * 10000
                pay(first, at(due - dt.timedelta(days=2), 9, 30), "BANK_TRANSFER", tag="A")
                partials.add(invoice_id=iid, installment_number=1, amount=first, paid_date=at(due - dt.timedelta(days=2), 9, 30))
                if paid - first > 0:
                    pay(paid - first, at(due + dt.timedelta(days=1), 16), "CASH", tag="B")
                    partials.add(invoice_id=iid, installment_number=2, amount=paid - first, paid_date=at(due + dt.timedelta(days=1), 16))
            elif exc == "UNPAID" and (code, m.month) == ("H301", 10):
                pay(total, at(D(2026, 11, 26), 20, 15), "QR_CODE", "FAILED", "F")  # QR khách quét nhưng chưa khớp, chờ xác nhận

# ---- tiền cọc: phiếu thu DEPOSIT cho mọi hợp đồng (hợp đồng mới H107 mới thu một nửa)
for c in CT:
    amt = c["rent"] // 2 if c["room"] == "H107" else c["rent"]
    vouchers.add(voucher_code=vcode((c["start"] - dt.timedelta(days=1))), transaction_id=None, voucher_type="DEPOSIT", amount=amt,
                 note=f"Thu cọc hợp đồng {c['code']} phòng {c['room']}", created_at=at(c["start"] - dt.timedelta(days=1), 15))

# ---- trả phòng: biên bản kiểm tra (chỉ số công tơ cuối lấy từ kỳ tháng 11), hoàn cọc
last_h303 = READ[("H303", 11)]
CO_INSP_ROWS = [
    dict(checkout_id=co_fin, final_electric_meter=last_h303["new_e"], final_water_meter=last_h303["new_w"],
         damaged_equipments_description="Tủ quần áo gãy bản lề: 200000\nMáy lạnh chưa vệ sinh: 150000", damage_compensation_amount=350000,
         unpaid_utilities_amount=0, inspected_by=MANAGER, inspected_at=at(D(2026, 11, 24), 15)),
    dict(checkout_id=co_inspecting, final_electric_meter=READ[("H306", 11)]["new_e"], final_water_meter=READ[("H306", 11)]["new_w"],
         damaged_equipments_description=None, damage_compensation_amount=0, unpaid_utilities_amount=0, inspected_by=MANAGER,
         inspected_at=at(D(2026, 11, 27), 10)),
]
refund_amt = H303["rent"] - 350000
refunds.add(checkout_id=co_fin, original_deposit_amount=H303["rent"], deducted_debt_amount=0, deducted_damage_amount=350000,
            actual_refund_amount=refund_amt, refund_date=at(D(2026, 11, 26), 11), status="REFUNDED")
refunds.add(checkout_id=co_inspecting, original_deposit_amount=c306["rent"], deducted_debt_amount=0, deducted_damage_amount=0, actual_refund_amount=c306["rent"],
            refund_date=None, status="PENDING")

# ================================================================== công nợ
today_days = lambda due: (TODAY - due).days


def debt_status(days):
    return "NORMAL" if days <= 0 else ("LONG_TERM_DEBT" if days >= 30 else "WARNING_OVERDUE")


debt_by_inv = {}
for key, inv in sorted(INV.items(), key=lambda kv: kv[1]["id"]):
    code, mo, _ = key
    if (code, mo) == ("H304", 9):
        continue  # tháng 9 của H304 đã gộp vào dòng nợ tháng 10 (nợ kéo dài)
    if inv["status"] in ("OVERDUE", "PARTIALLY_PAID", "UNPAID") and inv["total"] > inv["paid"] and inv["due"] < TODAY:
        rem = inv["total"] - inv["paid"]
        days = today_days(inv["due"])
        opening = 0
        cur = rem
        if (code, mo) == ("H304", 10):  # nợ kéo dài: cộng cả hóa đơn tháng 9 chưa trả
            sep = INV[("H304", 9, inv["contract"]["id"])]
            opening = sep["total"] - sep["paid"]
            days = today_days(sep["due"])
        did = debts.add(tenant_id=inv["tenant"], room_id=ROOM[code]["id"], invoice_id=inv["id"], opening_debt_balance=opening, current_period_debt=cur,
                        total_outstanding_debt=opening + cur, overdue_days=days, debt_status=debt_status(days), updated_at=at(TODAY, 7))
        debt_by_inv[inv["id"]] = (did, days, inv)
        late = inv["contract"]["late"]
        penalties.add(debt_id=did, overdue_days=days, daily_penalty_rate=late, calculated_penalty_amount=days * late, created_at=at(TODAY, 7, 5))
        msgs = (("FIRST_REMINDER", 1, f"Phòng {code} còn khoản chưa thanh toán, đã quá hạn. Bạn vui lòng thanh toán sớm nhé."),
                ("SECOND_REMINDER", 7, f"Nhắc lần 2: khoản nợ phòng {code} đã quá hạn {days} ngày. Mong bạn thanh toán trong tuần này."),
                ("FINAL_WARNING", 15, f"Cảnh báo cuối cùng: phòng {code} nợ quá hạn {days} ngày. Chủ trọ có thể xem xét chấm dứt hợp đồng."))
        for level, th, text in msgs:
            if days >= th:
                reminders.add(debt_id=did, reminder_level=level, message_content=text, sent_at=at(inv["due"] + dt.timedelta(days=th), 8, 30))
# khoản nợ đã thanh toán xong (trả muộn)
late_inv = INV[("H201", 9, CUR_CT["H201"]["id"])]
debts.add(tenant_id=late_inv["tenant"], room_id=ROOM["H201"]["id"], invoice_id=late_inv["id"], opening_debt_balance=0, current_period_debt=0,
          total_outstanding_debt=0, overdue_days=0, debt_status="CLEARED", updated_at=at(late_inv["due"] + dt.timedelta(days=8), 14))
proc_logs.add(debt_id=debt_by_inv[INV[("B106", 10, CUR_CT["B106"]["id"])]["id"]][0], promised_payment_date=D(2026, 11, 30),
              delay_reason="Chờ lương cuối tháng", staff_note="Khách hẹn trả đủ vào 30/11, đã nhắn xác nhận", recorded_by=CASHIER, recorded_at=at(D(2026, 11, 20), 10))
proc_logs.add(debt_id=debt_by_inv[INV[("H307", 10, CUR_CT["H307"]["id"])]["id"]][0], promised_payment_date=D(2026, 12, 3),
              delay_reason="Con nhỏ ốm, xin gia hạn", staff_note="Đồng ý cho trả phần còn lại trước 3/12", recorded_by=MANAGER, recorded_at=at(D(2026, 11, 24), 15))
# tiền trả trước và cấn trừ
h102 = INV[("H102", 10, CUR_CT["H102"]["id"])]
pp1 = prepay.add(tenant_id=h102["tenant"], excess_balance=200000, updated_at=at(D(2026, 11, 3), 10))
prepay_ded.add(prepayment_id=pp1, target_invoice_id=h102["id"], deducted_amount=300000, deducted_at=at(D(2026, 11, 3), 10))
prepay.add(tenant_id=CUR_CT["B103"]["tenant"], excess_balance=450000, updated_at=at(D(2026, 11, 10), 9))
prepay.add(tenant_id=CUR_CT["H205"]["tenant"], excess_balance=0, updated_at=at(D(2026, 10, 5), 9))

# ================================================================== đặt phòng
guest_hold = None
b1 = bookings.add(room_id=ROOM["H207"]["id"], tenant_id=PROSPECT[0], expected_start_date=D(2026, 12, 5), occupants_count=1, deposit_paid=0, status="PENDING",
                  rejection_reason=None, created_at=at(D(2026, 11, 24), 10), guest_name=None, guest_phone=None, hold_until=None)
b2 = bookings.add(room_id=ROOM["H308"]["id"], tenant_id=PROSPECT[1], expected_start_date=D(2026, 12, 10), occupants_count=2, deposit_paid=1000000, status="APPROVED",
                  rejection_reason=None, created_at=at(D(2026, 11, 22), 14), guest_name=None, guest_phone=None, hold_until=at(D(2026, 12, 3), 18))
bookings.add(room_id=ROOM["B104"]["id"], tenant_id=PROSPECT[2], expected_start_date=D(2026, 12, 1), occupants_count=1, deposit_paid=0, status="PENDING",
             rejection_reason=None, created_at=at(D(2026, 11, 25), 9), guest_name=None, guest_phone=None, hold_until=None)
bookings.add(room_id=ROOM["B206"]["id"], tenant_id=None, expected_start_date=D(2026, 12, 15), occupants_count=2, deposit_paid=0, status="PENDING",
             rejection_reason=None, created_at=at(D(2026, 11, 26), 20), guest_name="Hoàng Thu Trang", guest_phone="0911223344", hold_until=None)
bookings.add(room_id=ROOM["H104"]["id"], tenant_id=PROSPECT[3], expected_start_date=D(2026, 11, 20), occupants_count=1, deposit_paid=0, status="REJECTED",
             rejection_reason="Phòng đã có người đặt trước", created_at=at(D(2026, 11, 12), 9), guest_name=None, guest_phone=None, hold_until=None)
bookings.add(room_id=ROOM["H104"]["id"], tenant_id=PROSPECT[4], expected_start_date=D(2026, 11, 25), occupants_count=2, deposit_paid=0, status="CANCELLED",
             rejection_reason=None, created_at=at(D(2026, 11, 8), 9), guest_name=None, guest_phone=None, hold_until=None)
bookings.add(room_id=ROOM["B104"]["id"], tenant_id=None, expected_start_date=D(2026, 12, 8), occupants_count=1, deposit_paid=0, status="PENDING",
             rejection_reason=None, created_at=at(D(2026, 11, 27), 8), guest_name="Lê Quang Huy", guest_phone="0933445566", hold_until=None)
b8 = bookings.add(room_id=ROOM["H207"]["id"], tenant_id=PROSPECT[5], expected_start_date=D(2026, 12, 1), occupants_count=1, deposit_paid=500000, status="APPROVED",
                  rejection_reason=None, created_at=at(D(2026, 11, 18), 11), guest_name=None, guest_phone=None, hold_until=at(D(2026, 12, 1), 18))
HELD = {ROOM["H308"]["id"]: b2, ROOM["H207"]["id"]: b8}

# ================================================================== sự cố, thiết bị
CAT = {"ELECTRIC_ISSUE": "ELECTRIC_ISSUE", "WATER_ISSUE": "WATER_ISSUE", "AIR_CONDITIONER": "AIR_CONDITIONER", "DOOR_LOCK": "DOOR_LOCK",
       "INTERNET": "INTERNET", "OTHER_INCIDENT": "OTHER_INCIDENT"}
TK = [  # phòng, nhóm, mô tả, ưu tiên, trạng thái, ngày tạo, chi phí, điểm, ghi chú xử lý, ngày xong
    ("H105", "AIR_CONDITIONER", "Máy lạnh không mát, chảy nước ra sàn", "NORMAL", "CLOSED", D(2026, 9, 3), 350000, 5, "Đã nạp gas và vệ sinh dàn lạnh", D(2026, 9, 5)),
    ("H201", "WATER_ISSUE", "Vòi sen yếu, nước chảy nhỏ giọt", "NORMAL", "CLOSED", D(2026, 8, 12), 120000, 4, "Thay đầu vòi sen mới", D(2026, 8, 13)),
    ("B101", "WATER_ISSUE", "Nghi rò nước ở ống sau bồn rửa, đồng hồ quay liên tục", "URGENT", "RESOLVED", D(2026, 11, 10), 480000, None,
     "Thay ống PVC và van khóa, đã kiểm tra lại đồng hồ", D(2026, 11, 12)),
    ("H204", "ELECTRIC_ISSUE", "Hay nhảy aptomat khi bật máy lạnh và bếp cùng lúc", "URGENT", "IN_PROGRESS", D(2026, 11, 20), 0, None, "Đang chờ thợ điện đo tải", None),
    ("H306", "DOOR_LOCK", "Ổ khóa cửa chính kẹt, khó mở", "NORMAL", "ASSIGNED", D(2026, 11, 24), 0, None, None, None),
    ("H302", "INTERNET", "Wifi chập chờn buổi tối", "LOW", "OPEN", D(2026, 11, 25), 0, None, None, None),
    ("B202", "OTHER_INCIDENT", "Cửa sổ phòng tắm bị kẹt, không đóng kín", "NORMAL", "OPEN", D(2026, 11, 26), 0, None, None, None),
    ("H103", "AIR_CONDITIONER", "Máy lạnh kêu to khi chạy", "LOW", "OPEN", D(2026, 11, 27), 0, None, None, None),
    ("H301", "ELECTRIC_ISSUE", "Ổ cắm bên giường bị lỏng, có tia lửa", "URGENT", "IN_PROGRESS", D(2026, 11, 26), 0, None, "Đã ngắt điện ổ cắm, chờ thay", None),
    ("B105", "WATER_ISSUE", "Bồn cầu xả nước chậm", "NORMAL", "CLOSED", D(2026, 10, 8), 200000, 4, "Thông tắc, thay phao", D(2026, 10, 9)),
    ("H108", "OTHER_INCIDENT", "Tường ẩm mốc góc phòng ngủ", "NORMAL", "RESOLVED", D(2026, 11, 1), 650000, None, "Chống thấm và sơn lại, chờ khách xác nhận", D(2026, 11, 6)),
    ("H205", "INTERNET", "Mất mạng cả ngày", "NORMAL", "CLOSED", D(2026, 7, 21), 0, 5, "Nhà mạng đã khôi phục đường truyền", D(2026, 7, 21)),
]
for room, cat, desc, pri, st, created, cost, rating, notes, done in TK:
    ct = CUR_CT[room]
    rid = tickets.add(room_id=ROOM[room]["id"], tenant_id=ct["tenant"], category=cat, description=desc, priority=pri, status=st, created_at=at(created, 8, 30))
    if st != "OPEN":
        m_logs.add(request_id=rid, assigned_staff_id=MANAGER, repair_cost=cost, tenant_rating=rating, resolution_notes=notes,
                   completed_at=at(done, 16) if done and st in ("CLOSED", "RESOLVED") else None)
EQ = [
    ("H103", "EQ_WASHER", "Máy giặt", "INSTALL", 80000, "INSTALLED", "Đã lắp ngày 1/9, tính phụ phí hằng tháng", D(2026, 8, 25)),
    ("B201", "EQ_FRIDGE", "Tủ lạnh thêm", "EXTRA_USAGE", 60000, "INSTALLED", "Khách tự mang tủ lạnh, tính phí điện", D(2026, 7, 28)),
    ("H204", "EQ_AIR_CONDITIONER", "Máy lạnh thêm", "INSTALL", 150000, "INSTALLED", "Đã lắp ngày 1/10", D(2026, 9, 20)),
    ("H301", "EQ_WATER_HEATER", "Bình nước nóng", "INSTALL", 70000, "PENDING", None, D(2026, 11, 25)),
    ("B106", "EQ_FRIDGE", "Tủ lạnh 150L", "EXTRA_USAGE", 60000, "APPROVED", "Đồng ý, chờ khách mang vào phòng", D(2026, 11, 22)),
    ("H307", "EQ_OTHER", "Bếp nướng điện", "EXTRA_USAGE", 90000, "REJECTED", "Không cho phép: nguy cơ cháy nổ trong phòng", D(2026, 11, 18)),
    ("H108", "EQ_WASHER", "Máy giặt cũ", "INSTALL", 80000, "REMOVED", "Khách đã chuyển máy giặt đi", D(2026, 6, 10)),
    ("B102", "EQ_OTHER", "Quạt hơi nước", "EXTRA_USAGE", 40000, "PENDING", None, D(2026, 11, 26)),
]
eq_ids = {}
for room, cat, name, typ, fee, st, note, created in EQ:
    eq_ids[(room, name)] = eq_req.add(room_id=ROOM[room]["id"], tenant_id=CUR_CT[room]["tenant"],
                                      category_id=Raw(f"(SELECT [category_id] FROM [incident_categories] WHERE [category_code] = '{cat}')"), equipment_name=name, request_type=typ,
                                      estimated_monthly_fee=fee, status=st, reviewed_by=None if st == "PENDING" else MANAGER, note=note, created_at=at(created, 9))
for room, name, amt, start in ADDON:
    addons.add(room_id=ROOM[room]["id"], equipment_request_id=eq_ids[(room, name)], fee_name=name, amount=amt, billing_cycle_months=1, start_date=start,
               end_date=None, is_active=True)
addons.add(room_id=ROOM["H108"]["id"], equipment_request_id=eq_ids[("H108", "Máy giặt cũ")], fee_name="Máy giặt", amount=80000, billing_cycle_months=1,
           start_date=D(2026, 6, 10), end_date=D(2026, 9, 30), is_active=False)

# ================================================================== chi phí vận hành, ngân hàng
EXP = [("Bảo trì", "Bảo dưỡng máy lạnh, kiểm tra điện nước", 1800000), ("Vệ sinh", "Thuê dọn vệ sinh khu chung", 1200000),
       ("Lương bảo vệ", "Lương bảo vệ tháng", 5500000), ("Thuế và phí", "Phí vệ sinh môi trường, phí PCCC", 650000)]
for m in MONTHS:
    for p in (pA, pB):
        scale = 1.0 if p == pA else 0.6
        for i, (cat, desc, amt) in enumerate(EXP):
            if cat == "Bảo trì" and m.month % 2 == 1 and p == pB:
                continue
            expenses.add(property_id=p, expense_category=cat, description=f"{desc} {m.month:02d}/2026", amount=int(amt * scale / 10000) * 10000,
                         expense_date=m.replace(day=min(26, 5 + i * 6)), recorded_by=ADMIN, created_at=at(m.replace(day=min(26, 5 + i * 6)), 17))
expenses.add(property_id=pA, expense_category="Điện nước chung", description="Điện nước khu vực chung tháng 11/2026", amount=1350000, expense_date=D(2026, 11, 26),
             recorded_by=ADMIN, created_at=at(D(2026, 11, 26), 17))
expenses.add(property_id=pA, expense_category="Dịch vụ chung", description="Thuê trông xe và vệ sinh cầu thang tháng 11/2026", amount=600000,
             expense_date=D(2026, 11, 26), recorded_by=ADMIN, created_at=at(D(2026, 11, 26), 17))
expenses.add(property_id=pA, expense_category="Bảo trì", description="Thay bóng đèn hành lang và sơn lại cầu thang", amount=2400000, expense_date=D(2026, 11, 15),
             recorded_by=MANAGER, created_at=at(D(2026, 11, 15), 17))
banks.add(property_id=pA, bank_name="Vietcombank", account_number="0071001234567", account_holder="NGUYEN VAN CHU TRO", is_default=True, is_active=True)
banks.add(property_id=pA, bank_name="MB Bank", account_number="0988123456789", account_holder="NGUYEN VAN CHU TRO", is_default=False, is_active=True)
banks.add(property_id=pB, bank_name="Techcombank", account_number="19036789012345", account_holder="NGUYEN VAN CHU TRO", is_default=True, is_active=True)
banks.add(property_id=pB, bank_name="ACB", account_number="123456789", account_holder="NGUYEN VAN CHU TRO", is_default=False, is_active=False)

# ================================================================== tài khoản người thuê (cùng mật khẩu Admin@123)
ACC = [("H102", 0), ("H103", 0), ("H105", 0), ("H106", 0), ("H201", 0), ("H204", 0), ("H301", 0), ("H304", 0), ("B101", 0), ("B103", 0), ("B106", 0), ("B201", 0),
       ("H302", 1), ("H107", 1)]
TENANT_USER = {}
for room, must in ACC:
    c = CUR_CT[room]
    tn = tenants.rows[c["tenant"] - 1]
    uid = users.add(email=None, phone_number=tn["phone_number"], password_hash=PWD_HASH_SQL, full_name=tn["full_name"], status="ACTIVE",
                    must_change_password=bool(must), created_at=at(c["start"], 12), updated_at=at(c["start"], 12))
    role_rows.append((uid, "TENANT"))
    TENANT_USER[c["tenant"]] = uid
old = HIST_CT["H104" + str(D(2026, 1, 1))]
tn = tenants.rows[old["tenant"] - 1]
uid = users.add(email=None, phone_number=tn["phone_number"], password_hash=PWD_HASH_SQL, full_name=tn["full_name"], status="LOCKED",
                must_change_password=False, created_at=at(old["start"], 12), updated_at=at(old["end"], 12))
role_rows.append((uid, "TENANT"))

# ================================================================== mẫu thông báo, thông báo trên chuông, nhật ký
TPL = [("INVOICE_ISSUED", "EMAIL", "Hóa đơn {{invoice}} đã phát hành", "Chào bạn, hóa đơn {{invoice}} có tổng {{amount}} đ, hạn thanh toán {{dueDate}}. Bạn có thể thanh toán bằng tiền mặt hoặc quét mã QR. Cảm ơn bạn."),
       ("INVOICE_ADJUSTED", "SYSTEM_APP", "Hóa đơn của bạn đã được điều chỉnh", "Hóa đơn {{invoice}} vừa được điều chỉnh. Ghi chú: {{note}}. Vui lòng kiểm tra lại số tiền cần thanh toán."),
       ("DEBT_REMINDER_1", "SYSTEM_APP", "Nhắc thanh toán phòng {{room}}", "Phòng {{room}} còn khoản chưa thanh toán {{amount}}, đã quá hạn {{overdueDays}} ngày. Bạn vui lòng thanh toán sớm nhé."),
       ("DEBT_REMINDER_2", "SYSTEM_APP", "Nhắc thanh toán lần 2, phòng {{room}}", "Khoản nợ {{amount}} của phòng {{room}} đã quá hạn {{overdueDays}} ngày. Mong bạn thanh toán trong tuần này để tránh phát sinh phí chậm trả."),
       ("DEBT_FINAL_WARNING", "SYSTEM_APP", "Cảnh báo cuối cùng, phòng {{room}}", "Phòng {{room}} nợ {{amount}}, quá hạn {{overdueDays}} ngày. Nếu không thanh toán, chủ trọ có thể xem xét chấm dứt hợp đồng theo điều khoản đã ký."),
       ("CONTRACT_EXPIRING", "SYSTEM_APP", "Hợp đồng {{contract}} sắp hết hạn", "Hợp đồng {{contract}} phòng {{room}} sẽ hết hạn ngày {{endDate}}. Nếu muốn gia hạn, bạn vui lòng liên hệ chủ trọ."),
       ("CONTRACT_EXPIRED", "SYSTEM_APP", "Hợp đồng {{contract}} đã hết hạn", "Hợp đồng {{contract}} phòng {{room}} đã hết hạn ngày {{endDate}}. Vui lòng liên hệ chủ trọ để gia hạn hoặc làm thủ tục trả phòng.")]
TPLID = {}
for code, ch, sub, body in TPL:
    TPLID[code] = notif_tpl.add(template_code=code, channel=ch, subject=sub, content=body, is_active=True)
for (code, mo, _), inv in INV.items():
    if inv["status"] in ("OVERDUE",) and inv["tenant"] in TENANT_USER:
        user_notifs.add(user_id=TENANT_USER[inv["tenant"]], notif_template_id=TPLID["DEBT_REMINDER_1"], title=f"Nhắc thanh toán phòng {code}",
                        content=f"Hóa đơn {inv['code']} đã quá hạn. Bạn vui lòng thanh toán sớm.", reference_type="INVOICE", reference_id=inv["id"], is_read=False,
                        created_at=at(inv["due"] + dt.timedelta(days=1), 8))
    if inv["month"].month in (10, 11) and inv["tenant"] in TENANT_USER:
        user_notifs.add(user_id=TENANT_USER[inv["tenant"]], notif_template_id=TPLID["INVOICE_ISSUED"], title=f"Hóa đơn {inv['code']} đã phát hành",
                        content=f"Tổng {inv['total']:,} đ, hạn thanh toán {inv['due'].strftime('%d/%m/%Y')}.".replace(",", "."), reference_type="INVOICE",
                        reference_id=inv["id"], is_read=inv["status"] == "PAID", created_at=at(inv["issue"], 9, 10))
for c in CT:
    if c["status"] == "EXPIRING_SOON" and c["tenant"] in TENANT_USER:
        user_notifs.add(user_id=TENANT_USER[c["tenant"]], notif_template_id=TPLID["CONTRACT_EXPIRING"], title=f"Hợp đồng {c['code']} sắp hết hạn",
                        content=f"Hợp đồng phòng {c['room']} hết hạn ngày {c['end'].strftime('%d/%m/%Y')}.", reference_type="CONTRACT", reference_id=c["id"],
                        is_read=False, created_at=at(max(c["end"] - dt.timedelta(days=30), D(2026, 10, 28)), 8))
for uid in (ADMIN, MANAGER):
    for title, content, rtype, rid, days in (
            ("Hợp đồng sắp hết hạn", "Có 5 hợp đồng hết hạn trong 30 ngày tới.", "CONTRACT", CUR_CT["H102"]["id"], 3),
            ("Công nợ quá hạn", "Phòng H304 nợ kéo dài hơn 50 ngày, cần xử lý.", "DEBT", debt_by_inv[INV[("H304", 10, CUR_CT["H304"]["id"])]["id"]][0], 1),
            ("Sự cố khẩn cấp", "Phòng H301 báo ổ cắm có tia lửa.", "MAINTENANCE", 9, 1),
            ("Yêu cầu trả phòng mới", "Phòng H203 gửi yêu cầu trả phòng ngày 05/12.", "CHECKOUT", co_pend, 7)):
        user_notifs.add(user_id=uid, notif_template_id=None, title=title, content=content, reference_type=rtype, reference_id=rid, is_read=days > 5,
                        created_at=at(TODAY - dt.timedelta(days=days), 8, 30))
LOGIN_IPS = ["113.161.20.11", "27.72.98.44", "42.114.7.130"]
for i in range(40):
    d = TODAY - dt.timedelta(days=i // 2)
    uid = [ADMIN, MANAGER, CASHIER, READER][i % 4]
    act, desc = [("LOGIN", "Đăng nhập thành công"), ("CREATE_INVOICE", "Phát hành hóa đơn tháng"), ("UPDATE_METER", "Nhập chỉ số điện nước"),
                 ("COLLECT_PAYMENT", "Ghi nhận thu tiền hóa đơn")][i % 4]
    logs.add(user_id=uid, action_type=act, description=desc, ip_address=LOGIN_IPS[i % 3], created_at=at(d, 7 + i % 12, (i * 7) % 60))

# ================================================================== trạng thái phòng giữ chỗ
HELD_SQL = "\n".join(f"UPDATE [rooms] SET [held_booking_id] = {b} WHERE [room_id] = {r};" for r, b in HELD.items())

# ================================================================== phát SQL
ORDER = [users, properties, floors, room_types, rooms, furn, templates, tenants, bookings, contracts, adjustments, c_notifs, residents, stays, vehicles,
         checkins, plans, svc_rates, rate_hist, custom_rates, rperiods, readings, meter_adj, anomalies, bperiods, fees, items, invoices, inv_notifs, txns,
         vouchers, partials, prepay, prepay_ded, debts, penalties, reminders, proc_logs, checkouts, co_insp, refunds, tickets, m_logs, eq_req, addons,
         expenses, banks, notif_tpl, user_notifs, logs]

# điền các bảng còn dang dở (checkin_inspections, checkout_inspections cần biết chỉ số công tơ)
for c in CT:
    checkins.add(room_id=ROOM[c["room"]]["id"], tenant_id=c["tenant"], contract_id=c["id"], checkin_date=c["start"], initial_electric_meter=c["e0"],
                 initial_water_meter=c["w0"], key_handed_count=2, furnishings_condition="\n".join(c["checkin_lines"]),
                 notes="Tường sạch, sàn không hư hỏng" if c["id"] % 4 else "Có một vết ố nhỏ ở góc trần, đã chụp ảnh lưu lại",
                 tenant_confirmed=True, inspected_by_name="Lê Thị Thu Hà", created_at=at(c["start"], 10))
for cols in CO_INSP_ROWS:
    co_insp.add(**cols)

header = f"""-- ============================================================================
-- DỮ LIỆU GIẢ ĐỊNH DÀY ĐỂ TEST (tự sinh bằng db/tools/gen_sample_data.py, đừng sửa tay).
-- Chạy SAU group-7.sql và seed.sql, trên DATABASE TRỐNG (chưa có khu trọ, khách, hợp đồng): nếu đã có dữ liệu thì tự bỏ qua.
-- Mốc thời gian: hôm nay là 27/11/2026, hóa đơn từ tháng 06 đến 11/2026.
--
-- 2 khu trọ: Hoa Sen (24 phòng, 3 tầng) và Bình Minh (12 phòng, 2 tầng). {len(ROOM)} phòng, {len(tenants.rows)} khách, {len(contracts.rows)} hợp đồng
-- (ACTIVE / EXPIRING_SOON / EXPIRED / TERMINATED), {len(invoices.rows)} hóa đơn đủ trạng thái (đã trả, trả một phần, quá hạn, hủy, trả muộn,
-- giao dịch bị hoàn tác và giao dịch chờ xác nhận), công nợ (bình thường, quá hạn, nợ kéo dài, đã xong), nhắc nợ, cấn trừ trả trước,
-- sự cố, yêu cầu thiết bị + phụ phí, đặt phòng và giữ chỗ, yêu cầu trả phòng đủ 4 trạng thái, người ở cùng (đã/chưa khai tạm trú),
-- biên bản nhận phòng, chi phí vận hành, thông báo, nhật ký.
--
-- TÀI KHOẢN (mật khẩu chung Admin@123):
--   admin@minihouse.local   Chủ nhà (toàn quyền)
--   quanly@minihouse.local  Quản lý        thungan@minihouse.local  Thu ngân        ghidien@minihouse.local  Ghi điện nước
--   quanly2@minihouse.local bị KHÓA        nvcu@minihouse.local     chưa kích hoạt
--   Người thuê đăng nhập bằng SĐT: {', '.join(tenants.rows[CUR_CT[r]['tenant'] - 1]['phone_number'] + ' (' + r + ')' for r in ('H102', 'H106', 'H304', 'B106'))}
--   (còn 8 khách khác có tài khoản, và 1 tài khoản người thuê cũ bị khóa; SĐT {tenants.rows[CUR_CT['H302']['tenant'] - 1]['phone_number']} (H302) và {tenants.rows[CUR_CT['H107']['tenant'] - 1]['phone_number']} (H107) đang dùng mật khẩu tạm, bắt đổi lần đầu)
--
-- TÌNH HUỐNG ĐÁNG CHÚ Ý:
--   Hoa Sen tháng 11: đã có chỉ số điện nước nhưng CHƯA tính tiền -> tự làm 5 bước "Tính tiền tháng" (3 phòng chỉ số chưa xác nhận, có điện nước chung cần phân bổ).
--   Bình Minh tháng 11: đã phát hành hóa đơn (3 đã trả, 1 trả một phần, 5 chưa trả).
--   H304: nợ kéo dài (tháng 9 + 10), có phạt trễ hạn và đủ 3 mức nhắc nợ.   H105: giao dịch bị hoàn tác rồi thu lại.
--   H301: hóa đơn có giao dịch QR chờ xác nhận.   H102: cấn trừ tiền trả trước.   H206: hóa đơn tháng 9 đã hủy.
--   H303: vừa trả phòng xong (hoàn cọc đã chi, phòng chờ dọn).   H306: đang kiểm tra trả phòng.   H203: chờ xác nhận trả phòng.
--   H101, B101: hợp đồng hết hạn khách vẫn ở.   H102, H201, H208, H305, B202: sắp hết hạn.   người ở cùng quá hạn khai tạm trú: H108, H201, H204, H306; sắp tới hạn: H304.
--   H107, H302, B205: hợp đồng chưa ký.   H107: mới thu một nửa tiền cọc.   H308, H207: đang giữ chỗ.   H206 đang sửa chữa, B203 tạm ngưng.
-- ============================================================================
SET QUOTED_IDENTIFIER ON;
GO

IF (SELECT COUNT(*) FROM [users]) <> 1 OR NOT EXISTS (SELECT 1 FROM [users] WHERE [user_id] = 1 AND [email] = 'admin@minihouse.local')
   OR EXISTS (SELECT 1 FROM [properties]) OR EXISTS (SELECT 1 FROM [tenants]) OR EXISTS (SELECT 1 FROM [rental_contracts])
    PRINT N'Database đã có dữ liệu (hoặc chưa chạy seed.sql): bỏ qua dữ liệu mẫu test.';
ELSE
BEGIN
    SET XACT_ABORT ON;
    BEGIN TRAN;
    DECLARE @pwd NVARCHAR(255) = (SELECT [password_hash] FROM [users] WHERE [user_id] = 1);
"""
body = []
for tbl in ORDER:
    s = tbl.sql()
    if s:
        body.append(f"    -- {tbl.name}: {len(tbl.rows)} dòng\n" + "\n".join("    " + ln for ln in s.split("\n")))
    if tbl is user_notifs:
        pass
roles_sql = "    -- user_roles\n    INSERT INTO [user_roles] ([user_id], [role_id])\n    SELECT v.uid, r.[role_id] FROM (VALUES " + ", ".join(
    f"({u}, '{ro}')" for u, ro in role_rows) + ") v(uid, code) JOIN [roles] r ON r.[role_code] = v.code;"
body.insert(1, roles_sql)
footer = f"""
    -- phòng đang giữ chỗ
    {HELD_SQL.replace(chr(10), chr(10) + '    ')}
    -- chỗ phòng đang cho thuê để khớp với hợp đồng hiện hành
    UPDATE [rooms] SET [status] = 'RENTED' WHERE [room_id] IN (SELECT [room_id] FROM [rental_contracts] WHERE [status] IN ('ACTIVE', 'EXPIRING_SOON', 'EXPIRED'));
    COMMIT;
    PRINT N'Đã nạp dữ liệu mẫu test: 2 khu trọ, {len(ROOM)} phòng, {len(tenants.rows)} khách, {len(contracts.rows)} hợp đồng, {len(invoices.rows)} hóa đơn.';
END
GO
"""
sample = header + "\n".join(body) + footer
open(os.path.join(DB, "sample-data-test.sql"), "w", encoding="utf-8-sig", newline="\n").write(sample.replace("\n", "\r\n"))

# ------------------------------------------------------------------ ghép minihouse-full.sql
def rd(name):
    return open(os.path.join(DB, name), encoding="utf-8-sig", newline="").read().replace("\r\n", "\n").rstrip("\n") + "\n"


SEP = "-- ============================================================================\n"
FULL_HEAD = (SEP + "-- MINIBOARDINGHOUSE - FILE SQL CHUẨN (GỘP): TẠO DATABASE + BẢNG + NÂNG CẤP + DỮ LIỆU\n"
             "-- Nhóm 7. Chạy MỘT file này là đủ (SSMS bấm F5, hoặc: sqlcmd -S localhost -E -C -f 65001 -I -i minihouse-full.sql).\n"
             "--   Phần 1:  tạo database MiniBoardingHouseDB và toàn bộ bảng (nội dung group-7.sql)\n"
             "--   Phần 1B: nâng cấp database đã tạo từ bản cũ: patch 01 đến 18 (chạy lại an toàn; database mới thì không làm gì)\n"
             "--   Phần 2:  dữ liệu nền: vai trò, quyền, tài khoản admin, nhóm sự cố (nội dung seed.sql)\n"
             "--   Phần 3:  bộ dữ liệu giả định dày để test (nội dung sample-data-test.sql), CHỈ nạp khi database còn trống\n"
             "-- Chạy lại nhiều lần vẫn an toàn: có bảng rồi thì bỏ qua phần tạo bảng, seed không nhân đôi, dữ liệu test không nạp lại.\n"
             "-- File này được GHÉP bằng db/tools/gen_sample_data.py: sửa ở group-7.sql, patch_NN, seed.sql rồi chạy lại script, đừng sửa riêng ở đây.\n"
             "-- Muốn tạo lại từ đầu: DROP DATABASE [MiniBoardingHouseDB] rồi chạy lại file này.\n"
             "-- Tài khoản mặc định (CHỈ DÙNG DEV/DEMO): admin@minihouse.local / Admin@123. Không nạp bản có dữ liệu test lên máy chủ công khai.\n" + SEP + "\n")
PATCHES = sorted(f for f in os.listdir(DB) if f.startswith("patch_") and f[6:8].isdigit() and f != "patch_all.sql")
part1b = (SEP + "-- PHẦN 1B: NÂNG CẤP DATABASE CŨ (patch " + PATCHES[0][6:8] + " đến " + PATCHES[-1][6:8] + "), mỗi patch chạy lại an toàn\n" + SEP + "\n"
          + "\n".join(f"-- ---- {f}\n{rd(f)}GO\n" for f in PATCHES))
schema = rd("group-7.sql")
schema = schema[schema.index(SEP + "-- DATABASE CREATION"):]
part2 = ("-- ############################################################################\n-- PHẦN 2: DỮ LIỆU NỀN\n"
         "-- ############################################################################\n\n" + rd("seed.sql"))
part3 = ("\n-- ############################################################################\n-- PHẦN 3: DỮ LIỆU GIẢ ĐỊNH DÀY ĐỂ TEST (chỉ nạp khi database còn trống)\n"
         "-- ############################################################################\n\n" + sample.replace("\r\n", "\n"))
full = FULL_HEAD + schema + "\n" + part1b + "\n" + part2 + part3
open(os.path.join(DB, "minihouse-full.sql"), "w", encoding="utf-8-sig", newline="").write(full.replace("\n", "\r\n"))

print("OK", {t.name: len(t.rows) for t in ORDER})
