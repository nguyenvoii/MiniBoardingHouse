# MiniBoardingHouse (Nhóm 7)

Website quản lý nhà trọ mini. Công nghệ: Java 17, Spring Boot 3, Spring Data JPA, SQL Server.

## Cấu trúc package (`com.group7.minihouse`)

Một project duy nhất cho cả nhóm. Có 1 thư mục dùng chung và mỗi người có 1 package riêng mang tên mình.

```
com.group7.minihouse
├── shared/            DÙNG CHUNG (không ai tự ý sửa, cần sửa thì báo Phi hoặc tạo PR)
│   ├── entity/        56 entity map 56 bảng trong db/group-7.sql
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

Bên trong package của mỗi người đều có 4 thư mục: `controller`, `service`, `repository`, `dto`.
Ví dụ Chi làm phòng: `chi/controller/RoomController`, `chi/service/RoomService`, `chi/repository/RoomRepository`, `chi/dto/RoomResponse`.

Quy tắc:
- Mỗi người chỉ thêm file vào package của mình, nên không bao giờ đụng file nhau khi merge.
- Cần dữ liệu của bảng người khác thì gọi Service của người đó (ví dụ Toàn cần phòng thì gọi `RoomService` trong package `chi`), không tự viết repository cho bảng người khác.
- Spring Boot tự quét toàn bộ package con, nên thêm class vào đúng thư mục là chạy, không cần cấu hình gì thêm.

## Cách chạy

1. Chạy `db/group-7.sql` trên SQL Server.
2. Tạo file `src/main/resources/application-local.properties` (file này bị git ignore):
   ```
   spring.datasource.username=...
   spring.datasource.password=...
   ```
3. Chạy `db/seed.sql` để nạp 5 vai trò (LANDLORD, MANAGER, CASHIER, METER_READER, TENANT), 22 quyền và tài khoản `admin@minihouse.local` / `Admin@123` (chỉ dùng dev/demo).
4. Chạy `mvn spring-boot:run` (`ddl-auto=validate` sẽ báo lỗi nếu entity lệch với DB).
5. Thử API: `http://localhost:8080/api/ping`, xem toàn bộ API tại Swagger: `http://localhost:8080/swagger-ui.html`.

Tài liệu yêu cầu (use case, phân công) nằm trong thư mục `docs/`. Các bản thiết kế (lý do chọn cách làm đăng nhập, tài khoản người thuê, quản lý tài khoản) nằm trong `docs/thiet-ke/`.

## Phân công theo use case

Nguồn chính thức là file `docs/Nhom_7_47_usecase_khutro_4MM_PhanCongMoi.xlsx` (sheet "Phân Công", 123 use case, 663.8 giờ). Bảng dưới đây chỉ là bản tóm tắt, nếu lệch thì lấy Excel làm chuẩn. Cách chia này khớp với mục "Phân công theo nhóm bảng" bên dưới.

| Thành viên | Số UC | Giờ | Use case |
|---|---|---|---|
| Nguyễn Anh Phi | 45 | 235.2 | 1-13, 41-51, 65-75, 77, 85-88, 116, 118-120, 122 |
| Nguyễn Trần Bảo Chi | 29 | 146.3 | 14-40, 108-109 |
| Phạm Khánh Toàn | 24 | 137.3 | 53-64, 76, 78-84, 89-92 |
| Vũ Văn Quốc Việt | 25 | 145.0 | 52, 93-107, 110-115, 117, 121, 123 |

## Tiến độ từng người

Cập nhật ngày 16/11/2026, theo những gì thực sự có trên GitHub (nhánh `main` và các nhánh con). Ai đẩy code mới thì tự sửa dòng của mình trong bảng này.

| Thành viên | Được giao | Đã có trên `main` | Đang chờ / việc tiếp theo |
|---|---|---|---|
| Nguyễn Anh Phi | 45 UC | 16 UC: 1-13 (tài khoản, đăng nhập, phân quyền), 119, 120, 122, cộng khung xuất Excel/PDF | Hợp đồng 41-51, tính tiền tháng 65-77, thanh toán 85-88, 116, 118: phụ thuộc phòng, khách thuê, chỉ số, đơn giá, hóa đơn của Chi và Toàn nên chờ họ đẩy code |
| Nguyễn Trần Bảo Chi | 29 UC | 0 | Chưa có code nào trên GitHub. Nhiều phần của Phi và Toàn đang chờ `RoomService`, `TenantService` của Chi |
| Phạm Khánh Toàn | 24 UC | 0 | Đã gửi bản zip, Phi đã review. Cần sửa theo `docs/LOI_CAN_SUA_CODE_TOAN.md` (đưa vào package `toan`, bỏ entity trùng, dùng `BusinessException`) rồi đẩy bằng nhánh riêng và Pull Request theo `docs/HUONG_DAN_GIT.md` |
| Vũ Văn Quốc Việt | 25 UC | 0 (đã làm trên nhánh) | Nhánh `viet/boarding-house-management` đã có công nợ, trả phòng, bảo trì, thiết bị phòng, chi phí vận hành, thông báo. Chưa vào `main`, kiểm tra thấy gộp với `main` không xung đột, cần tạo Pull Request |

Chi tiết phần của Phi:
- Đã làm: UC1 (đăng ký chủ trọ), UC2 (xác thực email bằng OTP), UC3 (đăng nhập, đăng xuất), UC4 (quên mật khẩu), UC5 (đổi mật khẩu), UC6 (hồ sơ cá nhân), UC7 (ảnh đại diện), UC8, UC9 (quản lý nhân viên), UC10 (cấp tài khoản người thuê), UC11 (phân quyền theo vai trò), UC12 (bảng quyền chi tiết theo vai trò), UC13 (nhật ký thao tác), UC119 (tài khoản ngân hàng, mã QR), UC120 (khung job tự động), UC122 (sao lưu, phục hồi), khung xuất Excel/PDF.
- Chưa làm (chờ phần của người khác): hợp đồng (UC41-51), tính tiền tháng (UC65-77), thanh toán (UC85-88), UC116, UC118 (dashboard, báo cáo).
- Chưa gửi email/SMS thật: mã OTP hiện chỉ in ra log của ứng dụng (xem mục "Tính năng dùng chung").

## Phân công theo nhóm bảng (phân công lại)

Nguyên tắc: Nguyễn Anh Phi giữ toàn bộ khung xương của dự án và các phần khó nhất (tính tiền tháng, hợp đồng, thanh toán, phân quyền). 3 người còn lại chia đều số bảng, mỗi người một cụm nghiệp vụ độc lập để làm đồng thời được.

Mỗi người viết repository, service, controller, dto cho các bảng của mình, đặt trong package mang tên mình (entity đã sinh sẵn trong `shared/entity`, chỉ cần rà soát lại). Đặt tên file theo tên entity, ví dụ `RoomRepository`, `RoomService`, `RoomController`.

| Thành viên | Mã SV | Số bảng | Vai trò |
|---|---|---|---|
| Nguyễn Anh Phi | TH08326 | 13 | Khung xương + phần khó: `config`, `exception`, `util`, dto chung, scheduler, xuất Excel/PDF, đăng nhập - phân quyền, tính tiền tháng, hợp đồng, thanh toán |
| Nguyễn Trần Bảo Chi | TH08666 | 14 | Phòng - khách thuê - yêu cầu thuê |
| Phạm Khánh Toàn | TH08433 | 15 | Điện nước - đơn giá - hóa đơn - thu tiền |
| Vũ Văn Quốc Việt | TH08548 | 14 | Công nợ - trả phòng - sửa chữa - thông báo - chi phí |

### 1. Nguyễn Anh Phi (13 bảng + toàn bộ khung xương)
Khung xương (không gắn với bảng nào):
- `config`: Spring Security, đăng nhập, JWT/session, phân quyền theo Role/Permission, CORS
- `exception`: `BusinessException`, `GlobalExceptionHandler`, mã lỗi thống nhất
- `dto` chung: `ApiResponse<T>`, `PageResponse<T>`, base DTO
- `util`: upload ảnh, gửi email, OTP SMS, tiện ích ngày giờ - tiền tệ
- Scheduler: tự động chốt chỉ số, tính tiền, nhắc nợ (khung chạy job, Toàn và Việt viết logic bên trong)
- Xuất Excel/PDF (khung dùng chung), dashboard - báo cáo
- Sao lưu phục hồi dữ liệu (usecase 122)

Các bảng:
- Người dùng - phân quyền: `User`, `Role`, `Permission`, `UserRole` (+ `UserRoleId`), `UserVerification`, `UserActivityLog`
- Hợp đồng: `RentalContract`, `ContractAdjustment`
- Tính tiền tháng: `MonthlyBillingPeriod`, `CalculatedMonthlyFee`, `FeeItemDetail`
- Thanh toán: `PaymentTransaction`, `BankAccount`

### 2. Nguyễn Trần Bảo Chi (14 bảng)
- Khu trọ - phòng: `Property`, `PropertyFloor`, `Room`, `RoomMedia`, `RoomFurnishing`, `RoomEquipmentRequest`, `RoomAddonFee`
- Khách thuê - cư dân: `Tenant`, `TenantIdCard`, `RoomResident`, `ResidentStayHistory`, `TenantVehicle`
- Yêu cầu thuê - mẫu hợp đồng: `BookingRequest`, `ContractTemplate`

### 3. Phạm Khánh Toàn (15 bảng)
- Chỉ số điện nước: `UtilityReadingPeriod`, `MeterReading`, `MeterPhoto`, `MeterAdjustmentLog`, `MeterAnomalyAlert`
- Đơn giá - phí dịch vụ: `ServiceRatePlan`, `PropertyServiceRate`, `RoomCustomRate`, `RateChangeHistory`
- Hóa đơn: `Invoice`, `InvoiceNotification`
- Thu tiền - cấn trừ: `ReceiptVoucher`, `PartialPaymentLog`, `TenantPrepayment`, `PrepaymentDeduction`

### 4. Vũ Văn Quốc Việt (14 bảng)
- Công nợ: `TenantDebt`, `DebtPenaltyLog`, `DebtReminder`, `DebtProcessingLog`
- Trả phòng - hoàn cọc: `CheckoutRequest`, `CheckoutInspection`, `DepositRefundVoucher`
- Sự cố sửa chữa: `MaintenanceRequest`, `MaintenanceLog`, `IncidentCategory`
- Thông báo: `ContractNotification`, `NotificationTemplate`, `UserNotification`
- Chi phí vận hành: `OperatingExpense`

### Tổng kết

Phi 13 + Chi 14 + Toàn 15 + Việt 14 = 56 bảng, bằng số entity trong package `shared/entity`.

### Cách làm đồng thời, không chờ nhau

Trạng thái giai đoạn 0: ĐÃ XONG (Phi). Các class dùng chung đã có trên `main`, hãy pull về dùng:
- `shared/dto/ApiResponse`: mọi API trả `ApiResponse.ok(data)` hoặc `ApiResponse.error(msg)`
- `shared/dto/PageResponse`: dùng cho API phân trang
- `shared/exception/BusinessException`: ném từ service khi vi phạm nghiệp vụ, ví dụ `throw BusinessException.notFound("Phong")`
- `shared/exception/GlobalExceptionHandler`: tự đổi exception thành response chuẩn
- `shared/config/SecurityConfig`: đăng nhập JWT. MẶC ĐỊNH ĐANG MỞ (`app.security.enabled=false`): test API KHÔNG cần token, `@PreAuthorize` tạm bị bỏ qua. Bật `app.security.enabled=true` (trong `application-local.properties`) để bắt buộc đăng nhập, dùng khi demo/bảo vệ
- `shared/security/AuthUser`: người đang đăng nhập, lấy trong controller bằng `@AuthenticationPrincipal AuthUser user`
- `shared/controller/PingController`: ví dụ controller mẫu, thử bằng `GET /api/ping`

Cách test API khi đã bật `app.security.enabled=true`: mở Swagger `http://localhost:8080/swagger-ui.html`, gọi `POST /api/auth/login` với `admin@minihouse.local` / `Admin@123`, copy `accessToken`, bấm nút Authorize và dán token. Ngoài Swagger thì gửi header `Authorization: Bearer <token>`.
Phân quyền theo vai trò (`LANDLORD`, `MANAGER`, `CASHIER`, `METER_READER`, `TENANT`): gắn `@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")` lên hàm controller. Thiết kế chi tiết: `docs/thiet-ke/dang-nhap-jwt.md`.

Tài khoản người thuê (role `TENANT`): KHÔNG sửa cấu trúc DB. Tài khoản người thuê là 1 dòng `users` có `phone_number` TRÙNG với `tenants.phone_number` của khách thuê đó. Chủ trọ/quản lý cấp tài khoản bằng `POST /api/tenants/{tenantId}/account` (trả mật khẩu tạm 1 lần). Khi người thuê đăng nhập, token có thêm `tenantId`.
Quy ước cho API của người thuê (UC36, UC37, UC94...): đặt đường dẫn dưới `/api/me/**` (ví dụ `/api/me/invoices`), lấy mã khách thuê bằng `Integer tenantId = CurrentUser.requireTenantId(user)` với `user` là `@AuthenticationPrincipal AuthUser user`, TUYỆT ĐỐI không nhận `tenantId` từ client (nếu không người này xem được dữ liệu người khác). Khi bật `app.security.enabled=true`: `/api/me/**` chỉ người thuê vào được, MỌI đường dẫn khác chỉ nhân viên vào được (người thuê bị chặn mặc định, không cần gắn gì). Khi để mặc định (mở) thì không có token nên `user` là null: hãy test API người thuê bằng token người thuê (bật `app.security.enabled=true`), nếu không sẽ nhận 401. API của nhân viên thì gắn `@PreAuthorize` như trên để thu hẹp thêm trong nhóm nhân viên. Khi Chi viết xong `TenantService`, đổi `TenantReadRepository` (package `phi`) sang gọi service đó. Nếu đổi số điện thoại khách thuê phải cập nhật luôn tài khoản `users` tương ứng. Chi tiết: `docs/thiet-ke/tai-khoan-nguoi-thue.md`.

Quản lý tài khoản (UC3, 5, 6, 8, 9, 13), mọi API đều có trong Swagger:
- `POST /api/auth/password` đổi mật khẩu (mật khẩu mới từ 8 ký tự, có cả chữ và số); `GET/PUT /api/auth/profile` xem/sửa họ tên và số điện thoại (NGƯỜI THUÊ không tự đổi được số điện thoại vì tài khoản nối với hồ sơ khách thuê bằng số này); `POST /api/auth/logout` ghi nhật ký đăng xuất. Cả nhóm này dùng được cho mọi người đã đăng nhập, kể cả người thuê.
- `/api/staff`: thêm nhân viên (chỉ LANDLORD, trả mật khẩu tạm 1 lần), tìm kiếm có phân trang `GET /api/staff?keyword=&status=&page=&size=` (LANDLORD, MANAGER), sửa thông tin và thay vai trò `PUT /api/staff/{id}`, khóa/mở khóa `PATCH /api/staff/{id}/status` với `{"status":"LOCKED"}` hoặc `{"status":"ACTIVE"}`. Vai trò nhân viên hợp lệ: `MANAGER`, `CASHIER`, `METER_READER`. Không thao tác được trên tài khoản chủ trọ/người thuê (trả 404) và không tự khóa chính mình.
- Khóa tài khoản và đổi vai trò có hiệu lực NGAY: mỗi request có token đều tra `users.status` và vai trò hiện tại trong DB (1 truy vấn theo khóa chính) và LẤY QUYỀN TỪ DB, không lấy từ token. Token của tài khoản bị khóa nhận 401 liền; nhân viên bị giáng chức mất quyền cũ liền, không phải đợi token hết hạn. Khi viết test `@WebMvcTest` có `@Import(SecurityConfig.class)` phải thêm `@MockBean UserStatusChecker` (xem `StaffControllerSecurityTest` làm mẫu).
- Khóa/mở khóa tài khoản NGƯỜI THUÊ (ví dụ khách trả phòng): `PATCH /api/tenants/{tenantId}/account/status` với `{"status":"LOCKED"}` hoặc `{"status":"ACTIVE"}` (LANDLORD, MANAGER), có hiệu lực ngay. Nhân viên thì dùng `/api/staff/{id}/status`.
- Hồ sơ: không gửi `phoneNumber` = giữ nguyên số cũ, gửi chuỗi rỗng = xóa số (không xóa được nếu tài khoản chưa có email, vì sẽ hết cách đăng nhập).
- Lỗi trùng email/SĐT khi hai người cùng thao tác một lúc trả 409 (không còn 500).
- Nhật ký: `GET /api/activity-logs?userId=&actionType=&from=&to=&page=&size=` (chỉ LANDLORD), `GET /api/auth/activity` (nhật ký của chính mình). Các hành động được ghi: LOGIN, LOGOUT, CHANGE_PASSWORD, UPDATE_PROFILE, CREATE_STAFF, UPDATE_STAFF, LOCK_USER, UNLOCK_USER, CREATE_TENANT_ACCOUNT, RESET_PASSWORD, VERIFY_EMAIL, UPDATE_AVATAR, REMOVE_AVATAR, các thao tác tài khoản ngân hàng, BACKUP_DB. LANDLORD xuất nhật ký ra file bằng `GET /api/activity-logs/export?format=xlsx|pdf`. Muốn ghi nhật ký từ chức năng của mình thì gọi `ActivityLogService.log(userId, "TEN_HANH_DONG", "mo ta", ip)`.
- Lỗi do người gọi API (sai phương thức HTTP, sai kiểu dữ liệu, thiếu tham số) trả 405/415/400 chứ không còn 500.
Chi tiết: `docs/thiet-ke/quan-ly-tai-khoan.md`.

### Tính năng dùng chung của Phi (UC1, 2, 4, 7, 12, 119, 120, 122 và khung xuất file)

Đăng ký và quên mật khẩu (UC1, 2, 4), đều công khai khi bật bảo mật, mã OTP 6 số, hiệu lực 10 phút, dùng một lần, xin lại cách nhau 60 giây, đoán sai một lần là mã bị hủy:
- `POST /api/auth/register` (họ tên, email, mật khẩu, số điện thoại tùy chọn): tạo tài khoản chủ trọ ở trạng thái `INACTIVE`, gửi mã xác thực. `POST /api/auth/verify-email` (email, code): nhập đúng mã thì tài khoản thành `ACTIVE`. `POST /api/auth/resend-verification`: gửi lại mã. Đăng ký lại bằng email đang chờ xác thực sẽ ghi đè tên và mật khẩu (để người chiếm email trước không giữ được mật khẩu).
- `POST /api/auth/forgot-password` (email hoặc SĐT): luôn trả cùng một thông báo dù tài khoản có tồn tại hay không. `POST /api/auth/reset-password` (identifier, code, newPassword).
- Gửi mã đi qua `shared/notification/NotificationSender`. Bản mặc định `LogNotificationSender` chỉ ghi nội dung ra log (tìm dòng `THONG BAO GIA LAP`). Muốn gửi thật: thêm một class `@Component @Primary` implements `NotificationSender`, không cần sửa chỗ gọi. Mã OTP lưu ở bảng `user_verifications`, job `purge-expired-verifications` dọn mã hết hạn mỗi ngày.

Ảnh đại diện (UC7) và lưu file: `PUT /api/auth/avatar` (form-data, trường `file`, PNG/JPG/WebP, tối đa 2 MB), `DELETE /api/auth/avatar`, xem ảnh `GET /api/auth/avatars/{tên}`. File lưu trong thư mục `app.upload.dir` (mặc định `uploads/`, đã git ignore). Chức năng khác cần upload ảnh thì dùng `FileStorage.saveImage(thưMục, byte[], tốiĐa)`: nhận dạng ảnh theo nội dung file, tên file ngẫu nhiên, chống đường dẫn lạ.

Tài khoản ngân hàng và QR (UC119): `GET/POST /api/properties/{propertyId}/bank-accounts`, `PUT /api/bank-accounts/{id}`, `PUT /api/bank-accounts/{id}/default`, `DELETE /api/bank-accounts/{id}` (ngừng dùng, không xóa dòng vì giao dịch có thể tham chiếu), `PUT /api/bank-accounts/{id}/qr` (ảnh QR), xem QR `GET /api/bank-accounts/qr/{tên}`. Mỗi khu trọ có đúng một tài khoản mặc định; tài khoản đầu tiên tự thành mặc định. Ghi: LANDLORD, MANAGER.

Quyền chi tiết (UC12): `GET /api/permissions` (danh mục quyền), `GET /api/roles/permissions` (quyền mặc định của từng vai trò, LANDLORD và MANAGER xem), `GET /api/auth/permissions` (quyền của chính mình). Đây là bảng tra cứu cho giao diện hiện/ẩn chức năng; việc chặn API thật vẫn là `@PreAuthorize` theo vai trò. Muốn đổi quyền của một người thì đổi vai trò của họ (`PUT /api/staff/{id}`).

Job tự động (UC120): khung ở `shared/scheduler`. Muốn thêm job chỉ cần viết một class `@Component implements ScheduledJob` (tên, mô tả, cron mặc định, hàm `run()`), khung tự tìm và lên lịch, không phải đăng ký ở đâu. Mặc định TẮT (`app.jobs.enabled=false`), bật khi demo/chạy thật. Đổi giờ chạy bằng `app.jobs.<tên-job>.cron` trong `application-local.properties`. LANDLORD xem và chạy tay job qua `GET /api/admin/jobs` và `POST /api/admin/jobs/{tên}/run`. Mỗi job chỉ chạy một bản cùng lúc, job lỗi được ghi nhận mà không làm hỏng job khác. Toàn và Việt viết job của mình theo cách này; nhánh của Việt hiện dùng `@Scheduled` riêng (`VietJobs`), vẫn chạy được, nhưng chuyển sang `ScheduledJob` thì xem và chạy tay được từ một chỗ.

Sao lưu, phục hồi (UC122), chỉ LANDLORD: `POST /api/admin/backups` (sao lưu ngay, file `mbh-yyyyMMdd-HHmmss.bak`), `GET /api/admin/backups` (danh sách), `POST /api/admin/backups/{tên}/restore` với `{"confirm":"<tên CSDL>"}` (ghi đè TOÀN BỘ dữ liệu hiện tại, ngắt mọi kết nối đang mở). File do chính SQL Server ghi nên thư mục phải là nơi dịch vụ SQL Server ghi được: mặc định dùng thư mục sao lưu của SQL Server, đổi bằng `app.backup.dir`. Ứng dụng và SQL Server phải chạy cùng máy. Chức năng phục hồi chưa được thử trên DB thật, hãy thử trên DB tạm trước khi dùng.

Xuất Excel/PDF (khung dùng chung): tạo `TableData(tiêuĐề, tênCột, cácDòng)` rồi gọi `TableExporter.toExcel(...)` hoặc `toPdf(...)`, trả về trong controller với `TableExporter.EXCEL_TYPE` / `PDF_TYPE`. Ví dụ đầy đủ: `ActivityLogController.export`. Tối đa 50.000 dòng. PDF cần font Unicode để hiện tiếng Việt có dấu: tự tìm Arial (Windows, Mac) hoặc DejaVu Sans (Linux), hoặc chỉ định bằng `app.export.pdf-font=đường/dẫn/font.ttf`.

Cấu hình thêm trong `application-local.properties` khi cần: `app.upload.dir`, `app.jobs.enabled`, `app.backup.dir`, `app.export.pdf-font`.

- Giai đoạn 0 (ĐÃ XONG): các class dùng chung `ApiResponse`, `BusinessException`, `GlobalExceptionHandler`, và đăng nhập JWT do Phi làm. Chi, Toàn, Việt cứ làm API của mình, gắn `@PreAuthorize` theo vai trò phù hợp.
- Trong lúc Phi làm khung, Chi, Toàn, Việt bắt đầu ngay với các bảng của mình, không cần đợi.
- Mỗi người chỉ tạo repository, service, controller trong phần bảng của mình. Cần dữ liệu của bảng người khác thì gọi qua `Service` của người đó, hoặc dùng quan hệ JPA có sẵn trong entity. Không tự viết repository cho bảng người khác.
- Các service nền tảng nhiều người cần (`RoomService`, `TenantService`, `UserService`) chủ sở hữu nên làm các hàm đọc cơ bản (`findById`, `findAll`) trước, rồi mới làm nghiệp vụ phức tạp.
- Logic trong job tự động: Toàn viết phần chốt chỉ số - tính hóa đơn, Việt viết phần nhắc nợ - phạt trễ hạn, Phi chỉ viết khung scheduler.

## Quy ước làm chung

- Mỗi thay đổi DB phải sửa `db/group-7.sql` VÀ entity tương ứng trong cùng 1 commit.
- Số class trong package `shared/entity` phải bằng số bảng trong DB.
- Mỗi người làm trên branch riêng, ví dụ `phi/room`, `chi/tenant`, `toan/invoice`, `viet/debt`.
- Pull `main` trước khi bắt đầu làm và trước khi push.
- Làm xong thì tạo Pull Request vào `main`, không push thẳng lên `main`.
- Không sửa file entity của người khác; cần sửa thì nhắn người đó hoặc tạo PR.
- Phụ thuộc giữa các package đi MỘT CHIỀU, không gọi vòng. Thứ tự: `chi` (phòng, khách thuê) → `toan` (chỉ số, đơn giá, hóa đơn, phiếu thu) → `phi` (hợp đồng, tính tiền, thanh toán) → `viet` (công nợ, trả phòng, sửa chữa). Package đứng sau được gọi Service của package đứng trước, KHÔNG được ngược lại. Ví dụ: khi tính tiền tháng, Phi gọi `MeterReadingService`/`RateService` của Toàn; khi chốt tiền, Phi gọi `InvoiceService` của Toàn để tạo hóa đơn (Toàn không gọi ngược `phi`). Nếu bắt buộc phải gọi ngược thì chuyển phần dùng chung xuống `shared` hoặc truyền dữ liệu qua tham số/ID.
- Mọi entity đã có `@DynamicInsert`: cột nào để `null` khi tạo mới thì Hibernate bỏ qua, DB tự điền giá trị `DEFAULT` (ví dụ `created_at`, `status`). Entity mới phải thêm `@DynamicInsert`.
- Mọi API trả về `ApiResponse`, dùng `BusinessException` cho lỗi nghiệp vụ, dùng `@Valid` + annotation của `spring-boot-starter-validation` để kiểm tra đầu vào.
