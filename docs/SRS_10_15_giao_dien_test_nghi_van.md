# SRS hiện trạng, Phần 10-15: Giao diện, phi chức năng, test, điểm đáng ngờ, câu hỏi mở, ma trận truy vết

Đường dẫn Java viết tắt: `P/` = `src/main/java/com/group7/minihouse/`. Giao diện: `frontend/src/`.

---

# Phần 10. Giao diện và tích hợp ngoài

## 10.1 Màn hình theo tác nhân

Giao diện "Stayly": Vite + TypeScript thuần (không React/Vue), router theo hash `#/...` (`frontend/src/main.ts:46-67`). Có hai khu vực: `staff` và `tenant`. Người dùng sai khu vực bị chuyển hướng (`main.ts:111-112`). Token lưu ở `sessionStorage` (`frontend/src/lib/api.ts:52-88`).

### Khu vực nhân viên (menu hiển thị theo vai trò, `frontend/src/ui/shell.ts:14-45`)

| Màn | Đường dẫn | Vai trò thấy trong menu | API chính |
|---|---|---|---|
| Tổng quan | `#/` | L M C | `/api/dashboard` |
| Chỉ số điện nước | `#/meters` | L M R | `/api/meter-periods`, `/api/meter-readings`, `/api/meter-alerts` |
| Tính tiền tháng, chi tiết kỳ | `#/billing`, `#/billing/:id` | L M | `/api/billing/**` |
| Hóa đơn | `#/invoices` | L M C | `/api/invoices/**` |
| Thanh toán | `#/payments` | L M C | `/api/payments/**`, `/api/payment-transactions/{id}/reverse` |
| Công nợ | `#/debts` | L M C | `/api/debts/**` |
| Hợp đồng | `#/contracts` | L M | `/api/contracts/**` |
| Phòng | `#/rooms` | L M | KHÔNG gọi API; dữ liệu mẫu + trạng thái từ hợp đồng thật |
| Khách thuê | `#/tenants` | L M | dữ liệu mẫu; cấp/khóa tài khoản gọi API thật |
| Sửa chữa | `#/maintenance` | L M | `/api/maintenance-requests/**` |
| Doanh thu và chi phí | `#/reports` | L M | `/api/reports/revenue` |
| Chi phí vận hành | `#/expenses` | L M | `/api/expenses/**` |
| Nhân viên | `#/staff` | [KHÔNG RÕ vai trò menu; nút thêm/sửa chỉ hiện cho L: `staff.ts:21,29`] | `/api/staff/**` |
| Nhật ký | `#/activity` | [KHÔNG RÕ] | `/api/activity-logs` |
| Cài đặt (đơn giá, dịch vụ, phụ thu, ngân hàng + QR) | `#/settings` | [KHÔNG RÕ] | `/api/service-rate-plans`, `/api/property-service-rates`, `/api/room-custom-rates`, `/api/properties/{id}/bank-accounts` |
| Hồ sơ cá nhân | `#/profile` | mọi nhân viên | `/api/auth/profile`, `/api/auth/password` |
| Đăng nhập, quên mật khẩu (chỉ xin mã) | `#/login` | công khai | `/api/auth/login`, `/api/auth/forgot-password` |

[CODE] Menu ẩn mục theo vai trò nhưng quyền thật ở backend khác: ví dụ `CASHIER` không thấy "Chỉ số điện nước" nhưng khi bảo mật bật vẫn gọi được `/api/meter-readings` (Phần 8).

### Khu vực người thuê (`#/me/**`, `frontend/src/pages/me.ts`)

| Màn | API |
|---|---|
| Trang chủ (hóa đơn cần trả, tạo QR trả tiền) | `/api/me/debts/invoices`, `/api/me/contracts`, `/api/me/payments/qr` |
| Hóa đơn | `/api/me/debts/invoices` |
| Hợp đồng (xem, ký) | `/api/me/contracts`, `/api/me/contracts/{id}`, `/api/me/contracts/{id}/sign` |
| Thông báo | `/api/me/notifications`, `/api/me/notifications/read-all` |
| Hỗ trợ (gửi sự cố, đánh giá) | `/api/me/maintenance-requests`, `/api/me/maintenance-requests/{id}/rating` |

## 10.2 Chức năng có backend nhưng không có giao diện

(kiểm bằng so sánh danh sách chuỗi `/api/...` trong `frontend/src` với danh sách endpoint, xem Phần 15 cho từng UC)

- Đăng ký chủ trọ, xác thực email, gửi lại mã, đặt lại mật khẩu bằng mã (`/api/auth/register`, `/verify-email`, `/resend-verification`, `/reset-password`).
- Ảnh đại diện (`/api/auth/avatar`): giao diện chỉ hiển thị.
- Phân quyền chi tiết (`/api/permissions`, `/api/roles/permissions`).
- Trả phòng toàn bộ (`/api/checkout-requests/**`, `/api/refund-vouchers/**`, `/api/me/checkout-requests`).
- Yêu cầu thiết bị, phí tiện ích (`/api/equipment-requests/**`, `/api/room-addon-fees`, `/api/me/equipment-requests`).
- Mẫu thông báo (`/api/notification-templates/**`).
- Sao lưu/phục hồi (`/api/admin/backups/**`), quản lý job (`/api/admin/jobs/**`).
- Phiếu thu, biên nhận (`/api/receipts/**`), thanh toán một phần, tiền dư, cấn trừ, hoàn tiền dư (`/api/prepayments/**`, `/api/partial-payments/**`, `/api/invoices/{id}/overpayment/to-balance`).
- Điều chỉnh tổng hóa đơn (`PUT /api/invoices/{id}/total`), gửi hóa đơn qua thông báo (`/send-app`), điều chỉnh hóa đơn sau sự cố (`/api/maintenance-requests/{id}/invoice-adjustment`).
- Lịch sử đơn giá (`/api/rate-histories`), lịch sử tiêu thụ và so sánh (`/api/rooms/{id}/consumption`, `/api/properties/{id}/consumption-comparison`), ảnh công tơ, chỉ số vào/ra.
- Hợp đồng sắp hết hạn, làm mới trạng thái (`/api/contracts/expiring`, `/refresh-status`).

## 10.3 Chức năng có giao diện nhưng không có backend

- [CODE] Màn Phòng và Khách thuê: danh sách lấy từ `frontend/src/data/sample.ts` (12 phòng, 8 khách) cộng trạng thái suy ra từ hợp đồng thật (`data/live.ts`). Nút "Thêm phòng", "Thêm khách thuê" bị `disabled` (`pages/rooms.ts:38`, `pages/tenants.ts:37`). Không có API phòng/khách/khu trọ.
- [CODE] `PROPERTY_ID = 1` cố định (`lib/api.ts:5`): giao diện luôn thao tác khu trọ có id 1.
- [CODE] Giao diện gọi `/api/incident-categories`, `/api/notifications/unread-count` (có backend). Không phát hiện chuỗi `/api/...` nào của giao diện trỏ vào endpoint không tồn tại, nhưng chưa kiểm tra từng tham số truy vấn [KHÔNG RÕ].

## 10.4 Tích hợp ngoài

| Tích hợp | Hiện trạng | Bằng chứng |
|---|---|---|
| Ngân hàng / cổng thanh toán | Không có SDK/API ngân hàng. Chỉ có endpoint callback nhận thông báo do hệ thống ngoài gọi vào, bảo vệ bằng `X-Callback-Secret`. Không có mã QR được sinh tự động: "QR" là ảnh do chủ trọ tải lên, nội dung chuyển khoản là mã hóa đơn | `CashierPaymentController`, `BankAccountService` |
| Email / SMS | Không có. `NotificationSender` (shared) chỉ in log; `InvoiceMailGateway` là interface không có cài đặt | `LogNotificationSender`, `InvoiceMailGateway`, `pom.xml` |
| PDF | Hợp đồng: PDF văn bản tạo bằng OpenPDF lúc tải. Hóa đơn, biên nhận: trang HTML để in (`/print`), không phải PDF. Báo cáo, nhật ký: xuất PDF bằng `TableExporter` (font cấu hình `app.export.pdf-font`) | `ContractService.pdf`, `InvoiceService.printableHtml`, `TableExporter` |
| Excel | Xuất: nhật ký, báo cáo doanh thu, công nợ (Apache POI). Không có nhập Excel | `ActivityLogController`, `ReportController`, `DebtService.exportExcel` |
| SQL Server | Sao lưu/phục hồi bằng `BACKUP/RESTORE DATABASE` qua JDBC, yêu cầu ứng dụng và SQL Server cùng máy | `BackupService` |
| Hệ thống file | Thư mục `uploads/` (avatar, QR, chữ ký), tên file UUID | `FileStorage` |

## 10.5 Biến môi trường, bí mật

- [CODE] Không dùng biến môi trường hệ điều hành trực tiếp; mọi cấu hình qua `application.properties` / `application-local.properties` (Spring cũng đọc được từ biến môi trường theo quy ước relaxed binding [SUY RA, chưa kiểm]).
- [CODE] Được commit trong repo: `app.jwt.secret` mặc định, tài khoản SQL `sa` với mật khẩu rỗng (`application.properties`), tài khoản admin trong `db/seed.sql`. Không được commit (bị `.gitignore`): `application-local.properties`, `uploads/`, `backups/`, `demo-ui/`.
- [CODE] `app.payment.callback-secret` không có giá trị mặc định (rỗng).
- [CODE] `BackupService` dùng lại `spring.datasource.username/password` để mở kết nối vào `master` khi phục hồi.

---

# Phần 11. Phi chức năng

## 11.1 Nhật ký thao tác

- [CODE] Bảng `user_activity_logs` (user, loại hành động, mô tả, IP, thời điểm) ghi bởi `ActivityLogService.log` (`P/phi/service/ActivityLogService.java`).
- [CODE] Chỉ code của package `phi` gọi `ActivityLogService` (grep: không có file nào trong `toan/` hoặc `viet/`). Các hành động được ghi: `LOGIN, LOGOUT, CHANGE_PASSWORD, UPDATE_PROFILE, UPDATE_AVATAR, REMOVE_AVATAR, RESET_PASSWORD, VERIFY_EMAIL, CREATE_STAFF, UPDATE_STAFF, LOCK_USER, UNLOCK_USER, CREATE_TENANT_ACCOUNT, CREATE_CONTRACT, UPDATE_CONTRACT_*, SEND_CONTRACT, SIGN_CONTRACT, EXTEND_CONTRACT, ADJUST_CONTRACT, TRANSFER_ROOM, TERMINATE_CONTRACT, CREATE_BILLING_PERIOD, CALCULATE_BILLING, APPROVE_BILLING, ADD/UPDATE/DELETE_FEE_ITEM, RECORD_PAYMENT, CREATE_QR_PAYMENT, CONFIRM_PAYMENT, *_BANK_ACCOUNT, ADD/UPDATE/REMOVE_VEHICLE, ADD/UPDATE/REMOVE_RESIDENT, BACKUP_DB`.
- [CODE] KHÔNG ghi `user_activity_logs` cho: phát hành/sửa hóa đơn, đảo giao dịch, cấn trừ tiền dư, thanh toán một phần, phiếu thu (toan), nhập/xác nhận chỉ số (toan; riêng điều chỉnh chỉ số có bảng `meter_adjustment_logs`), đổi đơn giá (riêng dịch vụ có `rate_change_histories`), trả phòng, công nợ, chi phí, sự cố (viet), callback thanh toán.
- [CODE] Phần mô tả trong javadoc `ActivityLogService` liệt kê 9 loại hành động, ít hơn danh sách thực tế ở trên.
- [CODE] Nhật ký ghi người thao tác lấy từ token; khi bảo mật tắt (không có token) các lời gọi `audit(actorId, ...)` bỏ qua việc ghi (`if (actorId != null)`).

## 11.2 Mật khẩu, token, phiên

- [CODE] Mật khẩu băm BCrypt (`new BCryptPasswordEncoder()`, `SecurityConfig.passwordEncoder`), lưu `users.password_hash`.
- [CODE] JWT HS256, hạn 480 phút, không có refresh token, không có danh sách thu hồi. Đăng xuất chỉ ghi log. Đổi mật khẩu không vô hiệu hóa token cũ. Khóa tài khoản thì vô hiệu ngay nhờ kiểm DB mỗi request.
- [CODE] Frontend lưu token ở `sessionStorage` (mất khi đóng tab).
- [CODE] Session phía server: `STATELESS`; CSRF tắt (`csrf(AbstractHttpConfigurer::disable)`).
- [CODE] OTP lưu dạng chữ thường ở `user_verifications.code` (không băm) (`OtpService`).
- [CODE] CORS chỉ cho `http://localhost:*` và `http://127.0.0.1:*`.

## 11.3 Sao lưu

- [CODE] `BackupService`: `BACKUP DATABASE` ra file `mbh-yyyyMMdd-HHmmss.bak` (tên theo `FILE_NAME`), liệt kê, phục hồi `WITH REPLACE` sau `SET SINGLE_USER WITH ROLLBACK IMMEDIATE`, `RESTORE VERIFYONLY` trước. Không có lịch sao lưu tự động (không có job sao lưu trong `ScheduledJob`/`@Scheduled`).
- [KHÔNG RÕ] Chưa thấy bằng chứng đã chạy thử phục hồi trên DB thật. Test `BackupServiceTest` có mặt; hàm khởi tạo thứ hai của `BackupService` nhận `MasterConnections` để tiêm kết nối giả [SUY RA: test không chạm SQL Server thật; chưa đọc test].

## 11.4 Múi giờ, định dạng ngày và tiền

- [CODE] Múi giờ: `Clock.systemDefaultZone()` (`P/shared/config/ClockConfig.java:13`), tức múi giờ máy chủ. Có 51 chỗ gọi `LocalDate.now()`/`LocalDateTime.now()` không qua `Clock` (grep), nên cũng theo múi giờ máy chủ.
- [CODE] DB: `DATETIME2`/`DATE` không có múi giờ; mặc định `GETDATE()` (giờ máy chủ SQL).
- [CODE] Tiền: `DECIMAL(18,2)` (VND), tính bằng `BigDecimal`. Hiển thị ở giao diện: `Intl.NumberFormat('vi-VN')`, không số lẻ, ký hiệu `₫` (`frontend/src/lib/format.ts`). Ngày ở API: ISO (`yyyy-MM-dd`); giao diện hợp đồng PDF dùng `dd/MM/yyyy` (`ContractService.DATE`).
- [CODE] Nội dung thông báo lỗi và phần lớn thông báo backend viết tiếng Việt KHÔNG DẤU (ví dụ "Sai tai khoan hoac mat khau"); tên dòng hóa đơn và mẫu thông báo của Việt có dấu.

## 11.5 Khác

- [CODE] Nén phản hồi (`server.compression.enabled=true`, JS/CSS/JSON/HTML/SVG, tối thiểu 1.024 byte).
- [CODE] Phân trang tối đa 100 dòng/trang ở các danh sách đã đọc.
- [CODE] Lỗi chưa lường trước trả 500 "Loi he thong" và ghi log (`GlobalExceptionHandler.handleOther`).
- [KHÔNG RÕ] Hiệu năng, đồng thời, tải: không có test/benchmark.

---

# Phần 12. Test hiện có

## 12.1 Kết quả chạy

- [CODE] Lệnh đã chạy trong phiên này: `mvn -q test -Dfile.encoding=UTF-8` (JDK 17, Maven của NetBeans 12.6), thư mục gốc dự án.
- [CODE] Kết quả: tổng `tests=256, errors=0, failures=0, skipped=0`, cộng từ 30 file `target/surefire-reports/TEST-*.xml` (chạy lúc 12:48 ngày 2026-11-17). Bản ghi log của tiến trình chạy chứa nhiều dòng `WARN`/stack trace do các test cố ý kích hoạt lỗi (ví dụ job bị bỏ qua khi đang chạy), không làm test thất bại.
- Không chạy: ứng dụng thật với SQL Server; test giao diện (không có).

## 12.2 Phạm vi test

| Package | File test | Nội dung |
|---|---|---|
| `shared` | 9 | `SecurityRulesTest` (luật URL), `GlobalExceptionHandlerTest`, `TableExporterTest`, `JobSchedulerTest`, `AuthUserTest`, `CurrentUserTest`, `JwtServiceTest`, `FileStorageTest`, `PasswordGeneratorTest` |
| `phi` controller | 3 | `ActivityLogControllerSecurityTest`, `StaffControllerSecurityTest`, `TenantAccountControllerSecurityTest` (MockMvc, kiểm 401/403) |
| `phi` service | 18 | `AccountServiceTest`, `ActivityLogServiceTest`, `AuthServiceTest`, `BackupServiceTest`, `BankAccountServiceTest`, `BillingServiceTest`, `ContractServiceTest`, `OccupancyServiceTest`, `OtpServiceTest`, `PasswordRecoveryServiceTest`, `PaymentServiceTest`, `PurgeExpiredVerificationsJobTest`, `RegistrationServiceTest`, `ReportServiceTest`, `RolePermissionsTest`, `StaffServiceTest`, `TenantAccountServiceTest`, `UserStatusCheckerImplTest` |
| `toan` | 0 | KHÔNG có test |
| `viet` | 0 | KHÔNG có test |
| `chi` | 0 | không có code |
| Frontend | 0 | không có test (không có `vitest`/`playwright` trong `package.json`) |

## 12.3 Phần chưa có test

- [CODE] Toàn bộ package `toan` (chỉ số điện nước, đơn giá, hóa đơn, thanh toán một phần, tiền dư, phiếu thu, `BillingJobs`) và `viet` (công nợ, trả phòng, sự cố, thiết bị, chi phí, thông báo, `VietJobs`).
- [CODE] Không có test tích hợp với DB thật (không thấy H2/Testcontainers trong `pom.xml`); test dùng giả lập (mock) nên không phát hiện lỗi truy vấn JPQL, lỗi khởi tạo bean trùng tên, hoặc lệch entity - DB. Điều này khớp với ghi chú của chủ dự án về sự cố trùng bean (lời chủ dự án, không có trong code).
- [KHÔNG RÕ] Tỷ lệ bao phủ dòng (không có JaCoCo trong `pom.xml`).

---

# Phần 13. Điểm đáng ngờ

Mỗi mục là một sự thật quan sát được, không phải khuyến nghị sửa.

1. [CODE] Hai cách chấm dứt hợp đồng với tác dụng phụ khác nhau: `ContractService.terminate` (UC51) ngừng người ở cùng, ghi `resident_stay_history`, không xử lý cọc; `CheckoutService.finalizeCheckout` (UC107) xử lý cọc nhưng không ngừng người ở cùng, không ghi `resident_stay_history`, và không tạo bản ghi điều chỉnh `TERMINATED` (nên `BillingService.effectiveEnd` lấy `end_date` hợp đồng thay vì ngày thực trả phòng).
2. [CODE] Hai công thức phạt trễ hạn: `BillingService` (tổng `overdue_days` của mọi dòng nợ chưa xong của cặp phòng-khách × phí/ngày, thành dòng "Phạt trễ hạn" trên bảng tính) và `DebtService.logPenalty` (số ngày quá hạn lớn nhất × phí/ngày, chỉ ghi `debt_penalty_logs`, không vào hóa đơn).
3. [CODE] Hai (thực tế ba) đường cộng tiền vào hóa đơn không đi chung `payment_transactions`: thanh toán một phần (`partial_payment_logs`), cấn trừ tiền dư (`prepayment_deductions`), gạch nợ bằng cọc (`CheckoutService.applyDepositToInvoices`). Doanh thu (`ReportService`) chỉ tính `payment_transactions` `SUCCESS`, nên các khoản này không vào doanh thu dù hóa đơn đã `PAID`.
4. [CODE] `InvoiceService.adjustTotal` đổi `total_amount` mà không đổi `fee_item_details` hay `calculated_monthly_fees`, nên tổng hóa đơn và tổng các dòng chi tiết có thể lệch nhau. Chi tiết in ra (`printableHtml`) lấy dòng và tổng từ hai nguồn khác nhau.
5. [CODE] `RoomAddonFee` (phụ phí thiết bị lắp thêm, ghi bởi `EquipmentService.install`) không được `BillingService` đọc (grep `RoomAddonFee` chỉ thấy trong `viet/service/EquipmentService` và entity): phí này không vào hóa đơn tháng tự động.
6. [CODE] `recordedByUserId`, `adjustedByUserId`, `changedByUserId` do client gửi trong body (`toan/dto`), không lấy từ token.
7. [CODE] `PUT /api/service-rate-plans/{id}` đổi giá điện nước tại chỗ và không ghi `rate_change_histories` (chỉ `updateServiceRate` có `saveHistory`).
8. [CODE] Ba cơ chế lập lịch: `JobScheduler` (`app.jobs.enabled=false`), `BillingJobs` của Toàn (`@Scheduled`, BẬT mặc định vì `matchIfMissing=true`), `VietJobs` của Việt (`@Scheduled`, TẮT mặc định). Hệ quả: nhắc hạn hóa đơn và đóng kỳ chỉ số chạy tự động theo mặc định; tính công nợ, nhắc nợ, cập nhật trạng thái hợp đồng thì không. Tài liệu nội bộ README nói "Mặc định TẮT" cho framework chung.
9. [CODE] Trùng tên lớp/bean giữa các package: `NotificationSender` (interface `shared.notification` và class `viet.service`), `SchedulerConfig` (`toan.service`) và `JobSchedulerConfig` (`shared.scheduler`), `ContractDtos` (`phi.dto` và `viet.dto`), `PaymentDtos` (`phi.dto` và `toan.dto`).
10. [CODE] `ContractService.create` đặt phòng `RENTED` ngay cả khi ngày bắt đầu hợp đồng ở tương lai.
11. [CODE] `ContractService.pdf` ghi `pdf_contract_url` và một dòng `contract_adjustments` (`PDF_GENERATED`) trong cùng giao dịch của một API `GET` (hàm `@Transactional` không phải `readOnly`).
12. [CODE] `MeterReadingService.confirmReading` không kiểm tra kỳ đã đóng; `adjustReading` thì có.
13. [CODE] Chuyển phòng (`transferRoom`) không cập nhật phương tiện hay kỳ tính tiền; chỉ chuyển người ở cùng (`room_residents`).
14. [CODE] Hóa đơn `OVERDUE` có trả một phần vẫn là `OVERDUE` (ưu tiên quá hạn hơn một phần) (`InvoiceBalanceCalculator`).
15. [CODE] `CheckoutService.applyDepositToInvoices` đặt `payment_status` thành `PAID`/`PARTIALLY_PAID` trực tiếp, bỏ qua `InvoiceBalanceCalculator` (không xét quá hạn).
16. [CODE] `BillingService` coi hợp đồng `EXPIRED` là "đủ điều kiện" (`LIVE_AT_MONTH` gồm `EXPIRED`), nên hợp đồng đã hết hạn vẫn được tính tiền cho tháng nó còn trong thời hạn.
17. [CODE] Code chết/không dùng: bảng `booking_requests` (entity có, không service), `PUBLISHED`/`CLOSED` của kỳ tính tiền, `REPAIRING`/`SUSPENDED` của phòng, `CANCELLED` của hóa đơn (không có đường đặt), kiểu OTP `PHONE`.
18. [CODE] `package-info.java` của `chi` là toàn bộ nội dung; `chi/*` không có code.
19. [CODE] Mã OTP quên mật khẩu/đăng ký được in vào log của server ở mức `INFO` (`LogNotificationSender`).
20. [CODE] Một số mô tả trong code/README đã không còn khớp: javadoc `InvoiceService.detailByShareToken` ghi "khong can dang nhap" nhưng `SecurityConfig` không `permitAll` đường dẫn này.
21. [CODE] Mã khởi tạo chứa `"1 + (int) othersInRoom(...)"` dùng truy vấn riêng cho từng hợp đồng (truy vấn lặp N lần trong `calculate`); không đo hiệu năng [KHÔNG RÕ ảnh hưởng].
22. [CODE] `docs/` chứa `LOI_CAN_SUA_CODE_TOAN.md`, `PROMPT_CHO_AI.md`: ghi chú nội bộ của nhóm, không phải đặc tả [KHÔNG RÕ độ cập nhật].

---

# Phần 14. Câu hỏi mở cho chủ dự án và danh sách [KHÔNG RÕ]

## 14.1 Câu hỏi nghiệp vụ code không trả lời được

1. Mục đích của việc đưa "Phạt trễ hạn" vào bảng tính tháng sau theo tổng ngày quá hạn (cộng dồn qua nhiều dòng nợ): có chủ đích hay chưa thống nhất với cách `DebtService` tính?
2. Doanh thu có cần gồm thanh toán một phần, cấn trừ tiền dư và khoản gạch bằng tiền cọc không? Hiện chỉ giao dịch `payment_transactions` `SUCCESS` được tính.
3. Tiền cọc thu lúc ký hợp đồng được ghi nhận ở đâu (chỉ có `receipt_vouchers` loại `DEPOSIT` và `contracts.deposit_amount`; không có bản ghi thu cọc nối với hợp đồng)?
4. Khi hợp đồng kết thúc/trả phòng, tài khoản đăng nhập của người thuê có cần tự khóa không?
5. Có cần bắt đổi mật khẩu lần đầu với mật khẩu tạm không?
6. Phụ phí thiết bị (`room_addon_fees`) có phải đưa vào hóa đơn hàng tháng không?
7. Hóa đơn đã thu tiền, hoặc đã `PAID`, có được phép sửa tổng (`adjustTotal`) không?
8. Một khách có nhiều hợp đồng cùng lúc: nghiệp vụ mong muốn là gì (hiện không chặn)?
9. Hợp đồng bắt đầu ở tương lai: phòng có nên giữ `VACANT` đến ngày bắt đầu không?
10. `CASHIER`, `METER_READER` có được gọi các API đổi đơn giá, sửa hóa đơn, đảo giao dịch không (hiện được, khi bảo mật bật)?
11. Điều kiện nghiệp vụ để bật `app.security.enabled=true` khi nào, và mặc định `false` có phải chủ ý lâu dài không?
12. Cách gửi hóa đơn cho khách không có tài khoản/email dự kiến là gì?
13. Đơn vị tiền tệ và làm tròn mong muốn (hiện làm tròn số nguyên đồng cho tiền phòng tỷ lệ ngày và điện nước chung; điện nước/dịch vụ giữ 2 chữ số thập phân)?
14. Khu trọ: hệ thống dự kiến một hay nhiều khu (giao diện cố định `PROPERTY_ID=1`, API nhận `propertyId`)?
15. Múi giờ vận hành dự kiến là gì (hiện lấy theo máy chủ)?
16. Phần của Chi (phòng, khách, khu trọ, mẫu hợp đồng, yêu cầu thuê/giữ chỗ) và UC124-132 có nằm trong phạm vi nộp không?

## 14.2 Các mục [KHÔNG RÕ] trong tài liệu

1. Kiểu dữ liệu/độ dài giữa entity và cột SQL chưa được so sánh (chỉ so tên bảng và tên cột). (Phần 4)
2. Hành vi `BackupService.restore` trên DB thật, tác động khi đang có người dùng. (Phần 9 #26, 11.3)
3. `EquipmentService` điều kiện chi tiết từng bước chuyển trạng thái yêu cầu thiết bị. (5.10)
4. Kiểm sở hữu bản ghi trong `maintenanceService.rate`, `maintenanceService.mine`, `equipmentService.*` (chỉ thấy chữ ký hàm nhận `tenantId`). (8.4 #5)
5. Vai trò nào thấy menu "Nhân viên", "Nhật ký", "Cài đặt" ở giao diện. (10.1)
6. Quy tắc validate chỉ có ở frontend (chưa rà từng form). (7.3)
7. Định dạng/độ dài hồ sơ khách `tenants` và người ở cùng khi nhập (phần của Chi chưa có code; `OccupancyService.fill` chưa đọc). (7.2)
8. Cấu hình bằng biến môi trường hệ điều hành thay cho `application-local.properties`. (10.5)
9. Tác động hiệu năng của truy vấn lặp trong `BillingService.calculate`. (Phần 13 #21)
10. Tỷ lệ bao phủ test; hiệu năng/tải. (Phần 12, 11.5)
11. Điểm mâu thuẫn giữa trạng thái thực tế của callback đồng thời (race) cho cùng một giao dịch: chỉ [SUY RA], chưa kiểm bằng test đồng thời. (Phần 9 #13)
12. Ứng dụng thật chưa được khởi động trong phiên này nên các khẳng định về khởi động thành công (`ddl-auto=validate` khớp 56 bảng) chỉ dựa trên so sánh tên bảng/cột bằng script, không dựa trên một lần khởi động.

---

# Phần 15. Ma trận truy vết 132 use case

Nguồn danh sách: `docs/Nhom_7_47_usecase_khutro_4MM_PhanCongMoi.xlsx`, sheet "Phân Công" (132 UC, cột "Người Phụ Trách"). Tên UC lấy nguyên từ Excel; mức trạng thái và bằng chứng do code quyết định.

Giải thích mức:
- ĐÃ CODE: có backend chạy được và có màn giao diện dùng nó (hoặc UC là cơ chế nền như phân quyền).
- CÓ BACKEND CHƯA CÓ GIAO DIỆN: có endpoint/service nhưng giao diện không gọi.
- CHƯA LÀM: không tìm thấy code backend tương ứng.

[CODE] Việc xác định "giao diện có gọi" dựa trên danh sách chuỗi `/api/...` trong `frontend/src` (Phần 10.2); chưa mở từng màn để kiểm nút bấm [KHÔNG RÕ độ chính xác từng nút].

| UC | Người được giao | Tên (theo Excel) | Mức trạng thái | Bằng chứng / ghi chú |
|---|---|---|---|---|
| 1 | Phi | Đăng ký tài khoản chủ trọ & xác thực qua email | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `RegistrationService`, `AuthController.register`; frontend không có màn đăng ký |
| 2 | Phi | Xác thực tài khoản qua số điện thoại (OTP SMS) | CÓ BACKEND CHƯA CÓ GIAO DIỆN | chỉ xác thực email (`verify-email`, `resend-verification`); xác thực SĐT bằng SMS chưa làm (không endpoint dùng type `PHONE`); không có màn giao diện |
| 3 | Phi | Đăng nhập / đăng xuất & duy trì phiên đăng nhập theo vai trò | ĐÃ CODE | `AuthService`, `AuthController`, `frontend/src/pages/login.ts` |
| 4 | Phi | Quên & đặt lại mật khẩu qua email | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `PasswordRecoveryService`; UI chỉ có bước xin mã (`login.ts:46-54`), không có màn nhập mã và đặt lại; OTP chỉ in ra log |
| 5 | Phi | Đổi mật khẩu | ĐÃ CODE | `AccountService`, `frontend/src/pages/profile.ts` |
| 6 | Phi | Cập nhật thông tin cá nhân & số điện thoại liên hệ | ĐÃ CODE | `AccountService`, `frontend/src/pages/profile.ts` |
| 7 | Phi | Cập nhật ảnh đại diện | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `AccountService`, `AccountController /avatar`; frontend chỉ hiển thị, không có chỗ tải lên |
| 8 | Phi | Thêm tài khoản nhân viên quản lý nhà trọ | ĐÃ CODE | `StaffService`, `frontend/src/pages/staff.ts` |
| 9 | Phi | Cập nhật thông tin, khóa / mở khóa tài khoản nhân viên | ĐÃ CODE | `StaffService`, `frontend/src/pages/staff.ts` |
| 10 | Phi | Cấp tài khoản đăng nhập cho người thuê (liên kết hồ sơ khách thuê) | ĐÃ CODE | `TenantAccountService`, `frontend/src/pages/tenants.ts` |
| 11 | Phi | Phân quyền theo vai trò (Chủ trọ / Nhân viên / Người thuê) | ĐÃ CODE | `SecurityConfig`, `@PreAuthorize`, menu theo vai trò ở `frontend/src/ui/shell.ts` |
| 12 | Phi | Phân quyền chi tiết cho nhân viên thu tiền & ghi điện nước | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `RolePermissions`, `PermissionController`; không có màn giao diện |
| 13 | Phi | Xem lịch sử đăng nhập & nhật ký thao tác của người dùng | ĐÃ CODE | `ActivityLogController`, `frontend/src/pages/activity.ts` |
| 14 | Chi | Cập nhật thông tin khu nhà trọ (tên, địa chỉ) & liên hệ hỗ trợ | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 15 | Chi | Quản lý tầng của khu nhà trọ (thêm, sửa, xóa) | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 16 | Chi | Quản lý loại phòng & thiết lập giá thuê theo loại phòng | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 17 | Chi | Thêm phòng trọ mới (mã phòng, tầng, loại phòng, diện tích, sức chứa) | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 18 | Chi | Cập nhật phòng (mã, diện tích, sức chứa, giá thuê, tiền cọc) | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 19 | Chi | Tra cứu thông tin chi tiết phòng theo mã phòng | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 20 | Chi | Tìm kiếm & lọc phòng (tầng, diện tích, giá thuê, tình trạng sử dụng) | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 21 | Chi | Xem sơ đồ phòng theo tầng & trạng thái từng phòng | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 22 | Chi | Thêm hình ảnh phòng trọ (tải lên nhiều ảnh) | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 23 | Chi | Cập nhật & xóa hình ảnh phòng trọ | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 24 | Chi | Quản lý danh mục thiết bị & nội thất chuẩn của khu trọ | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 25 | Chi | Ghi nhận trang thiết bị có sẵn trong phòng (thêm, sửa, xóa) | CHƯA LÀM | không có service nào ghi `room_furnishings` hay trạng thái phòng; entity có nhưng không có API |
| 26 | Chi | Ghi nhận tình trạng nội thất phòng | CHƯA LÀM | không có service nào ghi `room_furnishings` hay trạng thái phòng; entity có nhưng không có API |
| 27 | Chi | Quản lý công tơ điện & đồng hồ nước của phòng | CHƯA LÀM | không có service nào ghi `room_furnishings` hay trạng thái phòng; entity có nhưng không có API |
| 28 | Chi | Cập nhật trạng thái phòng (trống / đang thuê / sửa chữa / ngưng) | CHƯA LÀM | không có service nào ghi `room_furnishings` hay trạng thái phòng; entity có nhưng không có API |
| 29 | Chi | Quản lý phòng chờ vệ sinh & xác nhận sẵn sàng cho thuê lại | CHƯA LÀM | không có service nào ghi `room_furnishings` hay trạng thái phòng; entity có nhưng không có API |
| 30 | Chi | Thêm hồ sơ khách thuê mới (cá nhân, liên hệ, cư trú, nghề nghiệp) | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 31 | Chi | Cập nhật hồ sơ khách thuê & ngày bắt đầu / kết thúc cư trú | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 32 | Chi | Quản lý CCCD (số, ảnh 2 mặt) & xác minh thông tin khách thuê | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 33 | Chi | Quản lý người ở cùng phòng (thêm, sửa, xóa) | ĐÃ CODE | `OccupancyService`, `OccupancyController`, `frontend/src/ui/occupancy.ts` (code do Phi viết dù UC giao cho Chi) |
| 34 | Chi | Quản lý phương tiện của khách thuê (biển số, loại xe) | ĐÃ CODE | `OccupancyService`, `OccupancyController`, `frontend/src/ui/occupancy.ts` (code do Phi viết dù UC giao cho Chi) |
| 35 | Chi | Tra cứu khách thuê (SĐT, CCCD) & lịch sử thuê phòng | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 36 | Chi | Người thuê xem thông tin phòng, hợp đồng & người ở cùng của mình | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 37 | Chi | Gửi yêu cầu thuê & giữ chỗ (chọn phòng, ngày thuê, số người ở) | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 38 | Chi | Xác nhận / từ chối yêu cầu thuê phòng (kèm lý do) | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 39 | Chi | Xác nhận số tiền & ghi nhận thanh toán tiền đặt cọc | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 40 | Chi | Quản lý mẫu hợp đồng thuê phòng (thêm, sửa, xóa) | CHƯA LÀM | package `chi` chỉ có `package-info.java`; không có API; giao diện dùng dữ liệu mẫu `frontend/src/data/sample.ts`. UC36: người thuê chỉ xem được hợp đồng của mình (`/api/me/contracts`) |
| 41 | Phi | Lập hợp đồng thuê (chọn mẫu, thời hạn, ngày bắt đầu / kết thúc) | ĐÃ CODE | `ContractService`, `ContractController`, `frontend/src/pages/contracts.ts` |
| 42 | Phi | Thiết lập giá thuê, tiền cọc, chu kỳ & hạn thanh toán trong hợp đồng | ĐÃ CODE | `ContractService`, `ContractController`, `frontend/src/pages/contracts.ts` |
| 43 | Phi | Thiết lập điều khoản thanh toán (phạt trễ hạn, hoàn trả tiền cọc) | ĐÃ CODE | `ContractService`, `ContractController`, `frontend/src/pages/contracts.ts` |
| 44 | Phi | Thiết lập điều khoản sử dụng điện nước & dịch vụ chung | ĐÃ CODE | `ContractService`, `ContractController`, `frontend/src/pages/contracts.ts` |
| 45 | Phi | Tạo bản hợp đồng điện tử theo mẫu (xuất PDF) | ĐÃ CODE | `ContractService`, `ContractController`, `frontend/src/pages/contracts.ts` |
| 46 | Phi | Gửi hợp đồng cho khách thuê & xác nhận bằng chữ ký điện tử | ĐÃ CODE | `ContractService`, `ContractController`, `frontend/src/pages/contracts.ts` |
| 47 | Phi | Tra cứu hợp đồng, xem lịch sử thay đổi & tải PDF | ĐÃ CODE | `ContractService`, `ContractController`, `frontend/src/pages/contracts.ts` |
| 48 | Phi | Gia hạn hợp đồng thuê phòng | ĐÃ CODE | `ContractService`, `ContractController`, `frontend/src/pages/contracts.ts` |
| 49 | Phi | Điều chỉnh giá thuê & thông tin người thuê trong hợp đồng | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `ContractService.adjust` (`PATCH /api/contracts/{id}`); giao diện không gọi `PATCH` (sửa giá có qua UC42) |
| 50 | Phi | Chuyển phòng cho khách thuê (đổi phòng, chuyển hợp đồng, chốt chỉ số) | ĐÃ CODE | `ContractService`, `ContractController`, `frontend/src/pages/contracts.ts` |
| 51 | Phi | Chấm dứt hợp đồng thuê phòng (kèm lý do) | ĐÃ CODE | `ContractService`, `ContractController`, `frontend/src/pages/contracts.ts` |
| 52 | Việt | Tự động nhắc hợp đồng sắp hết hạn | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `ContractExpiryService`, `VietJobs` (tắt mặc định), `EquipmentController /contracts/expiring|refresh-status`; không có màn giao diện |
| 53 | Toàn | Tạo kỳ ghi nhận chỉ số điện nước | ĐÃ CODE | `MeterReadingService`, `frontend/src/pages/meters.ts` |
| 54 | Toàn | Nhập chỉ số điện nước đầu / cuối kỳ theo phòng (tự tính tiêu thụ) | ĐÃ CODE | `MeterReadingService`, `frontend/src/pages/meters.ts` |
| 55 | Toàn | Ghi nhận người chốt chỉ số & tải ảnh công tơ / đồng hồ nước | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `POST /api/meter-readings/{id}/photos` chỉ lưu URL; giao diện không có |
| 56 | Toàn | Xác nhận & điều chỉnh chỉ số điện nước đã nhập (kèm lý do) | ĐÃ CODE | `MeterReadingService`, `frontend/src/pages/meters.ts` |
| 57 | Toàn | Cảnh báo chỉ số điện / nước tăng đột biến | ĐÃ CODE | `MeterReadingService`, `frontend/src/pages/meters.ts` |
| 58 | Toàn | Tra cứu lịch sử tiêu thụ điện, nước của phòng | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `/api/rooms/{id}/consumption`, `/consumption-comparison`, `/meter-readings/move-in|move-out`; giao diện không gọi |
| 59 | Toàn | So sánh lượng điện, nước tiêu thụ giữa các tháng (biểu đồ) | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `/api/rooms/{id}/consumption`, `/consumption-comparison`, `/meter-readings/move-in|move-out`; giao diện không gọi |
| 60 | Toàn | Ghi nhận chỉ số điện nước khi bắt đầu thuê & khi trả phòng | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `/api/rooms/{id}/consumption`, `/consumption-comparison`, `/meter-readings/move-in|move-out`; giao diện không gọi |
| 61 | Toàn | Thiết lập đơn giá điện (kWh) & nước (m³) | ĐÃ CODE | `ServiceRateService`, `frontend/src/pages/settings.ts` |
| 62 | Toàn | Thiết lập phí dịch vụ hàng tháng (vệ sinh, Internet, gửi xe, rác) | ĐÃ CODE | `ServiceRateService`, `frontend/src/pages/settings.ts` |
| 63 | Toàn | Thiết lập khoản phụ thu phát sinh & miễn giảm tiền phòng | ĐÃ CODE | `ServiceRateService`, `frontend/src/pages/settings.ts` |
| 64 | Toàn | Quản lý thời điểm áp dụng & lịch sử thay đổi đơn giá, bảng giá | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `GET /api/rate-histories`; giao diện không gọi; bảng giá điện nước sửa tại chỗ không ghi lịch sử |
| 65 | Phi | Ghi nhận hóa đơn điện, nước tổng của khu trọ (đầu vào để phân bổ) | ĐÃ CODE | gián tiếp: chi phí nhóm "Điện nước chung" ở UC117 làm đầu vào phân bổ (`BillingService.sharedCost`); không có chức năng riêng cho hóa đơn điện nước tổng |
| 66 | Phi | Tạo kỳ tính tiền hàng tháng & xác định phòng có hợp đồng hiệu lực | ĐÃ CODE | `BillingService`, `BillingController`, `frontend/src/pages/billing.ts` |
| 67 | Phi | Kiểm tra dữ liệu đầu vào trước khi tính tiền | ĐÃ CODE | `BillingService`, `BillingController`, `frontend/src/pages/billing.ts` |
| 68 | Phi | Tính tiền thuê phòng (theo tháng, ngày thực tế, vào / trả giữa tháng) | ĐÃ CODE | `BillingService`, `BillingController`, `frontend/src/pages/billing.ts` |
| 69 | Phi | Tính tiền điện, nước theo lượng tiêu thụ | ĐÃ CODE | `BillingService`, `BillingController`, `frontend/src/pages/billing.ts` |
| 70 | Phi | Tính phí dịch vụ hàng tháng (Internet, vệ sinh, gửi xe, rác, đầu người) | ĐÃ CODE | `BillingService`, `BillingController`, `frontend/src/pages/billing.ts` |
| 71 | Phi | Tính phụ phí, khoản giảm trừ & tiền phạt thanh toán trễ hạn | ĐÃ CODE | `BillingService`, `BillingController`, `frontend/src/pages/billing.ts` |
| 72 | Phi | Phân bổ điện nước & dịch vụ chung (theo số người ở / số phòng) | ĐÃ CODE | `BillingService`, `BillingController`, `frontend/src/pages/billing.ts` |
| 73 | Phi | Tính tổng tiền phải thanh toán của từng phòng | ĐÃ CODE | `BillingService`, `BillingController`, `frontend/src/pages/billing.ts` |
| 74 | Phi | Điều chỉnh khoản phí trong kỳ & tính lại khi đổi chỉ số / đơn giá | ĐÃ CODE | `BillingService`, `BillingController`, `frontend/src/pages/billing.ts` |
| 75 | Phi | Kiểm tra hóa đơn bất thường, xem chi tiết phí & xác nhận kết quả | ĐÃ CODE | `BillingService`, `BillingController`, `frontend/src/pages/billing.ts` |
| 76 | Toàn | Xem bảng tổng hợp tiền phải thu theo tháng & toàn khu | ĐÃ CODE | `InvoiceService`, `frontend/src/pages/invoices.ts` |
| 77 | Phi | Tạo hóa đơn hàng tháng cho từng phòng (mã HĐ, kỳ, hạn thanh toán) | ĐÃ CODE | `InvoiceService.issueBatch`, `invoices.ts` (`/api/invoices/batch`) |
| 78 | Toàn | Xem chi tiết hóa đơn (tiền phòng, điện, nước, dịch vụ) & tải / in PDF | ĐÃ CODE | `InvoiceService`, `frontend/src/pages/invoices.ts` |
| 79 | Toàn | Phát hành hóa đơn cho khách thuê & thông báo hóa đơn mới | ĐÃ CODE | `InvoiceService.issueBatch`, `invoices.ts` (`/api/invoices/batch`) |
| 80 | Toàn | Gửi hóa đơn cho khách thuê qua email | ĐÃ CODE | `InvoiceService.sendEmail`; nút gửi có ở `invoices.ts`, nhưng không có lớp gửi mail thật: thông báo ghi `PENDING` |
| 81 | Toàn | Gửi hóa đơn qua thông báo trên website & liên kết chia sẻ | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `sendToApp`, `adjustTotal`; giao diện không gọi |
| 82 | Toàn | Nhắc thanh toán trước hạn & thông báo hóa đơn quá hạn | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `BillingJobs` (bật mặc định, không có giao diện cấu hình) |
| 83 | Toàn | Thông báo khi hóa đơn được điều chỉnh | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `sendToApp`, `adjustTotal`; giao diện không gọi |
| 84 | Toàn | Tra cứu hóa đơn (mã, phòng, kỳ) & xem hóa đơn cần thanh toán | ĐÃ CODE | `InvoiceService`, `frontend/src/pages/invoices.ts` |
| 85 | Phi | Thanh toán tiền phòng bằng tiền mặt & chuyển khoản ngân hàng | ĐÃ CODE | `PaymentService`, `CashierPaymentController`, `frontend/src/pages/payments.ts` |
| 86 | Phi | Thanh toán tiền phòng qua mã QR & cổng thanh toán điện tử | ĐÃ CODE | `PaymentService`, `CashierPaymentController`, `frontend/src/pages/payments.ts` |
| 87 | Phi | Ghi nhận giao dịch thanh toán (số tiền, thời điểm, người tiếp nhận) | ĐÃ CODE | `PaymentService`, `CashierPaymentController`, `frontend/src/pages/payments.ts` |
| 88 | Phi | Xác nhận giao dịch chuyển khoản & đối chiếu nội dung với mã hóa đơn | ĐÃ CODE | `PaymentService`, `CashierPaymentController`, `frontend/src/pages/payments.ts` |
| 89 | Toàn | Tạo phiếu thu (tiền phòng, tiền đặt cọc, dịch vụ phát sinh) | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `ReceiptService`, `PartialPaymentService`; giao diện không gọi |
| 90 | Toàn | Gửi xác nhận thanh toán, tải & in biên nhận | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `ReceiptService`, `PartialPaymentService`; giao diện không gọi |
| 91 | Toàn | Ghi nhận thanh toán một phần / nhiều lần cho một hóa đơn | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `ReceiptService`, `PartialPaymentService`; giao diện không gọi |
| 92 | Toàn | Xử lý thanh toán dư: cấn trừ kỳ sau, hoàn tiền, hủy giao dịch sai | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `PrepaymentService`; giao diện chỉ có nút đảo giao dịch (`payments.ts`), phần tiền dư/cấn trừ/hoàn không có |
| 93 | Việt | Xác định số tiền còn nợ của từng phòng, từng khách thuê | ĐÃ CODE | `DebtService`, `DebtController`, `frontend/src/pages/debts.ts`, `me.ts` |
| 94 | Việt | Người thuê xem lịch sử thanh toán & công nợ của mình | ĐÃ CODE | `DebtService`, `DebtController`, `frontend/src/pages/debts.ts`, `me.ts` |
| 95 | Việt | Cập nhật số dư công nợ, ghi nhận nợ phát sinh & chuyển kỳ sau | ĐÃ CODE | `DebtService`, `DebtController`, `frontend/src/pages/debts.ts`, `me.ts` |
| 96 | Việt | Tính số ngày quá hạn & tiền phạt chậm thanh toán | ĐÃ CODE | `DebtService`, `DebtController`, `frontend/src/pages/debts.ts`, `me.ts` |
| 97 | Việt | Phân loại tình trạng thanh toán & khách thuê có công nợ kéo dài | ĐÃ CODE | `DebtService`, `DebtController`, `frontend/src/pages/debts.ts`, `me.ts` |
| 98 | Việt | Xem lịch sử, tổng công nợ toàn khu & xuất danh sách công nợ ra Excel | ĐÃ CODE | `DebtService`, `DebtController`, `frontend/src/pages/debts.ts`, `me.ts` |
| 99 | Việt | Nhắc nợ tự động (lần 1, lần 2, cảnh báo quá hạn) | ĐÃ CODE | `DebtService`, `DebtController`, `frontend/src/pages/debts.ts`, `me.ts` |
| 100 | Việt | Ghi nhận cam kết thanh toán, lý do chậm & lịch sử xử lý công nợ | ĐÃ CODE | `DebtService`, `DebtController`, `frontend/src/pages/debts.ts`, `me.ts` |
| 101 | Việt | Tiếp nhận yêu cầu trả phòng & xác nhận lịch kiểm tra phòng | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `CheckoutService`, `CheckoutController`; giao diện không có màn trả phòng |
| 102 | Việt | Ghi nhận hiện trạng phòng khi trả (ảnh chụp, thiết bị hư hỏng) | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `CheckoutService`, `CheckoutController`; giao diện không có màn trả phòng |
| 103 | Việt | Tính chi phí bồi thường tài sản hư hỏng | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `CheckoutService`, `CheckoutController`; giao diện không có màn trả phòng |
| 104 | Việt | Quyết toán tiền phòng, dịch vụ & công nợ khi trả phòng | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `CheckoutService`, `CheckoutController`; giao diện không có màn trả phòng |
| 105 | Việt | Khấu trừ công nợ & chi phí hư hỏng vào tiền đặt cọc | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `CheckoutService`, `CheckoutController`; giao diện không có màn trả phòng |
| 106 | Việt | Lập phiếu & ghi nhận thanh toán hoàn tiền đặt cọc | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `CheckoutService`, `CheckoutController`; giao diện không có màn trả phòng |
| 107 | Việt | Lập biên bản bàn giao phòng & xác nhận thanh lý hợp đồng | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `CheckoutService`, `CheckoutController`; giao diện không có màn trả phòng |
| 108 | Chi | Quản lý yêu cầu sử dụng thêm / lắp đặt thiết bị trong phòng | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `EquipmentService`, `EquipmentController` (code do Việt viết dù UC giao cho Chi); giao diện không có |
| 109 | Chi | Quản lý phí tiện ích bổ sung (thiết bị điện, dịch vụ theo kỳ) | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `EquipmentService`, `EquipmentController` (code do Việt viết dù UC giao cho Chi); giao diện không có |
| 110 | Việt | Ghi nhận sự cố mất điện / nước & yêu cầu sửa chữa điện, nước | ĐÃ CODE | `MaintenanceService`, `frontend/src/pages/maintenance.ts`, `me.ts` |
| 111 | Việt | Ghi nhận yêu cầu kiểm tra công tơ, đồng hồ nước & điều chỉnh hóa đơn | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `MaintenanceService.adjustInvoice`; giao diện không gọi |
| 112 | Việt | Gửi yêu cầu sửa chữa phòng trọ (kèm ảnh) & phân loại sự cố | ĐÃ CODE | `MaintenanceService`, `frontend/src/pages/maintenance.ts`, `me.ts` |
| 113 | Việt | Quản lý danh mục nhóm sự cố & nhóm thiết bị | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `IncidentCategoryService`; giao diện chỉ đọc danh mục |
| 114 | Việt | Phân công xử lý, cập nhật tiến độ, chi phí & xác nhận hoàn tất | ĐÃ CODE | `MaintenanceService`, `frontend/src/pages/maintenance.ts`, `me.ts` |
| 115 | Việt | Đánh giá chất lượng xử lý sự cố | ĐÃ CODE | `MaintenanceService`, `frontend/src/pages/maintenance.ts`, `me.ts` |
| 116 | Phi | Dashboard tổng quan (lấp đầy, doanh thu, công nợ, HĐ sắp hết hạn) | ĐÃ CODE | `ReportService`, `frontend/src/pages/dashboard.ts`, `reports.ts` |
| 117 | Việt | Quản lý chi phí vận hành khu trọ (bảo trì, vệ sinh, thuế, ...) | ĐÃ CODE | `OperatingExpenseService`, `frontend/src/pages/expenses.ts` |
| 118 | Phi | Báo cáo doanh thu & chi phí theo tháng / năm (xuất Excel) | ĐÃ CODE | `ReportService`, `frontend/src/pages/dashboard.ts`, `reports.ts` |
| 119 | Phi | Thiết lập tài khoản ngân hàng & mã QR nhận tiền | ĐÃ CODE | `BankAccountService`, `frontend/src/pages/settings.ts` |
| 120 | Phi | Cấu hình lịch tự động (chốt chỉ số, tính tiền, phát hành, nhắc nợ) | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `JobScheduler`, `JobController`; chỉ 1 job đăng ký vào framework; không có giao diện |
| 121 | Việt | Quản lý mẫu thông báo & kênh gửi (email, website) | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `NotificationTemplateService`; giao diện không có |
| 122 | Phi | Sao lưu & phục hồi dữ liệu hệ thống | CÓ BACKEND CHƯA CÓ GIAO DIỆN | `BackupService`; giao diện không có; restore chưa có bằng chứng đã thử trên DB thật |
| 123 | Việt | Trung tâm thông báo của người dùng (xem, đánh dấu đã đọc, xóa) | ĐÃ CODE | `NotificationCenterService`, `frontend/src/ui/bell.ts`, `me.ts` |
| 124 | Chi | Lập biên bản nhận phòng khi bắt đầu thuê (đối chiếu hiện trạng, thiết bị, chỉ số ban đầu, ảnh bàn giao) | CHƯA LÀM | UC mới thêm (README mục 3.1); không thấy code. UC126: không có endpoint hủy hóa đơn; UC127/128/129/130/131/132: không có endpoint tương ứng |
| 125 | Chi | Xuất danh sách phòng & khách thuê đang ở ra Excel (lọc theo tầng, trạng thái) | CHƯA LÀM | UC mới thêm (README mục 3.1); không thấy code. UC126: không có endpoint hủy hóa đơn; UC127/128/129/130/131/132: không có endpoint tương ứng |
| 126 | Toàn | Hủy hóa đơn phát hành nhầm (chỉ khi chưa thu tiền, bắt buộc ghi lý do, lưu nhật ký) | CHƯA LÀM | UC mới thêm (README mục 3.1); không thấy code. UC126: không có endpoint hủy hóa đơn; UC127/128/129/130/131/132: không có endpoint tương ứng |
| 127 | Toàn | Báo cáo thu tiền theo ngày / theo người thu & đối soát cuối ngày (xuất Excel) | CHƯA LÀM | UC mới thêm (README mục 3.1); không thấy code. UC126: không có endpoint hủy hóa đơn; UC127/128/129/130/131/132: không có endpoint tương ứng |
| 128 | Toàn | Xem lịch sử thao tác trên hóa đơn & giao dịch (phát hành, điều chỉnh, hủy, đảo giao dịch) | CHƯA LÀM | UC mới thêm (README mục 3.1); không thấy code. UC126: không có endpoint hủy hóa đơn; UC127/128/129/130/131/132: không có endpoint tương ứng |
| 129 | Toàn | Xuất bảng kê hóa đơn & phiếu thu theo tháng ra Excel / PDF | CHƯA LÀM | UC mới thêm (README mục 3.1); không thấy code. UC126: không có endpoint hủy hóa đơn; UC127/128/129/130/131/132: không có endpoint tương ứng |
| 130 | Việt | Gửi thông báo chung tới người thuê (toàn khu hoặc theo tầng) & theo dõi số người đã đọc | CHƯA LÀM | UC mới thêm (README mục 3.1); không thấy code. UC126: không có endpoint hủy hóa đơn; UC127/128/129/130/131/132: không có endpoint tương ứng |
| 131 | Việt | Thống kê sự cố & lịch sử bảo trì theo phòng, theo nhóm sự cố (xuất Excel) | CHƯA LÀM | UC mới thêm (README mục 3.1); không thấy code. UC126: không có endpoint hủy hóa đơn; UC127/128/129/130/131/132: không có endpoint tương ứng |
| 132 | Việt | Báo cáo hoàn tiền đặt cọc & quyết toán trả phòng theo tháng | CHƯA LÀM | UC mới thêm (README mục 3.1); không thấy code. UC126: không có endpoint hủy hóa đơn; UC127/128/129/130/131/132: không có endpoint tương ứng |

Tổng hợp theo mức (132 UC): CÓ BACKEND CHƯA CÓ GIAO DIỆN: 33; ĐÃ CODE: 65; CHƯA LÀM: 34

| Người được giao | ĐÃ CODE | CÓ BACKEND CHƯA CÓ GIAO DIỆN | CHƯA LÀM | KHÔNG RÕ | Tổng |
|---|---|---|---|---|---|
| Phi | 37 | 8 | 0 | 0 | 45 |
| Chi | 2 | 2 | 27 | 0 | 31 |
| Toàn | 12 | 12 | 4 | 0 | 28 |
| Việt | 14 | 11 | 3 | 0 | 28 |

