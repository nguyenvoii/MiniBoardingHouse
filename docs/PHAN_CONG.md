# Phân công

Phân công theo use case (nguồn chính thức là file Excel) và theo nhóm bảng. Tiến độ hiện tại xem `TIEN_DO_VA_CON_THIEU.md`.

**Giao diện: toàn bộ màn hình do Phi làm**, gồm cả màn hình của các chức năng thuộc phần Chi, Toàn, Việt. Chi, Toàn, Việt chỉ làm backend (API, nghiệp vụ, test) và không cần làm giao diện.

## 1. Theo use case

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

## 2. Theo nhóm bảng

Ngoài ra, Phi làm toàn bộ frontend, tức mọi màn hình của giao diện web Stayly trong `frontend/` (xem `docs/GIAO_DIEN_STAYLY.md`), kể cả màn hình của phần Chi, Toàn, Việt.

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

## 3. Phần của Phi (đã làm)

- Đã làm: UC1 đăng ký chủ trọ, UC2 xác thực email bằng OTP, UC3 đăng nhập/đăng xuất, UC4 quên mật khẩu, UC5 đổi mật khẩu, UC6 hồ sơ cá nhân, UC7 ảnh đại diện, UC8-9 quản lý nhân viên, UC10 cấp tài khoản người thuê, UC11 phân quyền theo vai trò, UC12 bảng quyền chi tiết, UC13 nhật ký thao tác, UC119 tài khoản ngân hàng và QR, UC120 khung job tự động, UC122 sao lưu và phục hồi, khung xuất Excel/PDF.
- Hợp đồng UC41-51 đã làm xong (`docs/API_PHI.md` mục 3).
- Tính tiền tháng UC65-75 (`docs/API_PHI.md` mục 4), thanh toán UC85-88 (mục 6), dashboard UC116 và báo cáo UC118 (mục 7) đã làm xong. UC76 và UC77 là phần của Toàn (đã có trong `InvoiceController`).
- Chưa gửi email/SMS thật: mã OTP hiện chỉ in ra log của ứng dụng (xem `docs/API_PHI.md` mục 2).
