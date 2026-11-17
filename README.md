# MiniBoardingHouse (Nhóm 7)

Website quản lý nhà trọ mini. Công nghệ: Java 17, Spring Boot 3, Spring Data JPA, SQL Server.

## Mục lục

1. [Bắt đầu nhanh](#1-bắt-đầu-nhanh)
2. [Tiến độ từng người](#2-tiến-độ-từng-người)
3. [Phân công](#3-phân-công)
4. [Cấu trúc code](#4-cấu-trúc-code)
5. [Quy ước làm chung](#5-quy-ước-làm-chung)
6. [Hướng dẫn viết API cho mọi người](#6-hướng-dẫn-viết-api-cho-mọi-người)
7. [Các API đã có (phần của Phi)](#7-các-api-đã-có-phần-của-phi)
8. [Cấu hình](#8-cấu-hình)
9. [Giao diện Stayly](#9-giao-diện-stayly)

Tài liệu khác trong thư mục `docs/`: file Excel phân công, `HUONG_DAN_GIT.md` (cách đẩy code và mở Pull Request), `LOI_CAN_SUA_CODE_TOAN.md`, và `docs/thiet-ke/` (lý do chọn cách làm đăng nhập, tài khoản người thuê, quản lý tài khoản).

---

## 1. Bắt đầu nhanh

1. Chạy `db/group-7.sql` trên SQL Server.
2. Tạo file `src/main/resources/application-local.properties` (file này bị git ignore):
   ```
   spring.datasource.username=...
   spring.datasource.password=...
   ```
3. Chạy `db/seed.sql` để nạp 5 vai trò (LANDLORD, MANAGER, CASHIER, METER_READER, TENANT), 22 quyền và tài khoản `admin@minihouse.local` / `Admin@123` (chỉ dùng dev/demo).
3a. Nếu DB đã tạo từ `group-7.sql` cũ (trước khi có bảng `checkin_inspections` của Chi), chạy thêm `db/patch_02_checkin_inspections.sql`. Chạy lại nhiều lần vẫn an toàn.
3b. (Tùy chọn, dev/demo) Chạy `db/sample-data.sql` để nạp 1 khu trọ, 12 phòng, 8 khách thuê, đơn giá và chỉ số điện nước mẫu. File chỉ chạy khi DB chưa có khu trọ. Hợp đồng, hóa đơn, thanh toán thì tạo bằng giao diện.
3c. (Tùy chọn, để test nhiều dữ liệu) Chạy `db/sample-data-day.sql` để nạp thêm 3 khu trọ "(DEMO)", 100 phòng, 100 khách thuê, 6 tháng chỉ số điện nước và chi phí vận hành. Không đụng bộ mẫu cũ, chạy lại an toàn. Xóa sạch bằng `db/sample-data-day-xoa.sql`.
4. Chạy `mvn spring-boot:run`. Ứng dụng đặt `ddl-auto=validate` nên sẽ báo lỗi ngay nếu entity lệch với DB.
5. Thử: `http://localhost:8080/api/ping`. Xem toàn bộ API tại Swagger: `http://localhost:8080/swagger-ui.html`.
6. Giao diện Stayly (thư mục `frontend/`): xem mục 9. Chạy nhanh: `cd frontend`, `npm install`, `npm run build`, rồi mở `http://localhost:8080/` (cần bật `app.security.enabled=true` để đăng nhập).

---

## 2. Tiến độ từng người

Cập nhật ngày 16/11/2026, theo những gì thực sự có trên GitHub (nhánh `main` và các nhánh con). Ai đẩy code mới thì tự sửa dòng của mình trong bảng này.

| Thành viên | Được giao | Đã có trên `main` | Đang chờ / việc tiếp theo |
|---|---|---|---|
| Nguyễn Anh Phi | 45 UC | 45 UC: 1-13, 41-51, 65-75, 85-88, 116, 118, 119, 120, 122, cộng khung xuất Excel/PDF | Đã xong hết phần được giao (UC76, UC77 do Toàn làm). Phần Phi đọc phòng và khách thuê trực tiếp qua EntityManager vì Chi chưa có service |
| Nguyễn Trần Bảo Chi | 31 UC | 29 UC trong package `chi` (API `/api/chi/**`): phòng, tầng, ảnh phòng, nội thất, khách thuê, xe, người ở cùng, đặt phòng, mẫu hợp đồng, yêu cầu thiết bị, phụ phí, biên bản nhận phòng (UC124) | Đã gộp vào main, đã rà soát và sửa (kiểm tra dữ liệu vào, luồng trạng thái, phân quyền, upload ảnh an toàn, 21 test). Giao diện riêng của Chi đã bỏ, dùng giao diện Stayly. Chưa có: UC125 xuất danh sách phòng/khách ra Excel. Phần của Phi vẫn đọc phòng, khách qua EntityManager, chưa đổi sang `RoomService`/`TenantService` của Chi |
| Phạm Khánh Toàn | 28 UC | Một phần (Pull Request #2): hóa đơn, chỉ số điện nước, thu tiền, đơn giá dịch vụ, job nhắc hạn | Chưa có test. Chi phí vận hành: đã bỏ bản của Toàn, giữ bản của Việt (`/api/expenses`, có phân quyền, tóm tắt, bộ lọc). Đối chiếu lại các mục còn lại trong `docs/LOI_CAN_SUA_CODE_TOAN.md`. Lưu ý: `BillingJobs` đang TỰ BẬT (thiếu cấu hình = bật), nên đặt `minihouse.scheduler.enabled=false` khi chạy thử trên máy. |
| Vũ Văn Quốc Việt | 28 UC | Đã merge PR #1 | Đã có công nợ, trả phòng, bảo trì, thiết bị phòng, chi phí vận hành, thông báo. |

Phần của Phi chi tiết:
- Đã làm: UC1 đăng ký chủ trọ, UC2 xác thực email bằng OTP, UC3 đăng nhập/đăng xuất, UC4 quên mật khẩu, UC5 đổi mật khẩu, UC6 hồ sơ cá nhân, UC7 ảnh đại diện, UC8-9 quản lý nhân viên, UC10 cấp tài khoản người thuê, UC11 phân quyền theo vai trò, UC12 bảng quyền chi tiết, UC13 nhật ký thao tác, UC119 tài khoản ngân hàng và QR, UC120 khung job tự động, UC122 sao lưu và phục hồi, khung xuất Excel/PDF.
- Hợp đồng UC41-51 đã làm xong (mục 7.3).
- Tính tiền tháng UC65-75 (mục 7.4), thanh toán UC85-88 (7.5), dashboard UC116 và báo cáo UC118 (7.6) đã làm xong. UC76 và UC77 là phần của Toàn (đã có trong `InvoiceController`).
- Chưa gửi email/SMS thật: mã OTP hiện chỉ in ra log của ứng dụng (xem mục 7.2).

---

## 3. Phân công

### 3.1. Theo use case

Nguồn chính thức là file `docs/Nhom_7_47_usecase_khutro_4MM_PhanCongMoi.xlsx` (sheet "Phân Công", 132 use case, 723.0 giờ). Bảng dưới chỉ là bản tóm tắt, nếu lệch thì lấy Excel làm chuẩn.

| Thành viên | Số UC | Giờ | Use case |
|---|---|---|---|
| Nguyễn Anh Phi | 45 | 239.8 | 1-13, 41-51, 65-75, 77, 85-88, 116, 118-120, 122 |
| Nguyễn Trần Bảo Chi | 31 | 160.7 | 14-40, 108-109, 124-125 |
| Phạm Khánh Toàn | 28 | 160.6 | 53-64, 76, 78-84, 89-92, 126-129 |
| Vũ Văn Quốc Việt | 28 | 161.9 | 52, 93-107, 110-115, 117, 121, 123, 130-132 |

Cách cân bằng giờ: mỗi thành viên cần tối thiểu 160 giờ. Chi, Toàn và Việt ban đầu chỉ có khoảng 140-147 giờ, nên đã thêm 9 use case mới (UC124-132) vào Excel, chỉ dùng các bảng có sẵn trong DB, không thêm hay xóa bảng nào:

| UC | Người làm | Nội dung | Giờ |
|---|---|---|---|
| 124 | Chi | Lập biên bản nhận phòng khi bắt đầu thuê (đối ứng với biên bản trả phòng UC102, 107 của Việt) | 9.0 |
| 125 | Chi | Xuất danh sách phòng và khách thuê đang ở ra Excel | 4.5 |
| 126 | Toàn | Hủy hóa đơn phát hành nhầm (chỉ khi chưa thu tiền, ghi lý do; dùng trạng thái `CANCELLED` có sẵn) | 7.8 |
| 127 | Toàn | Báo cáo thu tiền theo ngày, theo người thu, đối soát cuối ngày | 4.5 |
| 128 | Toàn | Xem lịch sử thao tác trên hóa đơn và giao dịch | 3.7 |
| 129 | Toàn | Xuất bảng kê hóa đơn và phiếu thu theo tháng | 4.5 |
| 130 | Việt | Gửi thông báo chung tới người thuê (toàn khu hoặc theo tầng), theo dõi số người đã đọc | 6.6 |
| 131 | Việt | Thống kê sự cố và lịch sử bảo trì theo phòng | 4.5 |
| 132 | Việt | Báo cáo hoàn tiền đặt cọc và quyết toán trả phòng theo tháng | 3.7 |

Phi vẫn dư giờ so với mốc 160 (239.8 giờ) vì phần khó nhất và toàn bộ khung xương nằm ở Phi.

### 3.2. Theo nhóm bảng

Ngoài ra, Phi làm toàn bộ frontend (giao diện web Stayly trong `frontend/`, xem mục 9).

Nguyên tắc: Phi giữ toàn bộ khung xương của dự án và các phần khó nhất (tính tiền tháng, hợp đồng, thanh toán, phân quyền). Ba người còn lại chia đều số bảng, mỗi người một cụm nghiệp vụ độc lập để làm đồng thời được. Phi 13 + Chi 14 + Toàn 15 + Việt 14 = 56 bảng, bằng số entity trong `shared/entity`.

| Thành viên | Mã SV | Số bảng | Vai trò |
|---|---|---|---|
| Nguyễn Anh Phi | TH08326 | 13 | Khung xương + phần khó: cấu hình, xử lý lỗi, tiện ích, scheduler, xuất Excel/PDF, đăng nhập - phân quyền, tính tiền tháng, hợp đồng, thanh toán |
| Nguyễn Trần Bảo Chi | TH08666 | 14 | Phòng - khách thuê - yêu cầu thuê |
| Phạm Khánh Toàn | TH08433 | 15 | Điện nước - đơn giá - hóa đơn - thu tiền |
| Vũ Văn Quốc Việt | TH08548 | 14 | Công nợ - trả phòng - sửa chữa - thông báo - chi phí |

Các bảng của từng người:
- **Phi** (13 bảng + khung xương):
  - Người dùng - phân quyền: `User`, `Role`, `Permission`, `UserRole` (+ `UserRoleId`), `UserVerification`, `UserActivityLog`
  - Hợp đồng: `RentalContract`, `ContractAdjustment`
  - Tính tiền tháng: `MonthlyBillingPeriod`, `CalculatedMonthlyFee`, `FeeItemDetail`
  - Thanh toán: `PaymentTransaction`, `BankAccount`
- **Chi** (14 bảng):
  - Khu trọ - phòng: `Property`, `PropertyFloor`, `Room`, `RoomMedia`, `RoomFurnishing`, `RoomEquipmentRequest`, `RoomAddonFee`
  - Khách thuê - cư dân: `Tenant`, `TenantIdCard`, `RoomResident`, `ResidentStayHistory`, `TenantVehicle`
  - Yêu cầu thuê - mẫu hợp đồng: `BookingRequest`, `ContractTemplate`
- **Toàn** (15 bảng):
  - Chỉ số điện nước: `UtilityReadingPeriod`, `MeterReading`, `MeterPhoto`, `MeterAdjustmentLog`, `MeterAnomalyAlert`
  - Đơn giá - phí dịch vụ: `ServiceRatePlan`, `PropertyServiceRate`, `RoomCustomRate`, `RateChangeHistory`
  - Hóa đơn: `Invoice`, `InvoiceNotification`
  - Thu tiền - cấn trừ: `ReceiptVoucher`, `PartialPaymentLog`, `TenantPrepayment`, `PrepaymentDeduction`
- **Việt** (14 bảng):
  - Công nợ: `TenantDebt`, `DebtPenaltyLog`, `DebtReminder`, `DebtProcessingLog`
  - Trả phòng - hoàn cọc: `CheckoutRequest`, `CheckoutInspection`, `DepositRefundVoucher`
  - Sự cố sửa chữa: `MaintenanceRequest`, `MaintenanceLog`, `IncidentCategory`
  - Thông báo: `ContractNotification`, `NotificationTemplate`, `UserNotification`
  - Chi phí vận hành: `OperatingExpense`

Khung xương của Phi (không gắn với bảng nào): cấu hình bảo mật JWT, xử lý lỗi thống nhất, `ApiResponse`/`PageResponse`, tiện ích (upload ảnh, gửi thông báo), khung job tự động (Toàn viết phần chốt chỉ số - tính hóa đơn, Việt viết phần nhắc nợ - phạt trễ hạn), xuất Excel/PDF, dashboard - báo cáo, sao lưu phục hồi (UC122).

---

## 4. Cấu trúc code

Một project duy nhất cho cả nhóm: 1 thư mục dùng chung và mỗi người có 1 package riêng mang tên mình.

```
com.group7.minihouse
├── shared/            DÙNG CHUNG (không ai tự ý sửa, cần sửa thì báo Phi hoặc tạo PR)
│   ├── entity/        56 entity map 56 bảng trong db/group-7.sql (cộng bảng `checkin_inspections` của Chi, entity ở `chi/entity`, tổng 57 bảng)
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

Trong package của mỗi người có 4 thư mục: `controller`, `service`, `repository`, `dto`. Ví dụ Chi làm phòng: `chi/controller/RoomController`, `chi/service/RoomService`, `chi/repository/RoomRepository`, `chi/dto/RoomResponse`. Đặt tên file theo tên entity.

Quy tắc:
- Mỗi người chỉ thêm file vào package của mình, nên không đụng file nhau khi merge.
- Cần dữ liệu của bảng người khác thì gọi `Service` của người đó (ví dụ Toàn cần phòng thì gọi `RoomService` của `chi`), hoặc dùng quan hệ JPA có sẵn trong entity. Không tự viết repository cho bảng người khác.
- Phụ thuộc giữa các package đi MỘT CHIỀU: `chi` (phòng, khách thuê) → `toan` (chỉ số, đơn giá, hóa đơn, phiếu thu) → `phi` (hợp đồng, tính tiền, thanh toán) → `viet` (công nợ, trả phòng, sửa chữa). Package đứng sau được gọi Service của package đứng trước, KHÔNG được ngược lại. Ví dụ: khi tính tiền tháng, Phi gọi `MeterReadingService`/`RateService` của Toàn; khi chốt tiền, Phi gọi `InvoiceService` của Toàn (Toàn không gọi ngược `phi`). Nếu bắt buộc phải gọi ngược thì chuyển phần dùng chung xuống `shared` hoặc truyền dữ liệu qua tham số/ID.
- Các service nền tảng nhiều người cần (`RoomService`, `TenantService`) thì chủ sở hữu nên làm trước các hàm đọc cơ bản (`findById`, `findAll`), rồi mới làm nghiệp vụ phức tạp.
- Spring Boot tự quét toàn bộ package con, nên thêm class vào đúng thư mục là chạy, không cần cấu hình gì thêm.

---

## 5. Quy ước làm chung

Git:
- Mỗi người làm trên nhánh riêng, ví dụ `phi/room`, `chi/tenant`, `toan/invoice`, `viet/debt`.
- Pull `main` trước khi bắt đầu làm và trước khi push.
- Làm xong thì tạo Pull Request vào `main`, không push thẳng lên `main` (cách làm từng bước: `docs/HUONG_DAN_GIT.md`).
- Không sửa file entity của người khác; cần sửa thì nhắn người đó hoặc tạo PR.

Cơ sở dữ liệu:
- Mỗi thay đổi DB phải sửa `db/group-7.sql` VÀ entity tương ứng trong cùng 1 commit. Số class trong `shared/entity` phải bằng số bảng trong DB.
- Mọi entity có `@DynamicInsert`: cột nào để `null` khi tạo mới thì Hibernate bỏ qua, DB tự điền giá trị `DEFAULT` (ví dụ `created_at`, `status`). Entity mới phải thêm `@DynamicInsert`.

Code:
- Mọi API trả về `ApiResponse`; lỗi nghiệp vụ dùng `BusinessException`; kiểm tra đầu vào bằng `@Valid` và các annotation của `spring-boot-starter-validation`.
- Thông báo lỗi và commit message viết tiếng Việt có dấu.

---

## 6. Hướng dẫn viết API cho mọi người

### 6.1. Các class dùng chung (đã có trên `main`, pull về dùng)

| Class | Cách dùng |
|---|---|
| `shared/dto/ApiResponse` | Mọi API trả `ApiResponse.ok(data)` hoặc `ApiResponse.error(msg)` |
| `shared/dto/PageResponse` | Dùng cho API phân trang |
| `shared/exception/BusinessException` | Ném từ service khi vi phạm nghiệp vụ, ví dụ `throw BusinessException.notFound("Phòng")` |
| `shared/exception/GlobalExceptionHandler` | Tự đổi exception thành response chuẩn (sai phương thức, sai kiểu, thiếu tham số trả 405/415/400; trùng dữ liệu trả 409) |
| `shared/security/AuthUser` | Người đang đăng nhập, lấy trong controller bằng `@AuthenticationPrincipal AuthUser user` |
| `ActivityLogService.log(userId, "HANH_DONG", "mô tả", ip)` | Ghi nhật ký thao tác từ chức năng của mình |
| `shared/controller/PingController` | Controller mẫu, thử bằng `GET /api/ping` |

### 6.2. Bật bảo mật và phân quyền

- MẶC ĐỊNH đang MỞ (`app.security.enabled=false`): test API KHÔNG cần token, `@PreAuthorize` tạm bị bỏ qua.
- Bật `app.security.enabled=true` trong `application-local.properties` để bắt buộc đăng nhập (dùng khi demo/bảo vệ).
- Test khi đã bật: mở Swagger, gọi `POST /api/auth/login` với `admin@minihouse.local` / `Admin@123`, copy `accessToken`, bấm Authorize và dán token. Ngoài Swagger thì gửi header `Authorization: Bearer <token>`.
- Phân quyền theo vai trò (`LANDLORD`, `MANAGER`, `CASHIER`, `METER_READER`, `TENANT`): gắn `@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")` lên hàm controller.
- Khóa tài khoản và đổi vai trò có hiệu lực NGAY: mỗi request đều tra trạng thái và vai trò hiện tại trong DB, không lấy từ token.
- Khi viết test `@WebMvcTest` có `@Import(SecurityConfig.class)` phải thêm `@MockBean UserStatusChecker` (xem `StaffControllerSecurityTest` làm mẫu).
- Thiết kế chi tiết: `docs/thiet-ke/dang-nhap-jwt.md`.

### 6.3. API của người thuê (UC36, UC37, UC94...)

- Đường dẫn đặt dưới `/api/me/**`, ví dụ `/api/me/invoices`.
- Lấy mã khách thuê bằng `Integer tenantId = CurrentUser.requireTenantId(user)`, TUYỆT ĐỐI không nhận `tenantId` từ client (nếu không người này xem được dữ liệu người khác).
- Khi bật bảo mật: `/api/me/**` chỉ người thuê vào được, mọi đường dẫn khác chỉ nhân viên vào được (người thuê bị chặn mặc định, không cần gắn gì).
- Khi để mặc định (mở) thì không có token nên `user` là null: hãy test API người thuê bằng token người thuê (bật bảo mật), nếu không sẽ nhận 401.
- Cách tài khoản người thuê hoạt động: tài khoản là 1 dòng `users` có `phone_number` TRÙNG với `tenants.phone_number` của khách thuê; chủ trọ/quản lý cấp bằng `POST /api/tenants/{tenantId}/account` (trả mật khẩu tạm 1 lần); token đăng nhập có thêm `tenantId`.
- Khi Chi viết xong `TenantService`, đổi `TenantReadRepository` (package `phi`) sang gọi service đó. Nếu đổi số điện thoại khách thuê phải cập nhật luôn tài khoản `users` tương ứng.
- Chi tiết: `docs/thiet-ke/tai-khoan-nguoi-thue.md`.

### 6.4. Thêm job tự động

Viết một class `@Component implements ScheduledJob` (tên, mô tả, cron mặc định, hàm `run()`). Khung `JobScheduler` tự tìm và lên lịch, không phải đăng ký ở đâu. Chi tiết ở mục 7.2.

### 6.5. Xuất Excel/PDF

Tạo `TableData(tiêuĐề, tênCột, cácDòng)` rồi gọi `TableExporter.toExcel(...)` hoặc `toPdf(...)`. Chi tiết và ví dụ ở mục 7.2.

---

## 7. Các API đã có (phần của Phi)

Mọi API đều xem được trong Swagger.

### 7.1. Tài khoản và phân quyền (UC1-13)

| API | Ai gọi được | Mô tả |
|---|---|---|
| `POST /api/auth/login` | Công khai | Đăng nhập bằng email hoặc SĐT, trả JWT |
| `POST /api/auth/logout` | Đã đăng nhập | Ghi nhật ký đăng xuất (client tự bỏ token) |
| `GET /api/auth/me` | Đã đăng nhập | Thông tin người đang đăng nhập |
| `POST /api/auth/register` | Công khai | UC1: đăng ký chủ trọ (xem 7.2) |
| `POST /api/auth/verify-email`, `/resend-verification` | Công khai | UC2: xác thực email bằng OTP |
| `POST /api/auth/forgot-password`, `/reset-password` | Công khai | UC4: quên mật khẩu |
| `POST /api/auth/password` | Đã đăng nhập | UC5: đổi mật khẩu (từ 8 ký tự, có cả chữ và số) |
| `GET/PUT /api/auth/profile` | Đã đăng nhập | UC6: xem/sửa họ tên và SĐT |
| `PUT/DELETE /api/auth/avatar`, `GET /api/auth/avatars/{tên}` | Đã đăng nhập (xem ảnh: công khai) | UC7: ảnh đại diện |
| `POST /api/staff` | LANDLORD | UC8: thêm nhân viên (trả mật khẩu tạm 1 lần) |
| `GET /api/staff?keyword=&status=&page=&size=` | LANDLORD, MANAGER | UC9: tìm nhân viên |
| `PUT /api/staff/{id}` | LANDLORD | UC9: sửa thông tin và thay vai trò |
| `PATCH /api/staff/{id}/status` | LANDLORD | UC9: khóa/mở khóa, body `{"status":"LOCKED"}` hoặc `{"status":"ACTIVE"}` |
| `GET /api/tenants/{tenantId}/account` | LANDLORD, MANAGER | Xem khách thuê đã có tài khoản đăng nhập chưa (`exists`) và đang `ACTIVE` hay `LOCKED` |
| `POST /api/tenants/{tenantId}/account` | LANDLORD, MANAGER | UC10: cấp tài khoản người thuê |
| `PATCH /api/tenants/{tenantId}/account/status` | LANDLORD, MANAGER | Khóa/mở khóa tài khoản người thuê (ví dụ khách trả phòng) |
| `GET /api/permissions` | Nhân viên | UC11-12: danh mục quyền |
| `GET /api/roles/permissions` | LANDLORD, MANAGER | UC12: quyền mặc định của từng vai trò |
| `GET /api/auth/permissions` | Đã đăng nhập | UC12: quyền của chính mình |
| `GET /api/activity-logs?userId=&actionType=&from=&to=&page=&size=` | LANDLORD | UC13: nhật ký của mọi người |
| `GET /api/activity-logs/export?format=xlsx\|pdf` | LANDLORD | Xuất nhật ký ra file (tối đa 5000 dòng mới nhất) |
| `GET /api/auth/activity` | Đã đăng nhập | Nhật ký của chính mình |

Ghi chú:
- Người thuê KHÔNG tự đổi được số điện thoại vì tài khoản nối với hồ sơ khách thuê bằng số này.
- Hồ sơ: không gửi `phoneNumber` = giữ nguyên số cũ; gửi chuỗi rỗng = xóa số (không xóa được nếu tài khoản chưa có email, vì sẽ hết cách đăng nhập).
- Vai trò nhân viên hợp lệ: `MANAGER`, `CASHIER`, `METER_READER`. Không thao tác được trên tài khoản chủ trọ/người thuê (trả 404) và không tự khóa chính mình.
- Các hành động được ghi nhật ký: LOGIN, LOGOUT, CHANGE_PASSWORD, UPDATE_PROFILE, CREATE_STAFF, UPDATE_STAFF, LOCK_USER, UNLOCK_USER, CREATE_TENANT_ACCOUNT, RESET_PASSWORD, VERIFY_EMAIL, UPDATE_AVATAR, REMOVE_AVATAR, các thao tác tài khoản ngân hàng, BACKUP_DB.
- Chi tiết thiết kế: `docs/thiet-ke/quan-ly-tai-khoan.md`.

### 7.2. Tính năng dùng chung (UC1, 2, 4, 7, 12, 119, 120, 122 và khung xuất file)

**Đăng ký, xác thực email, quên mật khẩu (UC1, 2, 4).** Mã OTP 6 số, hiệu lực 10 phút, dùng một lần, xin lại cách nhau 60 giây, đoán sai một lần là mã bị hủy.
- `POST /api/auth/register` (họ tên, email, mật khẩu, SĐT tùy chọn): tạo tài khoản chủ trọ ở trạng thái `INACTIVE` và gửi mã. `POST /api/auth/verify-email` (email, code): nhập đúng mã thì thành `ACTIVE`. Đăng ký lại bằng email đang chờ xác thực sẽ ghi đè tên và mật khẩu, để người chiếm email trước không giữ được mật khẩu.
- `POST /api/auth/forgot-password` (email hoặc SĐT): luôn trả cùng một thông báo dù tài khoản có tồn tại hay không. `POST /api/auth/reset-password` (identifier, code, newPassword).
- Gửi mã đi qua `shared/notification/NotificationSender`. Bản mặc định `LogNotificationSender` chỉ ghi nội dung ra log (tìm dòng `THONG BAO GIA LAP`). Muốn gửi thật: thêm một class `@Component @Primary` implements `NotificationSender`, không cần sửa chỗ gọi.
- Mã lưu ở bảng `user_verifications`; job `purge-expired-verifications` dọn mã hết hạn mỗi ngày.

**Ảnh đại diện và lưu file (UC7).** Form-data, trường `file`, PNG/JPG/WebP, tối đa 2 MB. File lưu trong thư mục `app.upload.dir` (mặc định `uploads/`, đã git ignore). Chức năng khác cần upload ảnh thì dùng `FileStorage.saveImage(thưMục, byte[], tốiĐa)`: nhận dạng ảnh theo nội dung file, tên file ngẫu nhiên, chống đường dẫn lạ.

**Tài khoản ngân hàng và QR (UC119).** Ghi: LANDLORD, MANAGER.

| API | Mô tả |
|---|---|
| `GET/POST /api/properties/{propertyId}/bank-accounts` | Xem/thêm tài khoản của khu trọ |
| `PUT /api/bank-accounts/{id}` | Sửa |
| `PUT /api/bank-accounts/{id}/default` | Đặt làm mặc định |
| `DELETE /api/bank-accounts/{id}` | Ngừng dùng (không xóa dòng vì giao dịch có thể tham chiếu) |
| `PUT /api/bank-accounts/{id}/qr`, `GET /api/bank-accounts/qr/{tên}` | Đặt/xem ảnh QR |

Mỗi khu trọ có đúng một tài khoản mặc định; tài khoản đầu tiên tự thành mặc định.

**Quyền chi tiết (UC12).** Đây là bảng tra cứu để giao diện hiện/ẩn chức năng. Việc chặn API thật vẫn là `@PreAuthorize` theo vai trò. Muốn đổi quyền của một người thì đổi vai trò của họ (`PUT /api/staff/{id}`).

**Job tự động (UC120).** Khung ở `shared/scheduler`.
- Thêm job: viết một class `@Component implements ScheduledJob`, khung tự tìm và lên lịch.
- Mặc định TẮT (`app.jobs.enabled=false`), bật khi demo hoặc chạy thật. Đổi giờ chạy bằng `app.jobs.<tên-job>.cron` trong `application-local.properties`.
- LANDLORD xem và chạy tay job: `GET /api/admin/jobs`, `POST /api/admin/jobs/{tên}/run`.
- Mỗi job chỉ chạy một bản cùng lúc; job lỗi được ghi nhận mà không làm hỏng job khác.
- Nhánh của Việt hiện dùng `@Scheduled` riêng (`VietJobs`), vẫn chạy được; chuyển sang `ScheduledJob` thì xem và chạy tay được từ một chỗ.

**Sao lưu, phục hồi (UC122).** Chỉ LANDLORD.
- `POST /api/admin/backups`: sao lưu ngay, tạo file `mbh-yyyyMMdd-HHmmss.bak`. `GET /api/admin/backups`: danh sách.
- `POST /api/admin/backups/{tên}/restore` với `{"confirm":"<tên CSDL>"}`: ghi đè TOÀN BỘ dữ liệu hiện tại và ngắt mọi kết nối đang mở.
- File do chính SQL Server ghi nên thư mục phải là nơi dịch vụ SQL Server ghi được. Mặc định dùng thư mục sao lưu của SQL Server, đổi bằng `app.backup.dir`. Ứng dụng và SQL Server phải chạy cùng máy.
- Chức năng phục hồi chưa được thử trên DB thật: hãy thử trên DB tạm trước khi dùng.

**Xuất Excel/PDF (khung dùng chung).** Tạo `TableData(tiêuĐề, tênCột, cácDòng)` rồi gọi `TableExporter.toExcel(...)` hoặc `toPdf(...)`, trả về trong controller với `TableExporter.EXCEL_TYPE` / `PDF_TYPE`. Ví dụ đầy đủ: `ActivityLogController.export`. Tối đa 50.000 dòng. PDF cần font Unicode để hiện tiếng Việt có dấu: tự tìm Arial (Windows, Mac) hoặc DejaVu Sans (Linux), hoặc chỉ định bằng `app.export.pdf-font`.

---

### 7.3. Hợp đồng thuê phòng (UC41-51)

Nhân viên (LANDLORD, MANAGER) quản lý; người thuê xem và ký hợp đồng của mình qua `/api/me/contracts`. Mọi thay đổi quan trọng ghi vào `contract_adjustments` (xem được ở chi tiết hợp đồng). Phòng và khách thuê đọc qua EntityManager vì Chi chưa có service.

| API | Mô tả |
|---|---|
| `POST /api/contracts` | UC41: lập hợp đồng (phòng phải VACANT, giá và cọc mặc định lấy từ phòng, chu kỳ 1 tháng, hạn ngày 5), phòng chuyển RENTED |
| `GET /api/contracts?keyword=&status=&page=&size=` | UC47: tra cứu theo mã HĐ, tên khách, mã phòng |
| `GET /api/contracts/{id}` | UC47: chi tiết kèm lịch sử thay đổi |
| `PUT /api/contracts/{id}/pricing` | UC42: giá thuê, tiền cọc, chu kỳ, ngày hạn |
| `PUT /api/contracts/{id}/payment-terms` | UC43: phạt trễ hạn mỗi ngày, điều kiện hoàn cọc |
| `PUT /api/contracts/{id}/utility-terms` | UC44: điều khoản điện nước |
| `GET /api/contracts/{id}/pdf` | UC45: tải PDF hợp đồng theo mẫu (tạo lúc tải, dùng font như mục xuất PDF) |
| `POST /api/contracts/{id}/send` | UC46: gửi thông báo trong app cho khách (cần khách đã có tài khoản UC10) |
| `POST /api/contracts/{id}/sign` | UC46: nhân viên ký thay (form-data `file` là ảnh chữ ký); chỉ ký 1 lần |
| `POST /api/me/contracts/{id}/sign` | UC46: người thuê tự ký |
| `PUT /api/contracts/{id}/extend` | UC48: gia hạn (ngày mới phải sau ngày hết hạn hiện tại) |
| `PATCH /api/contracts/{id}` | UC49: đổi giá thuê hoặc đổi người đứng tên (đổi người thì chữ ký bị xóa, phải ký lại) |
| `POST /api/contracts/{id}/transfer` | UC50: chuyển phòng; gửi thêm `periodId`, `closingElectric`, `closingWater` để chốt chỉ số phòng cũ |
| `POST /api/contracts/{id}/terminate` | UC51: chấm dứt kèm lý do, phòng về VACANT, ghi lịch sử lưu trú |
| `GET /api/me/contracts`, `GET /api/me/contracts/{id}` | UC37: người thuê xem hợp đồng của mình |

### 7.4. Tính tiền hàng tháng (UC65-75)

Chỉ nhân viên (LANDLORD, MANAGER). Một kỳ = một khu trọ + một tháng (`YYYY-MM`). Kỳ DRAFT tính và tính lại thoải mái; duyệt (APPROVED) xong thì khóa. UC77 phát hành hóa đơn là `POST /api/invoices/batch` của Toàn, chỉ chạy khi kỳ đã duyệt. Mọi con số lấy từ các dòng `fee_item_details`, các cột tiền phòng/điện/nước/dịch vụ/phụ thu/giảm trừ/phạt/tổng ở `calculated_monthly_fees` luôn được tính lại từ các dòng đó nên sửa tay một dòng không làm lệch tổng.

| API | Mô tả |
|---|---|
| `POST /api/billing/periods` | UC66: tạo kỳ `{propertyId, monthYear}`, trả danh sách hợp đồng có hiệu lực trong tháng |
| `GET /api/billing/periods?propertyId=`, `GET /api/billing/periods/{id}` | Danh sách kỳ, bảng tính của kỳ |
| `GET /api/billing/periods/{id}/validate` | UC67: thiếu chỉ số, chỉ số chưa xác nhận, thiếu đơn giá |
| `POST /api/billing/periods/{id}/calculate` | UC68-73: tính tiền toàn kỳ (xóa bảng tính cũ rồi tính lại). Trả kèm cảnh báo |
| `GET /api/billing/fees/{feeId}` | Chi tiết các dòng phí của một phòng |
| `PUT /api/billing/fee-items/{detailId}` | UC74: sửa số lượng/đơn giá một dòng (dòng giảm trừ phải có đơn giá âm) |
| `GET /api/billing/periods/{id}/anomalies` | UC75: phòng vượt 150% trung bình các kỳ trước |
| `POST /api/billing/periods/{id}/approve` | UC75: duyệt kỳ |
| `POST /api/billing/fees/{feeId}/items` | Thêm một khoản tay vào bảng tính của phòng (kỳ Nháp); số âm là giảm trừ. Khoản này được **giữ lại khi tính lại kỳ** (tên có tiền tố `✎ `) |
| `DELETE /api/billing/fee-items/{detailId}` | Xóa một dòng (kể cả dòng tự tính, tính lại kỳ thì dòng tự tính hiện lại), chỉ kỳ Nháp |
| `GET /api/billing/service-preview?chargeType=&unitPrice=` | Xem trước một dịch vụ cộng thêm bao nhiêu mỗi tháng cho cả khu (theo hợp đồng đang hiệu lực) |

Số lượng thật cho dịch vụ: **gửi xe** = số xe đang hoạt động của khách (đăng ký ở màn Khách thuê), **theo đầu người** = 1 người đứng tên hợp đồng + số người ở cùng đã khai (màn Phòng). Chưa khai thì tính 1 người và không tính tiền gửi xe, nên cần cập nhật hồ sơ khi có người hoặc xe mới. Khi tính lại kỳ, khoản thêm tay được giữ, chỉnh sửa tay trên dòng tự tính thì mất.

Cách tính: tiền phòng theo số ngày thực tế trong tháng (vào hoặc trả giữa tháng thì chia theo ngày, làm tròn đồng); điện nước = chỉ số trong kỳ ghi chỉ số của tháng đó nhân đơn giá hiệu lực; dịch vụ theo `property_service_rates` (cố định mỗi phòng, theo số người ở, theo số xe; loại `PER_METER_UNIT` chưa tự tính); phụ thu/giảm trừ từ `room_custom_rates`; phạt trễ hạn = số ngày quá hạn của các khoản nợ chưa tất toán nhân `late_fee_per_day`. UC65 và UC72 (điện nước chung của khu): ghi các khoản chi nhóm "Điện nước chung" bằng `/api/expenses` của Việt, hệ thống cộng các khoản trong tháng và chia cho các phòng theo số người ở.

### 7.4b. Xe và người ở cùng

Nguồn số lượng thật cho dịch vụ gửi xe và dịch vụ theo đầu người. Xem: mọi nhân viên; thêm, sửa, xóa: LANDLORD, MANAGER. Xóa chỉ là ngừng dùng (giữ lịch sử).

| API | Mô tả |
|---|---|
| `GET/POST /api/tenants/{tenantId}/vehicles` | Danh sách xe đang hoạt động, đăng ký xe `{licensePlate, vehicleType, brand, color}` (biển số không trùng; xe đã bỏ đăng ký lại được) |
| `PUT/DELETE /api/vehicles/{id}` | Sửa xe, bỏ xe |
| `GET/POST /api/rooms/{roomId}/residents` | Danh sách người ở cùng, thêm người ở cùng (không vượt số người tối đa của phòng, tính cả người đứng tên) |
| `PUT/DELETE /api/residents/{id}` | Sửa, cho rời phòng |

### 7.5. Thanh toán hóa đơn (UC85-88)

Nhân viên thu ngân (LANDLORD, MANAGER, CASHIER). Cần đăng nhập để biết ai nhận tiền. Giao dịch chờ xác nhận được lưu ở trạng thái `FAILED` (DB chỉ có SUCCESS/FAILED/REVERSED) và chỉ cộng vào hóa đơn khi chuyển SUCCESS. Trả dư thì dùng chức năng thanh toán dư của Toàn.

| API | Mô tả |
|---|---|
| `POST /api/payments` | UC85: ghi nhận đã nhận tiền mặt hoặc chuyển khoản `{invoiceId, method: CASH/BANK_TRANSFER, amount, transactionCode?}`, cộng vào hóa đơn ngay |
| `POST /api/payments/qr` | UC86: tạo giao dịch chờ, trả tài khoản ngân hàng mặc định (UC119), ảnh QR và nội dung chuyển khoản (= mã hóa đơn) |
| `POST /api/me/payments/qr` | UC86: người thuê tự lấy mã QR cho hóa đơn của mình |
| `POST /api/payments/callback` | UC86: cổng thanh toán gọi về, kiểm tra header `X-Callback-Secret` = `app.payment.callback-secret` (để trống = tắt) |
| `POST /api/payments/match` | UC88: tìm hóa đơn có mã nằm trong nội dung chuyển khoản |
| `POST /api/payments/{id}/confirm` | UC88: nhập số tiền thực nhận trên sao kê; khớp thì cộng vào hóa đơn, lệch thì giữ nguyên chờ |
| `GET /api/payments?invoiceId=&keyword=&status=`, `GET /api/payments/{id}` | UC87: tra cứu giao dịch |

### 7.6. Dashboard và báo cáo (UC116, UC118)

LANDLORD, MANAGER. `propertyId` bỏ trống là gộp mọi khu trọ.

| API | Mô tả |
|---|---|
| `GET /api/dashboard?propertyId=` | Số phòng (đang thuê, trống), tỷ lệ lấp đầy, doanh thu/đã lập hóa đơn/chi phí của tháng này, tổng công nợ, số hóa đơn chưa trả và quá hạn, hợp đồng sắp hết hạn |
| `GET /api/reports/revenue?propertyId=&year=` | Doanh thu, chi phí, lợi nhuận 12 tháng. Doanh thu = các giao dịch SUCCESS theo ngày thanh toán |
| `GET /api/reports/revenue/export?propertyId=&year=&format=xlsx\|pdf` | Xuất báo cáo ra Excel hoặc PDF |

## 8. Cấu hình

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

## 9. Giao diện Stayly

Người làm toàn bộ giao diện (frontend): Nguyễn Anh Phi (TH08326).

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
- Trạng thái thuê của phòng và người đang ở lấy từ hợp đồng thật (danh sách phòng, khách gốc vẫn là dữ liệu mẫu cho tới khi Chi có API).
- Phần của Chi: màn hình Phòng và Khách thuê, cùng danh sách chọn phòng/khách khi lập hợp đồng, đang dùng dữ liệu mẫu trong `frontend/src/data/sample.ts` và có nhãn "Dữ liệu mẫu". Khi Chi có API, chỉ cần thay dữ liệu trong file đó bằng lời gọi API.
- Các màn chốt tháng (Chỉ số, Tính tiền, Hóa đơn, Thanh toán, Công nợ) có thanh "Tuyến tháng" thu gọn ở đầu trang, chặng đang xem được gạch chân.
- Hủy giao dịch nằm trong hộp chi tiết giao dịch, chấm dứt hợp đồng nằm trong "vùng nguy hiểm" của hộp hợp đồng, để tránh bấm nhầm.
- Duyệt kỳ tính tiền sẽ tự đóng kỳ ghi chỉ số cùng tháng. Công nợ tự cập nhật khi mở trang. Trên điện thoại (≤700px) bảng chuyển thành thẻ.
- Backend bật nén dữ liệu (`server.compression.enabled=true`) để giao diện tải nhanh hơn.
- Giao diện đang dùng một khu trọ (`PROPERTY_ID = 1` trong `frontend/src/lib/api.ts`).
- Hóa đơn và chỉ số của Toàn trả JSON thô (không bọc `{success, data}`), client đã xử lý cả hai kiểu.
