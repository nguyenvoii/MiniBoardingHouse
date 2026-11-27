# SRS hiện trạng, Phần 8-9: Phân quyền và tình huống ngoại lệ

Đường dẫn Java viết tắt: `P/` = `src/main/java/com/group7/minihouse/`.

---

# Phần 8. Phân quyền

## 8.1 Cơ chế

1. [CODE] Công tắc `app.security.enabled` (mặc định `false`) quyết định toàn bộ (`P/shared/config/SecurityConfig.java:44`).
   - `false`: `auth.anyRequest().permitAll()`. Mọi API mở, không cần token. `MethodSecurityConfig` bị tắt (`@ConditionalOnProperty havingValue="true"`), nên `@PreAuthorize` KHÔNG có tác dụng.
   - `true`: luật URL ở mục 8.2 + `@PreAuthorize` ở mức hàm/lớp.
2. [CODE] Token JWT chỉ dùng để nhận diện người dùng. Vai trò được lấy lại từ DB mỗi request (`JwtAuthFilter` gọi `UserStatusChecker.activeRoles(userId)`); token hợp lệ nhưng tài khoản không `ACTIVE` hoặc bị xóa thì coi như chưa đăng nhập. Đổi vai trò, khóa tài khoản có hiệu lực ngay (`P/shared/security/JwtAuthFilter.java`, `P/phi/service/UserStatusCheckerImpl.java`).
3. [CODE] Bảng `permissions` (22 quyền, seed) và lớp `RolePermissions` KHÔNG dùng để chặn API; chỉ để giao diện tra cứu (`PermissionController`, javadoc `RolePermissions`).
4. [CODE] Không có bảng `role_permissions`: chủ dự án chốt phân quyền theo `role_code` (javadoc `RolePermissions`).

## 8.2 Luật URL khi bảo mật BẬT (`SecurityConfig.filterChain`)

| URL | Ai truy cập được |
|---|---|
| `POST /api/auth/login`, `/forgot-password`, `/reset-password`, `/register`, `/verify-email`, `/resend-verification`; `GET /api/ping`; `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`; `/ui/**`, `/`, `/index.html`, `/assets/**`, `/favicon.ico` | mọi người, không cần đăng nhập |
| `GET /api/auth/avatars/*`, `GET /api/bank-accounts/qr/*`, `GET /api/contracts/signatures/*` | mọi người, không cần đăng nhập (ảnh đại diện, ảnh QR, ảnh chữ ký hợp đồng) |
| `POST /api/payments/callback` | mọi người ở tầng URL; tự kiểm bí mật `X-Callback-Secret` trong controller |
| `/api/auth/**` (còn lại) | mọi người đã đăng nhập (kể cả TENANT) |
| `/api/me/**` | chỉ `TENANT` |
| mọi URL khác | `LANDLORD`, `MANAGER`, `CASHIER`, `METER_READER` (TENANT bị 403) |

[CODE] Swagger UI và OpenAPI công khai kể cả khi bảo mật bật. Khi chưa đăng nhập: 401 JSON "Chua dang nhap hoac token het han"; riêng `GET` có `Accept: text/html` thì chuyển hướng sang `/ui/index.html`. Thiếu quyền: 403 "Khong co quyen thuc hien".

## 8.3 Ma trận vai trò x chức năng

Ký hiệu: L=LANDLORD, M=MANAGER, C=CASHIER, R=METER_READER, T=TENANT. "Khi tắt" = `app.security.enabled=false` (mặc định): mọi người, kể cả người chưa đăng nhập, gọi được MỌI API ở cột này.

| Nhóm API | Quyền khi BẬT | Khi TẮT | Bằng chứng |
|---|---|---|---|
| Đăng nhập, quên mật khẩu, đăng ký chủ trọ | công khai | công khai | `SecurityConfig` |
| Hồ sơ, đổi mật khẩu, ảnh đại diện, nhật ký của mình, `GET /api/auth/permissions` | mọi người đã đăng nhập (L M C R T) | không cần token nhưng các API này cần biết user nên báo 401 | `AccountController`, `ActivityLogController:81` |
| Quản lý nhân viên: tạo, sửa, khóa | L | mở | `StaffController` (`@PreAuthorize hasRole('LANDLORD')`) |
| Quản lý nhân viên: danh sách, chi tiết | L M | mở | `StaffController:48,57` |
| Cấp/khóa tài khoản người thuê, xem trạng thái | L M | mở | `TenantAccountController` |
| Nhật ký toàn hệ thống (+ xuất) | L | mở | `ActivityLogController:40,55` |
| Sao lưu, phục hồi, chạy job | L | mở | `BackupController`, `JobController` |
| Tài khoản ngân hàng, QR: xem danh sách | L M C R | mở | `BankAccountController:46` (không có `@PreAuthorize`) |
| Tài khoản ngân hàng, QR: thêm, sửa, đặt mặc định, xóa, tải QR | L M | mở | `BankAccountController:52-84` |
| Hợp đồng (mọi API nhân viên) | L M | mở | `ContractController` hằng `STAFF` |
| Hợp đồng của mình, ký hợp đồng của mình | T | cần user | `ContractController:171-185` |
| Xe và người ở cùng: xem | L M C R | mở | `OccupancyController:37,64` (không có `@PreAuthorize`) |
| Xe và người ở cùng: thêm, sửa, xóa | L M | mở | `OccupancyController` hằng `STAFF` |
| Kỳ tính tiền, tính tiền, duyệt, sửa dòng phí | L M | mở | `BillingController` (class) |
| Thanh toán: ghi tiền mặt/chuyển khoản, tạo QR, xác nhận, tra cứu | L M C | mở | `CashierPaymentController` hằng `CASH_STAFF` |
| Người thuê lấy QR trả hóa đơn của mình | T | cần user | `CashierPaymentController:111` |
| Dashboard, báo cáo doanh thu | L M | mở | `ReportController` (class) |
| Kỳ ghi chỉ số, nhập/xác nhận/điều chỉnh chỉ số, cảnh báo | L M C R (không giới hạn thêm) | mở | `MeterReadingController` (không có `@PreAuthorize`) |
| Đơn giá, dịch vụ, phụ thu, lịch sử giá | L M C R | mở | `ServiceRateController` (không có `@PreAuthorize`) |
| Hóa đơn: phát hành, sửa tổng, gửi, xem, in, tổng hợp | L M C R | mở | `InvoiceController` (không có `@PreAuthorize`) |
| Phiếu thu, thanh toán một phần, tiền dư, cấn trừ, hoàn tiền dư, đảo giao dịch | L M C R | mở | `PaymentController` (toan, không có `@PreAuthorize`) |
| Công nợ: xem, xuất, tổng hợp | L M C | mở | `DebtController` hằng `READ` |
| Công nợ: tính lại, nhắc nợ, ghi xử lý | L M | mở | `DebtController` hằng `WRITE` |
| Công nợ của mình, hóa đơn của mình | T | cần user | `TenantSelfServiceController:50,56` |
| Trả phòng (nhân viên) | L M | mở | `CheckoutController` (class) |
| Yêu cầu trả phòng, sự cố, thiết bị của mình | T | cần user | `TenantSelfServiceController` |
| Sự cố, thiết bị, danh mục sự cố, hợp đồng sắp hết hạn, chi phí vận hành, mẫu thông báo | L M | mở | `MaintenanceController`, `EquipmentController`, `ExpenseController`, `NotificationTemplateController` (class) |
| Trung tâm thông báo | nhân viên (L M C R) qua `/api/notifications`; T qua `/api/me/notifications` | cần user | `NotificationController:35-65` |
| Hóa đơn chia sẻ `GET /api/invoices/share/{token}` | L M C R (không công khai dù mô tả là "không cần đăng nhập") | mở | `InvoiceController:83`, javadoc `InvoiceService.detailByShareToken` |

Hệ quả [CODE]: khi bảo mật BẬT, `CASHIER` và `METER_READER` gọi được các API đổi đơn giá, sửa tổng hóa đơn, đảo giao dịch, đổi chỉ số điện nước, vì `toan/controller` không giới hạn thêm vai trò. Giao diện (menu theo vai trò) không được kiểm trong tài liệu này [KHÔNG RÕ].

## 8.4 Người thuê chỉ truy cập dữ liệu của chính mình: cách code đảm bảo

1. [CODE] URL của người thuê tách riêng dưới `/api/me/**`, và luật URL cho `/api/me/**` là `hasRole("TENANT")`; mọi URL khác chỉ cho 4 vai trò nhân viên. Nghĩa là người thuê KHÔNG gọi được API nhân viên (`/api/invoices/{id}`, `/api/contracts/{id}`...).
2. [CODE] Trong API `/api/me/**`, mã khách lấy từ TOKEN qua `CurrentUser.requireTenantId(user)` (không nhận `tenantId` từ client), ví dụ `TenantSelfServiceController` và `ContractController:171-185`.
3. [CODE] `tenantId` trong token được xác định lúc đăng nhập bằng cách nối SĐT của user với `tenants.phone_number`, và chặn khi `users.created_at < tenants.created_at` (SĐT tái sử dụng) (`AuthService.findTenantId`).
4. [CODE] Kiểm sở hữu theo từng tài nguyên: hợp đồng (`detailOfTenant`, `signByTenant` trả 404 nếu không phải của mình), QR hóa đơn (`createQrForTenant` trả 404 nếu hóa đơn không của mình), yêu cầu trả phòng (`CheckoutService.resolveContract` trả 403 "Hop dong khong thuoc ve ban").
5. [SUY RA] Đánh giá, đọc yêu cầu sự cố và thiết bị theo `tenantId` dựa trên `maintenanceService.rate(id, tenantId, req)`, `maintenanceService.mine(tenantId)`, `equipmentService.mine(tenantId)` (đã thấy chữ ký hàm nhận `tenantId`, chưa đọc từng thân hàm xem có kiểm sở hữu bản ghi `id` hay không).
6. [CODE] Khi bảo mật TẮT: không có token nên `user = null`, các API `/api/me/**` trả 401 (`CurrentUser.requireTenantId`), nhưng mọi API nhân viên mở nên dữ liệu của mọi người truy cập được.
7. [CODE] Rò rỉ có chủ đích ở tầng URL: ảnh chữ ký hợp đồng, ảnh QR, ảnh đại diện là công khai theo tên file (UUID ngẫu nhiên, không có kiểm quyền): `SecurityConfig` permitAll cho `GET /api/contracts/signatures/*`.

---

# Phần 9. Tình huống ngoại lệ

Quy ước: CÓ = hệ thống xử lý/cho phép đúng nghĩa câu hỏi; KHÔNG = không xử lý hoặc không có; MỘT PHẦN = xử lý một phần.

| # | Tình huống | CÓ/KHÔNG/MỘT PHẦN | Hành vi thực tế | Bằng chứng |
|---|---|---|---|---|
| 1 | Sửa chỉ số điện nước sau khi kỳ đã duyệt/đóng | MỘT PHẦN | Duyệt kỳ tính tiền đóng luôn kỳ ghi chỉ số cùng tháng (`is_closed=true`). Kỳ đã đóng: nhập mới 409, điều chỉnh 409. Nhưng `PATCH /meter-periods/{id}/closed?value=false` mở lại không điều kiện, rồi điều chỉnh được. Kỳ tính tiền đã `APPROVED` thì không tính lại được (409) nên bảng tính và hóa đơn đã tạo KHÔNG đổi theo. `confirm` chạy được cả khi kỳ đã đóng | `BillingService.approve`, `MeterReadingService.recordReading:115`, `adjustReading:199`, `closePeriod`, `confirmReading` |
| 2a | Sửa hóa đơn sau khi phát hành | CÓ (sửa tổng) | `PUT /api/invoices/{id}/total` (bắt buộc lý do) đặt thẳng `total_amount`, tính lại trạng thái, gửi thông báo; không chặn hóa đơn `PAID`/đã thu một phần; không sửa các dòng chi tiết `fee_item_details` (tổng hóa đơn và các dòng lệch nhau). Ngoài ra `MaintenanceService.adjustInvoice` giảm tiền sau sự cố (chặn `PAID`/`CANCELLED`) | `InvoiceService.adjustTotal`, `MaintenanceService.adjustInvoice` |
| 2b | Hủy hóa đơn sau khi phát hành | KHÔNG | Không có endpoint hủy; trạng thái `CANCELLED` có trong DB nhưng không code nào đặt (UC126 chưa làm). Hóa đơn phát hành nhầm chỉ sửa tổng hoặc để đó | `InvoiceController` (không có `cancel`) |
| 2c | Hóa đơn đã thu một phần | MỘT PHẦN | Tiếp tục thu được; sửa tổng được; tổng mới nhỏ hơn đã thu thì trạng thái thành `PAID` và `paid > total` (tiền dư), xử lý bằng `overpayment/to-balance`. Đảo giao dịch trừ lại tiền | `InvoiceBalanceCalculator`, `PrepaymentService` |
| 3 | Đổi đơn giá trong Cài đặt: kỳ và hóa đơn cũ có đổi không | CÓ lưu cố định | Đơn giá được chép vào từng dòng `fee_item_details` (`unit_price`) khi tính kỳ, hóa đơn chép `total_amount` lúc phát hành. Kỳ `APPROVED` và hóa đơn đã phát hành không đổi. Kỳ còn `DRAFT` thì đổi theo khi bấm tính lại (`calculate`). `PUT /api/service-rate-plans/{id}` sửa bảng giá tại chỗ, không để lại lịch sử (chỉ `updateServiceRate` ghi `rate_change_histories`) | `BillingService.calculate`, `InvoiceService.issueOne`, `ServiceRateService:72,101-123` |
| 4a | Chỉ số mới nhỏ hơn chỉ số cũ | KHÔNG cho phép | 400 khi nhập và khi điều chỉnh. Không có nhánh "thay công tơ/quay vòng" | `recordReading`, `adjustReading` |
| 4b | Kỳ đầu tiên chưa có chỉ số cũ | CÓ | Dùng `rooms.initial_electric_meter` / `initial_water_meter` (mặc định 0); client cũng có thể gửi `oldElectric/oldWater` | `resolveOldValues` |
| 4c | Phòng trống | MỘT PHẦN | Vẫn nhập được chỉ số cho phòng không có hợp đồng (không kiểm tra). Khi tính tiền, phòng không có hợp đồng chạm tháng thì không có bảng tính (không tính tiền) | `recordReading`, `BillingService.eligibleContracts` |
| 5 | Khách vào/ra giữa tháng: lẻ ngày hay tròn tháng | MỘT PHẦN | Tiền phòng tính theo ngày thực ở (`giá × ngày / số ngày trong tháng`, làm tròn đồng). Điện nước theo chỉ số của kỳ (không theo ngày). Dịch vụ cố định tính ĐỦ tháng (không tỷ lệ ngày). Điện nước chung chia theo số người, không theo ngày | `BillingService.prorateRent`, `calculate`, `addService` |
| 6a | Trả phòng/chấm dứt: chốt điện nước cuối | MỘT PHẦN | Quy trình trả phòng (UC101-107) ghi `final_electric_meter/final_water_meter` vào biên bản nhưng không tạo `meter_readings` và không đưa vào hóa đơn. Chuyển phòng (UC50) có tùy chọn chốt chỉ số (tạo chỉ số vào kỳ chọn). `terminate` (UC51) không chốt chỉ số | `CheckoutService.recordInspection`, `ContractService.transferRoom`, `terminate` |
| 6b | Xử lý tiền cọc | CÓ (đường trả phòng) / KHÔNG (đường chấm dứt) | Qua quy trình trả phòng: quyết toán, phiếu hoàn cọc, gạch nợ hóa đơn bằng cọc. Qua `terminate` (UC51): không động đến cọc | `CheckoutService.settlement`, `payRefund`; `ContractService.terminate` |
| 6c | Công nợ còn lại khi trả phòng | MỘT PHẦN | Nợ hóa đơn được trừ vào cọc (nợ trước, hư hỏng sau). Phần vượt quá cọc chỉ hiện ở `mustPay` (phải trả thêm), KHÔNG tạo hóa đơn hay khoản thu mới. Hóa đơn chưa trả vẫn nằm trong công nợ sau khi phòng `VACANT` | `settlement`, `DebtService.recalculate` |
| 6d | Trạng thái phòng khi trả phòng | CÓ | `VACANT` khi `finalize` hoặc `terminate` | `finalizeCheckout`, `terminate` |
| 6e | Tài khoản khách khi trả phòng | KHÔNG | Không tự khóa/vô hiệu hóa. Chỉ khóa tay bằng `PATCH /api/tenants/{id}/account/status` | `finalizeCheckout`, `TenantAccountService.setAccountStatus` |
| 6f | Tiền phòng/điện nước tháng cuối | MỘT PHẦN | Hợp đồng `TERMINATED` vẫn vào kỳ tính tiền của tháng mà nó còn hiệu lực đến ngày chấm dứt (`effectiveEnd`). Với đường `finalize` (trả phòng) hợp đồng cũng thành `TERMINATED` nhưng ngày chấm dứt lấy từ bản ghi điều chỉnh `TERMINATED`, mà `finalize` không tạo bản ghi này nên ngày hiệu lực lấy bằng `end_date` hợp đồng | `BillingService.effectiveEnd`, `CheckoutService.finalizeCheckout` |
| 7a | Thu tiền mặt: trả thiếu | CÓ | Nhận số tiền nhỏ hơn số còn phải trả; hóa đơn thành `PARTIALLY_PAID` (hoặc `OVERDUE` nếu quá hạn) | `PaymentService.record`, `InvoiceBalanceCalculator` |
| 7b | Thu tiền mặt: trả dư | KHÔNG | 400 khi `amount > remaining` ("Phan thua xu ly o chuc nang thanh toan du"). Không có đường nhận dư rồi ghi nhận tiền thừa | `PaymentService.checkAmount`, `PartialPaymentService.record` |
| 7c | Thu gộp nhiều hóa đơn một lần | KHÔNG | Mỗi giao dịch gắn đúng một `invoiceId`. Không có API thu gộp | `PaymentRequest` (một `invoiceId`) |
| 7d | Hủy giao dịch | CÓ | `POST /api/payment-transactions/{id}/reverse` (chỉ `SUCCESS`): đặt `REVERSED`, trừ khỏi `paid_amount`. Không có lý do bắt buộc, không ghi `user_activity_logs`. Giao dịch chờ (`FAILED`) không hủy được | `PrepaymentService.reverseTransaction` |
| 8a | QR: ngân hàng báo trùng | CÓ | Callback lần hai cho giao dịch đã `SUCCESS` trả 409; mã giao dịch có kiểm trùng khi tạo | `PaymentService.confirmWith`, `newTransaction` |
| 8b | QR: sai số tiền | CÓ | Số tiền khác số tiền giao dịch: KHÔNG cộng, trả `ok=false` kèm lý do (HTTP 200), giao dịch giữ `FAILED` để nhân viên xử lý | `PaymentService.confirmWith` |
| 8c | QR: sai nội dung | MỘT PHẦN | Callback khớp theo `transactionCode`, KHÔNG so nội dung chuyển khoản. Nhân viên có `POST /api/payments/match` tìm hóa đơn theo mã nằm trong nội dung (để đối chiếu tay) | `callback`, `match`, `mentions` |
| 8d | QR: hóa đơn đã trả bằng tiền mặt | MỘT PHẦN | Nếu còn nợ nhỏ hơn số tiền giao dịch: báo không cộng; nếu vẫn còn đủ nợ thì cộng (không phát hiện trùng thanh toán). Hóa đơn `PAID`: tạo QR mới bị 409 | `confirmWith`, `payableInvoice` |
| 8e | QR: giao dịch không khớp hóa đơn nào | MỘT PHẦN | Callback mã lạ trả 404, không lưu lại để đối soát | `callback` |
| 8f | QR: xác thực nguồn báo | MỘT PHẦN | Header `X-Callback-Secret` so sánh hằng thời gian với `app.payment.callback-secret`; bí mật rỗng thì từ chối 403; không có chữ ký trên nội dung, không giới hạn IP | `CashierPaymentController.callback` |
| 8g | QR: hết hạn giao dịch chờ | KHÔNG | Giao dịch chờ không có hạn hủy | `PaymentService.createQr` |
| 9a | Công nợ chuyển sang kỳ sau | CÓ | `tenant_debts`: nợ kỳ trước = tổng nợ − nợ hóa đơn mới nhất; hóa đơn tháng sau là hóa đơn độc lập, nợ cũ không cộng vào hóa đơn mới (nợ cũ nằm ở hóa đơn cũ) | `DebtService.recalculate` |
| 9b | Phí trễ hạn | CÓ | `Phạt trễ hạn` là một dòng của kỳ tính tiền kế tiếp (tổng ngày quá hạn × phí/ngày); `debt_penalty_logs` ghi riêng một công thức khác (Phần 13) | `BillingService.calculate`, `DebtService.logPenalty` |
| 9c | Nhắc nợ | CÓ (trong ứng dụng) | 3 mức, vào `user_notifications`; không có kênh email/SMS thật | `DebtService.sendReminders`, `NotificationSender` (viet) |
| 10a | Nhiều người ở một phòng | CÓ | `room_residents`; tối đa `max_occupants` tính cả người đứng tên; số người ảnh hưởng dịch vụ `PER_PERSON` và chia điện nước chung. Người ở cùng không có tài khoản | `OccupancyService.addResident`, `BillingService.othersInRoom` |
| 10b | Một khách nhiều hợp đồng | CÓ | Không chặn. Tuy nhiên khi tính tiền công nợ/QR hay trả phòng người thuê cần chọn `contractId` (`CheckoutService.resolveContract` báo "can chon contractId") | `ContractService.create`, `CheckoutService.resolveContract` |
| 10c | Hai hợp đồng chồng thời gian trên cùng phòng | MỘT PHẦN | `create` chặn nếu phòng không `VACANT` hoặc đã có hợp đồng `ACTIVE/EXPIRING_SOON`. Không có ràng buộc DB; chuyển phòng/gia hạn cũng kiểm. Hợp đồng đã `EXPIRED`/`TERMINATED` có thể chồng ngày với hợp đồng mới; khi đó kỳ tính tiền chỉ lấy một hợp đồng và ghi cảnh báo. Hợp đồng tương lai cũng đặt phòng `RENTED` ngay | `ContractService.create`, `BillingService.calculate` |
| 11 | Khách không có tài khoản nhận hóa đơn thế nào | KHÔNG (tự động) | Email: không có lớp gửi thật (`InvoiceMailGateway` không có bản cài đặt), bản ghi thông báo ở trạng thái `PENDING`. Thông báo trong ứng dụng cần tài khoản. Link chia sẻ yêu cầu đăng nhập nhân viên khi bảo mật bật. Nhân viên có `GET /api/invoices/{id}/print` (HTML) để in/đưa tay | `InvoiceService.sendEmail`, `dispatchEmail`, `printableHtml` |
| 12a | Mật khẩu tạm: bắt buộc đổi lần đầu | KHÔNG | Không có cờ "phải đổi mật khẩu" (grep `mustChange` không có). Mật khẩu tạm trả về trong response tạo tài khoản, chỉ hiện một lần | `StaffService`, `TenantAccountService` |
| 12b | Quên mật khẩu | CÓ (qua OTP) | `forgot-password` + `reset-password`; nhưng mã OTP chỉ in ra log server (không gửi email/SMS thật); giao diện chỉ có bước xin mã, không có màn nhập mã | `PasswordRecoveryService`, `LogNotificationSender`, `frontend/src/pages/login.ts:46-54` |
| 12c | Tài khoản tự khóa khi hết hợp đồng | KHÔNG | Không có code nối trạng thái hợp đồng với tài khoản | `ContractExpiryService`, `TenantAccountService` |
| 12d | Đăng nhập sai nhiều lần | KHÔNG | Không đếm, không khóa tạm | `AuthService.login` |
| 13 | Hai nhân viên sửa cùng một bản ghi cùng lúc | KHÔNG | Không có `@Version` (grep không có), không khóa bi quan; lần lưu sau ghi đè lần trước. Các ràng buộc unique trả 409 (`DataIntegrityViolation`). [SUY RA] Hai callback đồng thời cho cùng giao dịch có thể cùng thấy `FAILED` và cùng cộng tiền (không có khóa) | `GlobalExceptionHandler.handleDataIntegrity`, `PaymentService.confirmWith` |
| 14 | Tiền cọc có tính vào doanh thu | KHÔNG | Doanh thu chỉ tính `payment_transactions` `SUCCESS`. Tiền cọc thu ban đầu không đi qua bảng này, hoàn cọc cũng không | `ReportService.dashboard`, `revenueReport` |
| 15a | Xóa phòng, khách | KHÔNG CÓ API (phần của Chi chưa làm) | Không có endpoint xóa phòng/khách; FK trong DB sẽ chặn nếu xóa bằng SQL (đa số FK không `ON DELETE CASCADE`) | `P/chi/*` chỉ có `package-info.java` |
| 15b | Xóa hợp đồng | KHÔNG CÓ API | Chỉ chấm dứt (`TERMINATED`), không xóa | `ContractController` |
| 15c | Xóa các dữ liệu khác | MỘT PHẦN | Xóa cứng: chi phí vận hành, phí dịch vụ, phụ thu theo phòng, thông báo của người dùng, dòng chi tiết hóa đơn (kỳ DRAFT), lần cấn trừ tiền dư. Xóa mềm (`is_active=false`): xe, người ở cùng, tài khoản ngân hàng, danh mục sự cố/mẫu thông báo (đổi `active`). Không có kiểm tra "đang có hóa đơn/công nợ" vì các bảng này không liên kết tới hóa đơn | `OperatingExpenseService:88`, `ServiceRateService:133,168`, `OccupancyService`, `BankAccountService` |
| 16 | Tắt/bật `app.security.enabled` giữa chừng | CÓ (hành vi theo cấu hình) | Cấu hình đọc lúc khởi động; đổi cần khởi động lại. Token cũ vẫn hợp lệ nếu cùng `app.jwt.secret`. | `SecurityConfig` |
| 17 | SĐT của khách cũ dùng lại cho khách mới | CÓ (có xử lý) | Đăng nhập: tài khoản tạo trước hồ sơ khách bị coi là không liên kết (409). Cấp tài khoản mới: tài khoản cũ bị vô hiệu hóa (`INACTIVE`) và giải phóng SĐT/email | `AuthService.findTenantId`, `TenantAccountService.resolveExistingAccount` |
| 18 | Đăng ký chủ trọ trên email đang chờ xác thực; OTP hết hạn, dùng lại, đoán sai, xin dồn dập | CÓ | Đăng ký lại ghi đè tài khoản chờ; OTP: một lần dùng, hết hạn 10 phút, sai một lần hủy mọi mã còn hạn, xin lại trong 60 giây báo 429 (riêng quên mật khẩu im lặng không báo) | `RegistrationService`, `OtpService`, `PasswordRecoveryService.forgotPassword` |
| 19 | Callback thiếu hoặc sai `X-Callback-Secret` | CÓ | 403; nếu chưa cấu hình bí mật cũng 403 | `CashierPaymentController.callback` |
| 20 | Khóa/giáng chức tài khoản đang có token còn hạn | CÓ | Có hiệu lực ngay (vai trò/trạng thái đọc từ DB mỗi request) | `JwtAuthFilter`, `UserStatusCheckerImpl` |
| 21 | Upload sai loại hoặc quá lớn; xem chữ ký người khác | MỘT PHẦN | Sai loại ảnh 400, quá lớn 413. Ảnh chữ ký/QR/avatar xem được công khai nếu biết tên file UUID | `FileStorage.saveImage`, `SecurityConfig` |
| 22 | Job định kỳ chạy hai lần / chưa có dữ liệu | MỘT PHẦN | Nhắc hạn và nhắc nợ có kiểm trùng trong ngày/đợt; `refreshStatuses` chỉ đổi trạng thái một lần. Framework `JobScheduler` bỏ qua lần gọi nếu job đang chạy. `BillingJobs` BẬT mặc định, `VietJobs` TẮT mặc định, framework chung TẮT mặc định | `InvoiceService.sendDueReminders`, `JobScheduler`, `BillingJobs`, `VietJobs` |
| 23 | Duyệt kỳ khi còn phòng thiếu chỉ số | CÓ (chỉ cảnh báo) | `approve` chỉ yêu cầu kỳ có bảng tính; thiếu chỉ số chỉ ghi cảnh báo khi tính và `validate`, tiền điện nước = 0 | `BillingService.approve`, `addMeter` |
| 24 | Chia điện nước chung khi tổng số người = 0 | CÓ (không chia) | Bỏ qua khoản phân bổ (điều kiện `totalResidents > 0`) | `BillingService.calculate` |
| 25 | Mốc "hợp đồng sắp hết hạn" nhất quán | CÓ | 30 ngày dùng ở `ContractExpiryService.DEFAULT_DAYS`, dashboard `plusDays(30)`, controller mặc định `days=30` | `ContractExpiryService`, `ReportService:90`, `EquipmentController:112` |
| 26 | Phục hồi dữ liệu (restore) khi đang có người dùng | [KHÔNG RÕ] | Chưa đọc thân `BackupService.restore`; không có bằng chứng đã chạy thử trên DB thật trong phiên này | `BackupService` |
