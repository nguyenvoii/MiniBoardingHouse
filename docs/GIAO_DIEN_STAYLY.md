# Giao diện Stayly

Người làm toàn bộ giao diện (frontend): Nguyễn Anh Phi (TH08326).

Các màn hình còn thiếu cho phần của Chi, Toàn, Việt: xem `TIEN_DO_VA_CON_THIEU.md` mục 5.

Giao diện web nằm trong `frontend/` (Vite + TypeScript, không dùng framework UI). Nhân viên (chủ trọ, quản lý, thu ngân) dùng trên máy tính; người thuê dùng trên điện thoại. Cùng một ứng dụng, tự chọn khu vực theo vai trò khi đăng nhập.

| Lệnh (chạy trong `frontend/`) | Việc |
|---|---|
| `npm install` | Cài thư viện (một lần) |
| `npm run dev` | Chạy thử ở `http://localhost:5173`, tự chuyển `/api` sang backend cổng 8080 (backend phải đang chạy, không cần CORS) |
| `npm run build` | Build vào `src/main/resources/static`, Spring Boot phục vụ chung cổng 8080 tại `/` |

Thư mục `src/main/resources/static` và `frontend/node_modules` bị git ignore: ai muốn thấy giao diện trên bản backend của mình thì chạy `npm run build`.

- Màn hình nhân viên: Tổng quan (có "Tuyến tháng" 5 chặng), Chỉ số điện nước, Tính tiền tháng, Hóa đơn, Thanh toán, Công nợ, Sửa chữa, Hợp đồng, Phòng, Khách thuê (kèm cấp, khóa, mở khóa tài khoản đăng nhập), Doanh thu và chi phí, Chi phí vận hành, Nhân viên, Cài đặt (đơn giá điện nước, dịch vụ, phụ thu và giảm trừ, tài khoản ngân hàng và ảnh QR), Nhật ký thao tác, Tài khoản của tôi. Chuông thông báo ở thanh trên.
- Màn hình người thuê: Trang chủ (số tiền cần trả, trả bằng QR), Hóa đơn, Hợp đồng (ký tay trên màn hình), Hỗ trợ (báo sự cố, đánh giá xử lý), Thông báo.
- Chọn tháng ở thanh trên chỉ áp dụng cho Tổng quan, Chỉ số điện nước, Tính tiền tháng, Hóa đơn, Thanh toán, Chi phí vận hành và Báo cáo; các màn khác ẩn nó.
- Phòng, khách thuê và danh sách khu trọ lấy từ API của Chi (`/api/chi/**`); trạng thái thuê và người đang ở lấy từ hợp đồng thật. Có thêm phòng và thêm khách thuê ngay trên giao diện.
- Ô chọn khu trọ ở thanh trên (nhớ lựa chọn trong trình duyệt, đổi thì tải lại trang): mọi màn hình theo khu trọ đã chọn. Dữ liệu DEMO dày (3 khu, 100 phòng) nạp bằng `db/sample-data-day.sql`.
- Các màn chốt tháng (Chỉ số, Tính tiền, Hóa đơn, Thanh toán, Công nợ) có thanh "Tuyến tháng" thu gọn ở đầu trang, chặng đang xem được gạch chân.
- Hủy giao dịch nằm trong hộp chi tiết giao dịch, chấm dứt hợp đồng nằm trong "vùng nguy hiểm" của hộp hợp đồng, để tránh bấm nhầm.
- Duyệt kỳ tính tiền sẽ tự đóng kỳ ghi chỉ số cùng tháng. Công nợ tự cập nhật khi mở trang. Trên điện thoại (≤700px) bảng chuyển thành thẻ.
- Backend bật nén dữ liệu (`server.compression.enabled=true`) để giao diện tải nhanh hơn.
- Khu trọ đang làm việc lưu trong trình duyệt (`PROPERTY_ID` trong `frontend/src/lib/api.ts`), mặc định là khu có mã 1.
- Hóa đơn và chỉ số của Toàn trả JSON thô (không bọc `{success, data}`), client đã xử lý cả hai kiểu.
