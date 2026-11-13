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

## Phân công theo nhóm bảng

Mỗi người phụ trách một nhóm bảng (phân hệ DB): viết entity, repository, service, controller, dto cho các bảng đó.
Đặt tên file theo tên entity để không đụng nhau, ví dụ `RoomRepository`, `RoomService`, `RoomController`.
Nhóm bảng được gán theo người làm nhiều usecase nhất của phân hệ đó trong file Excel usecase.

| Thành viên | Mã SV | Số bảng | Số usecase | Package chung phụ trách |
|---|---|---|---|---|
| Nguyễn Anh Phi | TH08326 | 13 | 41 | `config` (bảo mật, đăng nhập, phân quyền) |
| Nguyễn Trần Bảo Chi | TH08666 | 15 | 32 | `util` (upload ảnh, gửi email, OTP SMS) |
| Phạm Khánh Toàn | TH08433 | 16 | 25 | job/scheduler (tự động chốt chỉ số, tính tiền, nhắc nợ) |
| Vũ Văn Quốc Việt | TH08548 | 12 | 25 | `exception`, xuất Excel/PDF, dashboard - báo cáo |

### 1. Nguyễn Anh Phi (13 bảng)
- Người dùng - phân quyền: `User`, `Role`, `Permission`, `UserRole` (+ `UserRoleId`)
- Khu trọ - phòng: `Property`, `PropertyFloor`, `Room`
- Hợp đồng: `RentalContract`
- Tính tiền tháng: `MonthlyBillingPeriod`, `CalculatedMonthlyFee`, `FeeItemDetail`
- Thanh toán: `PaymentTransaction`
- Bổ sung: `BankAccount`

### 2. Nguyễn Trần Bảo Chi (15 bảng)
- Người dùng: `UserVerification`, `UserActivityLog`
- Phòng: `RoomMedia`, `RoomFurnishing`
- Khách thuê - cư dân: `Tenant`, `TenantIdCard`, `RoomResident`, `ResidentStayHistory`
- Yêu cầu thuê - hợp đồng: `BookingRequest`, `ContractTemplate`, `ContractAdjustment`, `ContractNotification`
- Bổ sung: `TenantVehicle`, `NotificationTemplate`, `UserNotification`
- Usecase 1-2 (OTP email, SMS) cần API bên thứ ba, do Chi làm.

### 3. Phạm Khánh Toàn (16 bảng)
- Chỉ số điện nước: `UtilityReadingPeriod`, `MeterReading`, `MeterPhoto`, `MeterAdjustmentLog`, `MeterAnomalyAlert`
- Đơn giá - phí dịch vụ: `ServiceRatePlan`, `PropertyServiceRate`, `RoomCustomRate`, `RateChangeHistory`
- Hóa đơn: `Invoice`, `InvoiceNotification`
- Thu tiền - cấn trừ: `ReceiptVoucher`, `PartialPaymentLog`, `TenantPrepayment`, `PrepaymentDeduction`
- Bổ sung: `OperatingExpense`

### 4. Vũ Văn Quốc Việt (12 bảng)
- Công nợ: `TenantDebt`, `DebtPenaltyLog`, `DebtReminder`, `DebtProcessingLog`
- Trả phòng - hoàn cọc - sự cố sửa chữa: `CheckoutRequest`, `CheckoutInspection`, `DepositRefundVoucher`, `MaintenanceRequest`, `MaintenanceLog`
- Bổ sung: `RoomEquipmentRequest`, `RoomAddonFee`, `IncidentCategory`

### Tổng kết

Phi 13 + Chi 15 + Toàn 16 + Việt 12 = 56 bảng, bằng số entity trong package `entity`.

8 bảng bổ sung (phân hệ 11 trong `group-7.sql`): `TenantVehicle` (Chi), `IncidentCategory` (Việt), `RoomEquipmentRequest` (Việt), `RoomAddonFee` (Việt), `OperatingExpense` (Toàn), `BankAccount` (Phi), `NotificationTemplate` (Chi), `UserNotification` (Chi).

Lưu ý: usecase 122 (sao lưu phục hồi dữ liệu) không cần bảng, Phi làm bằng chức năng của hệ quản trị/CSDL.

## Quy ước làm chung

- Mỗi thay đổi DB phải sửa `db/group-7.sql` VÀ entity tương ứng trong cùng 1 commit.
- Số class trong package `entity` phải bằng số bảng trong DB.
- Mỗi người làm trên branch riêng, ví dụ `phi/room`, `chi/tenant`, `toan/invoice`, `viet/debt`.
- Pull `main` trước khi bắt đầu làm và trước khi push.
- Làm xong thì tạo Pull Request vào `main`, không push thẳng lên `main`.
- Không sửa file entity của người khác; cần sửa thì nhắn người đó hoặc tạo PR.
