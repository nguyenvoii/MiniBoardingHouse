# Sửa theo góp ý 2026-11-27

Nguồn: 5 lỗi ghi nhận khi dùng thử. Phi đã chốt hướng làm.

## 1. Ô chọn khách (hợp đồng, đổi người đứng tên, số dư trả trước, nhận khách, trang Khách hàng)

Trước: mọi ô tải toàn bộ khách rồi lọc ở trình duyệt. Khách nhiều thì chậm và danh sách dài.

Sau: `GET /api/chi/tenants?keyword=&limit=&available=&renting=` lọc ở DB, tối đa 50 dòng.
Từ khóa khớp tên, SĐT, CCCD hoặc mã phòng đang thuê. Giao diện dùng `remotePicker` (kit.ts) và `tenantPicker`: gõ xong 250ms mới hỏi, mỗi lần 15 gợi ý.
`loadLive()` không còn tải khách. Trang Khách hàng tải 50 khách đầu và lọc/tìm ở server.
Đổi người đứng tên chỉ gợi ý khách chưa có hợp đồng (`available=true`).

## 2. Người ở ghép

Một người đứng tên hợp đồng, người còn lại khai ở "Người ở cùng". Thêm 3 cột vào `room_residents` (db/patch_17): ngày sinh, địa chỉ thường trú, ngày đăng ký tạm trú.
Hộp thoại khai thêm ngày vào ở. Chi tiết hợp đồng có mục "Người ở cùng"; màn Nhận khách bước 4 vẫn khai được như cũ.
Chưa làm: ảnh CCCD của người ở cùng, nhắc hạn tạm trú.

## 3. Tình trạng phòng lúc bàn giao

Bước 3 Nhận khách: mỗi món đồ của phòng (`room_furnishings`) một ô tick kèm ghi chú. Món không tick thì bắt buộc ghi chú thiếu/hỏng.
Kết quả lưu vào `furnishings_condition` dạng từng dòng "✔ Giường (x2)" / "✖ Tủ: cánh trái hỏng bản lề", nên màn Trả phòng đọc lại được, không đổi DB.
Phòng chưa khai báo đồ đạc thì vẫn ghi tay như cũ.

## 4. QR đăng nhập cho khách vừa được cấp tài khoản

`POST /api/tenants/{id}/account/login-link` (chủ trọ/quản lý) trả mã; `POST /api/auth/qr-login` (công khai) đổi mã lấy phiên đăng nhập.
Mã hết hạn sau 24 giờ, chỉ dùng được khi tài khoản còn mật khẩu tạm; khách đổi mật khẩu hoặc được cấp lại mật khẩu tạm là mã chết.
Mã không dùng trực tiếp làm token phiên được (JwtService.parse từ chối). Link dạng `/?qr=<mã>`, trang đăng nhập đổi mã rồi xóa khỏi thanh địa chỉ.
Hộp thoại cấp tài khoản vẽ mã QR của link (thư viện qrcode-generator, ui/qr.ts) kèm nút chép link.

## 5. Đóng đăng ký chủ trọ và yêu cầu thuê của khách vãng lai

Xem commit 3f20b47.
