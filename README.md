# MiniBoardingHouse (Nhóm 7)

Website quản lý nhà trọ mini: khu trọ, phòng, khách thuê, hợp đồng, chỉ số điện nước, tính tiền hàng tháng, hóa đơn, thanh toán, công nợ, trả phòng và sửa chữa. Có giao diện web Stayly cho nhân viên (máy tính) và người thuê (điện thoại).

Công nghệ: Java 17, Spring Boot 3, Spring Data JPA, SQL Server. Giao diện: Vite và TypeScript.

## Mục lục

1. [Tình trạng dự án](#1-tình-trạng-dự-án)
2. [Bắt đầu nhanh](#2-bắt-đầu-nhanh)
3. [Cấu hình](#3-cấu-hình)
4. [Cấu trúc code](#4-cấu-trúc-code)
5. [Nhóm và phân công](#5-nhóm-và-phân-công)
6. [Tài liệu](#6-tài-liệu)

---

## 1. Tình trạng dự án

Phạm vi nghiệm thu là **123 use case có tên (UC1–UC123)** trong sheet **Phân Công** của `group-7.xlsx`, kiểm trực tiếp theo cột STT và Tên Usecase / Chức năng. Danh sách 132 mục trong tài liệu phân công cũ là mốc lịch sử. Chưa nghiệm thu đủ 123 use case.

| Use case | Thao tác trong Stayly |
|---|---|
| UC7 | **Tài khoản của tôi** → tải và xem ảnh đại diện. |
| UC14–15 | **Cài đặt → Khu trọ và tầng** → sửa thông tin hỗ trợ, thêm, sửa và xóa tầng. |
| UC16 | **Cài đặt → Loại phòng** → quản lý danh mục và giá mặc định. Chưa nối loại phòng vào hồ sơ phòng. |
| UC19–20 | **Phòng** → tìm theo mã, lọc theo tầng, diện tích, giá thuê và trạng thái. |
| UC22–23 | **Phòng → Chi tiết phòng** → tải nhiều ảnh, đặt ảnh bìa và xóa ảnh. |
| UC28 | **Phòng** → đổi trạng thái phòng chưa có hợp đồng. Trạng thái Đang thuê đi theo hợp đồng. |
| UC35 | **Khách hàng** → tìm theo số CCCD hoặc điện thoại và mở lịch sử thuê. |
| UC123 | **Thông báo** của người thuê → đánh dấu từng mục hoặc tất cả đã đọc và xóa từng mục đã đọc. |

### Kiểm thử

- Maven/Surefire chạy 429 test, 0 lỗi, 0 thất bại, 0 bỏ qua.
- `npm run build` thành công, gồm TypeScript và Vite.
- Spring Boot khởi động với SQL Server QA và `ddl-auto=validate` sau khi áp dụng patch 06.
- Chromium với SQL Server QA chạy thành công các luồng nhân viên: đăng nhập, lọc và đổi trạng thái phòng, tải hai ảnh phòng, đổi ảnh bìa và xóa ảnh, tải và xem ảnh đại diện, sửa khu trọ, thêm/sửa/xóa tầng, tìm khách theo CCCD và mở lịch sử thuê.
- Chromium ở kích thước màn hình điện thoại với SQL Server QA chạy thành công luồng người thuê đăng nhập, đánh dấu từng thông báo đã đọc và xóa thông báo đã đọc.

### Phần còn chờ

- Hoàn tất đối chiếu và nghiệm thu từng use case. Các phần chưa khép kín gồm gán loại phòng, thiết bị phòng trên giao diện, danh mục thiết bị chuẩn có thể chỉnh sửa, yêu cầu giữ chỗ, các luồng đặt lại mật khẩu từ đầu đến cuối, và các chức năng vận hành chỉ có API.
- SMS OTP, email xác thực, khôi phục mật khẩu và gửi hóa đơn đang dùng bộ gửi ghi log. Cần cấu hình nhà cung cấp để kiểm chứng gửi và nhận thực tế.
- QR nhận tiền có giao diện tài khoản ngân hàng. Tích hợp đối soát qua cổng thanh toán cần bí mật callback và cấu hình nhà cung cấp.

---

## 2. Bắt đầu nhanh

Cần: JDK 17, Maven, SQL Server chạy trên máy, Node.js (chỉ khi build giao diện).

**Cài cơ sở dữ liệu** (chạy trên SQL Server, theo thứ tự):

1. `db/group-7.sql`: tạo CSDL `MiniBoardingHouseDB` và các bảng.
2. `db/seed.sql`: nạp 5 vai trò (LANDLORD, MANAGER, CASHIER, METER_READER, TENANT), 22 quyền và tài khoản `admin@minihouse.local` / `Admin@123` (chỉ dùng dev, demo).
3. Chỉ khi DB đã tạo từ bản `group-7.sql` cũ: áp dụng các script `db/patch_01_*.sql` đến `db/patch_06_*.sql` theo thứ tự cho schema cũ đang dùng. Chạy patch 06 trong đúng cơ sở dữ liệu Stayly trước khi khởi động với `ddl-auto=validate`.
4. Dữ liệu mẫu (tùy chọn, dev, demo):

| File | Nạp gì |
|---|---|
| `db/sample-data.sql` | 1 khu trọ, 12 phòng, 8 khách thuê, đơn giá, chỉ số điện nước tháng 10 và 11. Chỉ chạy khi DB chưa có khu trọ |
| `db/sample-data-thang-12.sql` | Chỉ số điện nước tháng 12 để thử luồng tính tiền (chạy sau file trên) |
| `db/sample-data-day.sql` | Thêm 3 khu trọ "(DEMO)", 100 phòng, 100 khách thuê, 6 tháng chỉ số điện nước và chi phí. Không đụng bộ mẫu cũ. Xóa sạch bằng `db/sample-data-day-xoa.sql` |

Hợp đồng, hóa đơn, thanh toán thì tạo bằng giao diện.

**Cấu hình và chạy:**

1. Tạo file `src/main/resources/application-local.properties` (file này bị git ignore):
   ```
   spring.datasource.username=...
   spring.datasource.password=...
   ```
2. Chạy `mvn spring-boot:run`. Ứng dụng đặt `ddl-auto=validate` nên báo lỗi ngay nếu entity lệch với DB.
3. Thử `http://localhost:8080/api/ping`. Xem toàn bộ API tại Swagger: `http://localhost:8080/swagger-ui.html`.
4. Chạy test: `mvn test`.

**Giao diện Stayly** (thư mục `frontend/`): `cd frontend`, `npm install`, `npm run build`, rồi mở `http://localhost:8080/`. Cần bật `app.security.enabled=true` để đăng nhập. Chi tiết: `docs/GIAO_DIEN_STAYLY.md`.

---

## 3. Cấu hình

Đặt trong `src/main/resources/application-local.properties` (không đưa lên GitHub). Giá trị mặc định nằm ở `application.properties`.

| Khóa | Mặc định | Ý nghĩa |
|---|---|---|
| `spring.datasource.username`, `spring.datasource.password` | không có | Tài khoản SQL Server (bắt buộc) |
| `app.security.enabled` | `false` | `true` = bắt buộc đăng nhập JWT, dùng khi demo/bảo vệ |
| `app.jwt.secret` | khóa dev | Bí mật ký JWT, từ 32 ký tự; nên đổi khi chạy thật |
| `app.jwt.expiration-minutes` | `480` | Thời hạn token |
| `app.upload.dir` | `uploads` | Thư mục lưu file upload |
| `app.jobs.enabled` | `false` | Bật các job tự động |
| `app.jobs.<tên-job>.cron` | theo job | Đổi giờ chạy của từng job |
| `app.payment.callback-secret` | trống (tắt) | Bí mật cổng thanh toán gọi `/api/payments/callback` |
| `app.backup.dir` | thư mục sao lưu của SQL Server | Nơi ghi file `.bak` |
| `app.export.pdf-font` | tự tìm Arial/DejaVu | Đường dẫn font `.ttf` cho PDF |

---

## 4. Cấu trúc code

Một project cho cả nhóm: một thư mục dùng chung và mỗi người có một package riêng.

```
com.group7.minihouse
├── shared/            DÙNG CHUNG (không ai tự ý sửa, cần sửa thì báo Phi hoặc tạo PR)
│   ├── entity/        56 entity map 56 bảng trong db/group-7.sql (bảng thứ 57 `checkin_inspections` có entity ở `chi/entity`)
│   ├── dto/           ApiResponse, PageResponse
│   ├── exception/     BusinessException, GlobalExceptionHandler
│   ├── config/        SecurityConfig, ClockConfig
│   ├── security/      JWT, AuthUser, CurrentUser
│   ├── notification/  NotificationSender (gửi email/SMS, bản mặc định chỉ ghi log)
│   ├── storage/       FileStorage (lưu ảnh upload)
│   ├── scheduler/     ScheduledJob, JobScheduler (khung job tự động)
│   ├── export/        TableExporter (xuất Excel/PDF)
│   ├── util/          tiện ích dùng chung
│   └── controller/    PingController (mẫu)
├── phi/               RIÊNG của Nguyễn Anh Phi
├── chi/               RIÊNG của Nguyễn Trần Bảo Chi
├── toan/              RIÊNG của Phạm Khánh Toàn
└── viet/              RIÊNG của Vũ Văn Quốc Việt
```

- Mỗi người chỉ thêm file vào package của mình. Cần sửa `shared/` thì báo Phi hoặc tạo Pull Request.
- Phụ thuộc giữa các package đi một chiều: `chi` → `toan` → `phi` → `viet`. Package đứng sau được gọi service của package đứng trước, không được ngược lại.
- Mỗi thay đổi DB phải sửa `db/group-7.sql` và entity trong cùng một commit; DB đã tồn tại thì thêm `db/patch_NN_*.sql`.

Quy ước đầy đủ (Git, DB, cách viết API, phân quyền, job, xuất file): `docs/HUONG_DAN_LAP_TRINH.md`.

---

## 5. Nhóm và phân công

Bảng phân công lịch sử dùng file `docs/Nhom_7_47_usecase_khutro_4MM_PhanCongMoi.xlsx` (132 mục, 723.0 giờ). Phạm vi nghiệm thu đợt này lấy theo `group-7.xlsx` ở mục 1.

| Thành viên | Mã SV | Use case | Giờ | Cụm nghiệp vụ |
|---|---|---|---|---|
| Nguyễn Anh Phi | TH08326 | 1-13, 41-51, 65-75, 77, 85-88, 116, 118-120, 122 | 239.8 | Khung xương, đăng nhập và phân quyền, hợp đồng, tính tiền, thanh toán, báo cáo, toàn bộ giao diện |
| Nguyễn Trần Bảo Chi | TH08666 | 14-40, 108-109, 124-125 | 160.7 | Phòng, khách thuê, yêu cầu thuê, mẫu hợp đồng |
| Phạm Khánh Toàn | TH08433 | 53-64, 76, 78-84, 89-92, 126-129 | 160.6 | Điện nước, đơn giá, hóa đơn, thu tiền |
| Vũ Văn Quốc Việt | TH08548 | 52, 93-107, 110-115, 117, 121, 123, 130-132 | 161.9 | Công nợ, trả phòng, sửa chữa, thông báo, chi phí |

**Giao diện: toàn bộ màn hình do Phi làm**, gồm cả màn hình của các chức năng thuộc phần Chi, Toàn, Việt. Chi, Toàn, Việt chỉ làm backend (API, nghiệp vụ, test) và không cần làm giao diện.

Chi tiết (giờ các use case thêm sau, bảng DB của từng người): `docs/PHAN_CONG.md`.

---

## 6. Tài liệu

| File | Nội dung |
|---|---|
| `docs/TIEN_DO_VA_CON_THIEU.md` | Tiến độ từng use case, lỗi và chỗ mâu thuẫn, phương án cho phần của Việt |
| `docs/PHAN_CONG.md` | Phân công theo use case và theo bảng DB |
| `docs/HUONG_DAN_LAP_TRINH.md` | Cấu trúc code, quy ước Git và DB, cách viết API, phân quyền, job, xuất file |
| `docs/API_PHI.md` | Các API do Phi làm (tài khoản, hợp đồng, tính tiền, thanh toán, báo cáo) |
| `docs/GIAO_DIEN_STAYLY.md` | Giao diện web: lệnh chạy, các màn hình, cách hoạt động |
| `docs/KICH_BAN_KHACH_A.md` | Kịch bản khách A từ lúc đến thuê tới lúc trả phòng: ai làm gì, ở màn nào, tiền đi đâu |
| `docs/HUONG_DAN_GIT.md` | Cách đẩy code và mở Pull Request |
| `docs/HUONG_DAN_SU_DUNG.txt` | Hướng dẫn sử dụng ngắn gọn |
| `docs/LOI_CAN_SUA_CODE_TOAN.md` | Các lỗi cần sửa trong code của Toàn |
| `docs/thiet-ke/` | Lý do chọn cách làm đăng nhập, tài khoản người thuê, quản lý tài khoản |
| Swagger | `http://localhost:8080/swagger-ui.html`: toàn bộ API, kể cả của Chi, Toàn, Việt |
