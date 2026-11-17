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

Cập nhật ngày 17/11/2026, theo những gì có trên nhánh `main`. Hệ thống có 132 use case. Bộ test hiện có 279 test, đều đạt.

| Thành viên | Phụ trách | Giao | Xong | Một phần | Chỉ có backend | Chưa làm | Việc gấp |
|---|---|---|---|---|---|---|---|
| Nguyễn Anh Phi | Khung xương, tài khoản, hợp đồng, tính tiền, thanh toán, báo cáo, giao diện | 45 | 45 | 0 | 0 | 0 | Dựng các màn giao diện còn thiếu |
| Nguyễn Trần Bảo Chi | Phòng, khách thuê, đặt phòng, nhận phòng | 31 | 6 | 5 | 12 | 8 | UC18 (sửa phòng), UC31 (sửa hồ sơ khách); chốt với Việt phần trùng thiết bị phòng |
| Phạm Khánh Toàn | Điện nước, đơn giá, hóa đơn, thu tiền | 28 | 14 | 1 | 9 | 4 | Gắn `@PreAuthorize` cho mọi API; UC126 đến 129 |
| Vũ Văn Quốc Việt | Công nợ, trả phòng, sửa chữa, thông báo, chi phí | 28 | 13 | 1 | 11 | 3 | UC130 đến 132; bổ sung test; chốt hai chỗ trùng |

- **Xong**: có cả API lẫn giao diện dùng được. **Chỉ có backend**: API đã có, chưa có màn hình.
- Danh sách từng use case còn thiếu, các lỗi và chỗ mâu thuẫn giữa các phần, phương án cho phần của Việt: `docs/TIEN_DO_VA_CON_THIEU.md`.
- Ai đẩy code mới thì tự sửa dòng của mình trong bảng này và trong file trên.

### Còn thiếu của từng người

Mỗi người nhận đúng các use case dưới đây. "Chưa làm" là chưa có code, "Làm dở" là có nhưng thiếu phần ghi kèm, "Có API, chưa có màn hình" là cần dựng giao diện (có thể nhờ Phi hoặc tự làm theo mẫu trong `frontend/src/pages/`). Làm xong thì xóa dòng đó đi và cập nhật bảng trên.

**Nguyễn Trần Bảo Chi**

- Chưa làm (8 use case):
  - UC14 sửa thông tin khu trọ, SĐT hỗ trợ
  - UC15 thêm, sửa, xóa tầng
  - UC16 loại phòng và giá theo loại (cần chốt bỏ hay thêm bảng)
  - UC18 sửa phòng (mã, diện tích, giá, cọc)
  - UC24 danh mục thiết bị, nội thất chuẩn
  - UC29 phòng chờ vệ sinh, xác nhận cho thuê lại
  - UC31 sửa hồ sơ khách, ngày cư trú
  - UC125 xuất danh sách phòng, khách ra Excel
- Làm dở, cần bổ sung (5 use case):
  - UC19 chưa tra phòng theo mã
  - UC20 chưa lọc theo diện tích, giá
  - UC27 chưa quản lý thiết bị công tơ, đồng hồ
  - UC35 chưa có lịch sử thuê phòng của khách
  - UC37 người thuê chưa tự gửi được yêu cầu thuê
- Có API, chưa có màn hình (12 use case):
  - UC22, 23 ảnh phòng
  - UC25, 26 nội thất và tình trạng
  - UC28 đổi trạng thái phòng
  - UC32 CCCD, xác minh khách
  - UC38 duyệt, từ chối yêu cầu thuê
  - UC39 tiền đặt cọc
  - UC40 mẫu hợp đồng
  - UC108 yêu cầu thiết bị (trùng Việt)
  - UC109 phụ phí tiện ích (chưa vào hóa đơn)
  - UC124 biên bản nhận phòng

**Phạm Khánh Toàn**

- Chưa làm (4 use case):
  - UC126 hủy hóa đơn phát hành nhầm
  - UC127 báo cáo thu tiền theo ngày, theo người thu
  - UC128 lịch sử thao tác trên hóa đơn, giao dịch
  - UC129 xuất bảng kê hóa đơn, phiếu thu ra Excel, PDF
- Làm dở, cần bổ sung (1 use case):
  - UC92 thanh toán dư: cấn trừ kỳ sau, hoàn tiền mới có API
- Có API, chưa có màn hình (9 use case):
  - UC55 ảnh công tơ
  - UC58 lịch sử tiêu thụ của phòng
  - UC59 so sánh tiêu thụ các tháng (biểu đồ)
  - UC60 chỉ số khi nhận, trả phòng
  - UC64 lịch sử đổi đơn giá
  - UC81 thông báo hóa đơn trong app, link chia sẻ
  - UC89 phiếu thu
  - UC90 biên nhận
  - UC91 thanh toán một phần

**Vũ Văn Quốc Việt**

- Chưa làm (3 use case):
  - UC130 gửi thông báo chung cho cả khu hoặc theo tầng
  - UC131 thống kê sự cố, lịch sử bảo trì
  - UC132 báo cáo hoàn cọc, quyết toán trả phòng theo tháng
- Làm dở, cần bổ sung (1 use case):
  - UC112 gửi yêu cầu sửa chữa kèm ảnh (phần ảnh chưa kiểm chứng)
- Có API, chưa có màn hình (11 use case):
  - UC52 danh sách hợp đồng sắp hết hạn
  - UC101 đến 107 trả phòng, quyết toán, hoàn cọc, thanh lý (Phi đang dựng màn này)
  - UC111 điều chỉnh hóa đơn từ sự cố
  - UC113 quản lý nhóm sự cố
  - UC121 mẫu thông báo

---

## 2. Bắt đầu nhanh

Cần: JDK 17, Maven, SQL Server chạy trên máy, Node.js (chỉ khi build giao diện).

**Cài cơ sở dữ liệu** (chạy trên SQL Server, theo thứ tự):

1. `db/group-7.sql`: tạo CSDL `MiniBoardingHouseDB` và các bảng.
2. `db/seed.sql`: nạp 5 vai trò (LANDLORD, MANAGER, CASHIER, METER_READER, TENANT), 22 quyền và tài khoản `admin@minihouse.local` / `Admin@123` (chỉ dùng dev, demo).
3. Chỉ khi DB đã tạo từ bản `group-7.sql` cũ: chạy `db/patch_01_users_unique.sql` và `db/patch_02_checkin_inspections.sql` (chạy lại nhiều lần vẫn an toàn).
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

Nguồn chính thức là file Excel `docs/Nhom_7_47_usecase_khutro_4MM_PhanCongMoi.xlsx` (132 use case, 723.0 giờ).

| Thành viên | Mã SV | Use case | Giờ | Cụm nghiệp vụ |
|---|---|---|---|---|
| Nguyễn Anh Phi | TH08326 | 1-13, 41-51, 65-75, 77, 85-88, 116, 118-120, 122 | 239.8 | Khung xương, đăng nhập và phân quyền, hợp đồng, tính tiền, thanh toán, báo cáo, toàn bộ giao diện |
| Nguyễn Trần Bảo Chi | TH08666 | 14-40, 108-109, 124-125 | 160.7 | Phòng, khách thuê, yêu cầu thuê, mẫu hợp đồng |
| Phạm Khánh Toàn | TH08433 | 53-64, 76, 78-84, 89-92, 126-129 | 160.6 | Điện nước, đơn giá, hóa đơn, thu tiền |
| Vũ Văn Quốc Việt | TH08548 | 52, 93-107, 110-115, 117, 121, 123, 130-132 | 161.9 | Công nợ, trả phòng, sửa chữa, thông báo, chi phí |

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
| `docs/HUONG_DAN_GIT.md` | Cách đẩy code và mở Pull Request |
| `docs/HUONG_DAN_SU_DUNG.txt` | Hướng dẫn sử dụng ngắn gọn |
| `docs/LOI_CAN_SUA_CODE_TOAN.md` | Các lỗi cần sửa trong code của Toàn |
| `docs/thiet-ke/` | Lý do chọn cách làm đăng nhập, tài khoản người thuê, quản lý tài khoản |
| Swagger | `http://localhost:8080/swagger-ui.html`: toàn bộ API, kể cả của Chi, Toàn, Việt |
