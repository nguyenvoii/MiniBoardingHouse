# E2E nghiệm thu MiniBoardingHouse (26/11/2026)

Kết quả: 123/123 UC E2E PASS (mức bên ngoài: SMTP/payOS thử bằng máy chủ giả cục bộ, SMS lấy mã từ log; gửi/thu tiền thật cần khóa) do phụ thuộc bên ngoài (xem bảng dưới). Chi tiết từng UC ở cột F/G của
`docs/KET_QUA_E2E_NGHIEM_THU.xlsx` (hòa vào `docs/NGHIEM_THU_123_USE_CASE.xlsx` bằng `python docs/merge_nghiem_thu_e2e.py` sau khi đóng Excel).

| UC | Trạng thái | Việc còn lại |
| --- | --- | --- |
| UC86 thanh toán payOS | PASS (payOS giả) | Điền `app.payment.payos.client-id/api-key/checksum-key/return-url/cancel-url` + `app.payment.payos.enabled=true` để thu tiền thật |
| UC122 phục hồi dữ liệu | PASS | Cần đăng nhập SQL có quyền restore (QA: đã `ALTER SERVER ROLE dbcreator ADD MEMBER mbh_app`; hoàn tác bằng `DROP MEMBER`) |
| UC80, UC1 | PASS qua SMTP cục bộ | Điền `spring.mail.*` + `app.mail.enabled=true` để gửi qua nhà cung cấp thật |
| UC2 | PASS | SMS thật cần khóa Twilio; QA lấy mã từ log backend |

## Chạy lại

1. Áp dụng `db/patch_11` … `db/patch_16` lên DB QA.
2. Backend QA (cổng 18091, tắt job): `target/start-qa.sh` (biên dịch bằng `javac`, xem `target/qa-classes`).
3. Frontend: `cd frontend && node node_modules/vite/bin/vite.js --config ../target/vite.e2e.config.mjs` (cổng 5178).
4. `audit/e2e/run-all.sh <kịch bản>.mjs ...` (cần `playwright` trong `audit/e2e/node_modules`). Mỗi kịch bản tự dựng dữ liệu riêng.

Kịch bản: auth, tenant-profile, rooms, contract, contract2, bookings, occupancy, meter-transfer, checkout, photos, maintenance,
payments, payments2, tenant-portal, debts, billing, anomaly, settings-reports, system-config.

## Giới hạn đã biết

- `billing-flow` dùng tháng quá khứ chưa có kỳ của khu 1 (chi phí vận hành không ghi được ở tương lai): còn khoảng 17 tháng; hết thì cần DB QA mới.
- Chi phí vận hành chưa gắn khu trọ nên khoản "Điện nước chung" chỉ chia cho khu mặc định.
- Ảnh chữ ký hợp đồng công khai theo tên UUID (thiết kế hiện tại).
- Không có Maven trên máy: biên dịch và chạy JUnit bằng `javac` + `junit-platform-console-standalone` (458 test đạt).
