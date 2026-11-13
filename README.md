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
│   ├── config/        SecurityConfig
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
3. Chạy `db/seed.sql` để nạp 4 vai trò, 22 quyền và tài khoản `admin@minihouse.local` / `Admin@123` (chỉ dùng dev/demo).
4. Chạy `mvn spring-boot:run` (`ddl-auto=validate` sẽ báo lỗi nếu entity lệch với DB).
5. Thử API: `http://localhost:8080/api/ping`, xem toàn bộ API tại Swagger: `http://localhost:8080/swagger-ui.html`.

Tài liệu yêu cầu (use case, phân công) nằm trong thư mục `docs/`.

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
Phân quyền theo vai trò (`LANDLORD`, `MANAGER`, `CASHIER`, `METER_READER`, `TENANT`): gắn `@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")` lên hàm controller. Thiết kế chi tiết: `docs/superpowers/specs/2026-11-14-jwt-login-design.md`.

Tài khoản người thuê (role `TENANT`): KHÔNG sửa cấu trúc DB. Tài khoản người thuê là 1 dòng `users` có `phone_number` TRÙNG với `tenants.phone_number` của khách thuê đó. Chủ trọ/quản lý cấp tài khoản bằng `POST /api/tenants/{tenantId}/account` (trả mật khẩu tạm 1 lần). Khi người thuê đăng nhập, token có thêm `tenantId`.
Quy ước cho API của người thuê (UC36, UC37, UC94...): lấy mã khách thuê từ `@AuthenticationPrincipal AuthUser user` rồi dùng `user.tenantId()`, TUYỆT ĐỐI không nhận `tenantId` từ client (nếu không người này xem được dữ liệu người khác). API của nhân viên thì gắn `@PreAuthorize` như trên. Khi Chi viết xong `TenantService`, đổi `TenantReadRepository` (package `phi`) sang gọi service đó. Nếu đổi số điện thoại khách thuê phải cập nhật luôn tài khoản `users` tương ứng. Chi tiết: `docs/superpowers/specs/2026-11-14-tenant-account-design.md`.

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
