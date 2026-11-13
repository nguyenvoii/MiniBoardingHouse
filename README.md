# MiniBoardingHouse (Nhóm 7)

Website quản lý nhà trọ mini. Công nghệ: Java 17, Spring Boot 3, Spring Data JPA, SQL Server.

## Cấu trúc package (`com.group7.minihouse`)

- `entity`: 56 class tương ứng 56 bảng trong `db/group-7.sql` (`UserRoleId` là khóa phức hợp, không phải entity)
- `repository`: Spring Data JPA repository
- `service`: xử lý nghiệp vụ
- `controller`: REST / web controller
- `dto`: đối tượng request/response
- `config`: cấu hình (bảo mật, web...)
- `util`: hàm tiện ích
- `exception`: exception tùy chỉnh

## Cách chạy

1. Chạy `db/group-7.sql` trên SQL Server.
2. Tạo file `src/main/resources/application-local.properties` (file này bị git ignore):
   ```
   spring.datasource.username=...
   spring.datasource.password=...
   ```
3. Chạy `mvn spring-boot:run` (`ddl-auto=validate` sẽ báo lỗi nếu entity lệch với DB).

## Phân công theo nhóm bảng (phân công lại)

Nguyên tắc: Nguyễn Anh Phi giữ toàn bộ khung xương của dự án và các phần khó nhất (tính tiền tháng, hợp đồng, thanh toán, phân quyền). 3 người còn lại chia đều số bảng, mỗi người một cụm nghiệp vụ độc lập để làm đồng thời được.

Mỗi người viết repository, service, controller, dto cho các bảng của mình (entity đã sinh sẵn, chỉ cần rà soát lại). Đặt tên file theo tên entity, ví dụ `RoomRepository`, `RoomService`, `RoomController`.

| Thành viên | Mã SV | Số bảng | Vai trò |
|---|---|---|---|
| Nguyễn Anh Phi | TH08326 | 12 | Khung xương + phần khó: `config`, `exception`, `util`, dto chung, scheduler, xuất Excel/PDF, đăng nhập - phân quyền, tính tiền tháng, hợp đồng, thanh toán |
| Nguyễn Trần Bảo Chi | TH08666 | 15 | Phòng - khách thuê - yêu cầu thuê |
| Phạm Khánh Toàn | TH08433 | 15 | Điện nước - đơn giá - hóa đơn - thu tiền |
| Vũ Văn Quốc Việt | TH08548 | 14 | Công nợ - trả phòng - sửa chữa - thông báo - chi phí |

### 1. Nguyễn Anh Phi (12 bảng + toàn bộ khung xương)
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
- Hợp đồng: `RentalContract`
- Tính tiền tháng: `MonthlyBillingPeriod`, `CalculatedMonthlyFee`, `FeeItemDetail`
- Thanh toán: `PaymentTransaction`, `BankAccount`

### 2. Nguyễn Trần Bảo Chi (15 bảng)
- Khu trọ - phòng: `Property`, `PropertyFloor`, `Room`, `RoomMedia`, `RoomFurnishing`, `RoomEquipmentRequest`, `RoomAddonFee`
- Khách thuê - cư dân: `Tenant`, `TenantIdCard`, `RoomResident`, `ResidentStayHistory`, `TenantVehicle`
- Yêu cầu thuê - mẫu hợp đồng: `BookingRequest`, `ContractTemplate`, `ContractAdjustment`

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

Phi 12 + Chi 15 + Toàn 15 + Việt 14 = 56 bảng, bằng số entity trong package `entity`.

### Cách làm đồng thời, không chờ nhau

Trạng thái giai đoạn 0: ĐÃ XONG (Phi). Các class dùng chung đã có trên `main`, hãy pull về dùng:
- `dto/ApiResponse`: mọi API trả `ApiResponse.ok(data)` hoặc `ApiResponse.error(msg)`
- `dto/PageResponse`: dùng cho API phân trang
- `exception/BusinessException`: ném từ service khi vi phạm nghiệp vụ, ví dụ `throw BusinessException.notFound("Phong")`
- `exception/GlobalExceptionHandler`: tự đổi exception thành response chuẩn
- `config/SecurityConfig`: tạm cho phép mọi request (permitAll), chưa cần đăng nhập
- `controller/PingController`: ví dụ controller mẫu, thử bằng `GET /api/ping`

- Giai đoạn 0 (Phi làm trước, càng sớm càng tốt, chỉ vài chục dòng): đẩy lên `main` các class dùng chung `ApiResponse`, `BusinessException`, `GlobalExceptionHandler`. Trong lúc chờ, `SecurityConfig` cho phép mọi request (permitAll) để mọi người test API mà chưa cần đăng nhập. Phi siết phân quyền lại sau khi mọi người đã có API.
- Trong lúc Phi làm khung, Chi, Toàn, Việt bắt đầu ngay với các bảng của mình, không cần đợi.
- Mỗi người chỉ tạo repository, service, controller trong phần bảng của mình. Cần dữ liệu của bảng người khác thì gọi qua `Service` của người đó, hoặc dùng quan hệ JPA có sẵn trong entity. Không tự viết repository cho bảng người khác.
- Các service nền tảng nhiều người cần (`RoomService`, `TenantService`, `UserService`) chủ sở hữu nên làm các hàm đọc cơ bản (`findById`, `findAll`) trước, rồi mới làm nghiệp vụ phức tạp.
- Logic trong job tự động: Toàn viết phần chốt chỉ số - tính hóa đơn, Việt viết phần nhắc nợ - phạt trễ hạn, Phi chỉ viết khung scheduler.

## Quy ước làm chung

- Mỗi thay đổi DB phải sửa `db/group-7.sql` VÀ entity tương ứng trong cùng 1 commit.
- Số class trong package `entity` phải bằng số bảng trong DB.
- Mỗi người làm trên branch riêng, ví dụ `phi/room`, `chi/tenant`, `toan/invoice`, `viet/debt`.
- Pull `main` trước khi bắt đầu làm và trước khi push.
- Làm xong thì tạo Pull Request vào `main`, không push thẳng lên `main`.
- Không sửa file entity của người khác; cần sửa thì nhắn người đó hoặc tạo PR.
