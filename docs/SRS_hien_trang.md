# SRS hiện trạng (as-built) của MiniBoardingHouse

Tài liệu này mô tả điều phần mềm THỰC SỰ làm tại thời điểm viết, rút ra từ code. Không mô tả điều phần mềm "nên" làm.

- Bản code được đọc: nhánh `main`, commit `8d19ba2` (2026-11-17), working tree sạch, `main` trùng `origin/main`.
- Người viết: Claude (đọc code, không chạy ứng dụng với DB thật trong phiên này; có chạy `mvn test`, xem Phần 12).
- Người đọc: QA không xem code. Mỗi nhận định có đường dẫn file (và tên hàm hoặc số dòng). Đường dẫn Java viết tắt: `P/` = `src/main/java/com/group7/minihouse/`.

## Nhãn bằng chứng

- [CODE] thấy trực tiếp trong code.
- [SUY RA] suy từ code, có nêu suy từ đâu.
- [KHÔNG RÕ] không tìm thấy hoặc không chắc. Tổng hợp ở Phần 14.

## Mục lục và các file

| Phần | Nội dung | File |
|---|---|---|
| 1-3 | Tổng quan, tác nhân, use case | file này |
| 4 | Mô hình dữ liệu (ERD, 56 bảng) | `SRS_04_du_lieu.md` (sinh tự động từ `db/group-7.sql`) |
| 5-7 | Vòng đời trạng thái, công thức, hard-code, validation | `SRS_05_07_trang_thai_cong_thuc_validation.md` |
| 8-9 | Phân quyền, tình huống ngoại lệ | `SRS_08_09_phan_quyen_ngoai_le.md` |
| 10-15 | Giao diện, phi chức năng, test, điểm đáng ngờ, câu hỏi mở, ma trận truy vết 132 UC | `SRS_10_15_giao_dien_test_nghi_van.md` |

## Kế hoạch đã thực hiện (Bước 1)

Module/thư mục đã đọc (số file Java main / test, đếm bằng `find`):

| Package | Main | Test | Nội dung |
|---|---|---|---|
| `shared` | 86 | 9 | cấu hình bảo mật, JWT, exception, export Excel/PDF, scheduler chung, lưu file, 56 entity |
| `phi` | 77 | 21 | tài khoản, đăng nhập, nhân viên, hợp đồng, tính tiền tháng, thanh toán, báo cáo, QR, sao lưu |
| `chi` | 4 | 0 | chỉ có `package-info.java`, KHÔNG có code nghiệp vụ |
| `toan` | 38 | 0 | đơn giá, chỉ số điện nước, hóa đơn, thanh toán một phần, tiền dư, phiếu thu, job |
| `viet` | 49 | 0 | trả phòng, công nợ, sự cố, thiết bị, chi phí vận hành, thông báo |
| `frontend/src` | 2.644 dòng TS | 0 | giao diện "Stayly" (Vite + TypeScript) |
| `db/` | | | `group-7.sql`, `seed.sql`, `sample-data*.sql`, `patch_01_users_unique.sql`, `minihouse-full.sql` |

[CODE] Package `chi` chỉ có 4 file `package-info.java` (`P/chi/controller|dto|repository|service/package-info.java`). Toàn bộ phần "quản lý phòng, khách thuê, khu trọ" do `chi` phụ trách trong README chưa có code. Hệ quả ở Phần 10 và Phần 15.

---

# Phần 1. Tổng quan

## 1.1 Mục đích, đối tượng dùng

- [CODE] Ứng dụng web quản lý nhà trọ: hợp đồng thuê, tính tiền hàng tháng, hóa đơn, thanh toán (tiền mặt, QR), công nợ, trả phòng, sự cố, chi phí, báo cáo (README.md, mục mô tả; các controller ở `P/phi|toan|viet/controller`).
- [CODE] Đối tượng dùng theo vai trò trong DB (`db/seed.sql`, `P/phi/service/RolePermissions.java:ROLES`): `LANDLORD` (chủ trọ), `MANAGER` (quản lý), `CASHIER` (thu ngân), `METER_READER` (ghi điện nước), `TENANT` (người thuê).
- [SUY RA] Đây là đồ án nhóm 7 của sinh viên (README.md có bảng phân công 4 người: Phi, Chi, Toàn, Việt). Mục đích nghiệp vụ chi tiết ngoài README không có trong code, xem Phần 14.

## 1.2 Tech stack

| Thành phần | Giá trị | Bằng chứng |
|---|---|---|
| Ngôn ngữ, JDK | Java 17 | `pom.xml` `<java.version>17</java.version>` |
| Framework | Spring Boot 3.3.5 (web, data-jpa, security, validation) | `pom.xml` parent + starters |
| ORM | JPA/Hibernate, `ddl-auto=validate`, `open-in-view=false`, naming `PhysicalNamingStrategyStandardImpl` | `src/main/resources/application.properties` |
| DB | SQL Server (driver `mssql-jdbc`), DB `MiniBoardingHouseDB`, cổng 1433 | `application.properties` (datasource.url) |
| Bảo mật | JWT HS256 (jjwt), BCrypt, không session | `P/shared/security/JwtService.java`, `P/shared/config/SecurityConfig.java` |
| Tài liệu API | springdoc-openapi (`/swagger-ui/**`, `/v3/api-docs/**`) | `pom.xml`, `SecurityConfig` (permitAll) |
| Excel/PDF | Apache POI 5.2.5, OpenPDF 1.3.43 | `pom.xml`, `P/shared/export/TableExporter.java` |
| Giao diện | Vite 5 + TypeScript 5.6, không có framework UI, thư viện `lucide` (icon) và font `be-vietnam-pro` | `frontend/package.json` |
| Test | spring-boot-starter-test (JUnit 5, Mockito) | `pom.xml` |

[CODE] Không có dependency gửi email (`spring-boot-starter-mail`) trong `pom.xml`. Không có dependency SDK ngân hàng/cổng thanh toán.

## 1.3 Cách cài và chạy

Theo `README.md` mục chạy nhanh và `HUONG_DAN_CHAY.md`:

1. [CODE] Tạo DB `MiniBoardingHouseDB` trên SQL Server, chạy `db/group-7.sql` (tạo 56 bảng).
2. [CODE] Tạo file `src/main/resources/application-local.properties` (bị git ignore, `.gitignore` dòng `application-local.properties`) chứa tài khoản DB. File này được nạp bằng `spring.config.import=optional:classpath:application-local.properties`.
3. [CODE] Chạy `db/seed.sql`: nạp 5 vai trò, 22 quyền, tài khoản `admin@minihouse.local` / `Admin@123` (README mục 3).
4. [CODE] Chạy `mvn spring-boot:run` (cổng mặc định 8080; không thấy `server.port` trong `application.properties`).
5. [CODE] Giao diện: `cd frontend && npm install && npm run build` (script `build` = `tsc --noEmit && vite build`, `frontend/package.json`). Kết quả build vào `src/main/resources/static` (bị git ignore) rồi mở `http://localhost:8080/`.
6. [CODE] Dữ liệu mẫu tùy chọn: `db/sample-data.sql`, `db/sample-data-thang-12.sql`.

Cấu hình quan trọng (tên khóa, mặc định trong `application.properties` hoặc `@Value`):

| Khóa | Mặc định | Ý nghĩa | Bằng chứng |
|---|---|---|---|
| `app.security.enabled` | `false` | false: mọi API mở, không cần token, `@PreAuthorize` bị bỏ qua. true: bắt buộc JWT | `application.properties`, `SecurityConfig.java:44`, `MethodSecurityConfig.java:13` |
| `app.jwt.secret` | `dev-only-secret-minihouse-group7-change-me-2026` | khóa ký JWT (có sẵn trong file được commit) | `application.properties` |
| `app.jwt.expiration-minutes` | 480 | hạn token | `JwtService.java:22` |
| `app.upload.dir` | `uploads` | thư mục lưu file | `FileStorage.java:28` |
| `spring.servlet.multipart.max-file-size` | 5MB (request 6MB) | giới hạn upload | `application.properties` |
| `app.payment.callback-secret` | rỗng | bí mật cho callback thanh toán; rỗng thì callback bị từ chối 403 | `CashierPaymentController.java:45,99` |
| `app.jobs.enabled` | `false` | bật framework job chung (`ScheduledJob`) | `JobScheduler.java:38` |
| `minihouse.scheduler.enabled` | không đặt | `BillingJobs` (Toàn) chạy khi thiếu khóa này (`matchIfMissing=true`), `VietJobs` chỉ chạy khi `=true` | `BillingJobs.java:24`, `VietJobs.java:17` |
| `app.backup.dir`, `app.export.pdf-font` | rỗng | thư mục sao lưu, font PDF | `BackupService.java:57`, `TableExporter.java:62` |

---

# Phần 2. Tác nhân

| Tác nhân | Vai trò trong DB | Giao diện dùng | Đăng nhập |
|---|---|---|---|
| Chủ trọ | `LANDLORD` | giao diện nhân viên của Stayly (menu đầy đủ) | email hoặc SĐT + mật khẩu, JWT |
| Quản lý | `MANAGER` | giao diện nhân viên | như trên |
| Thu ngân | `CASHIER` | giao diện nhân viên | như trên |
| Nhân viên ghi điện nước | `METER_READER` | giao diện nhân viên | như trên |
| Người thuê | `TENANT` | khu vực người thuê của Stayly (`frontend/src/pages/me.ts`) | như trên; tài khoản do nhân viên cấp |
| Cổng thanh toán / ngân hàng | hệ thống ngoài | không có giao diện | gọi `POST /api/payments/callback` với header `X-Callback-Secret` |
| Hệ thống (scheduler) | không phải người dùng | không có | chạy theo cron nếu bật |
| Khách chưa đăng nhập | không có vai trò | trang đăng nhập, quên mật khẩu | không cần |

[CODE] Cách đăng nhập: `POST /api/auth/login` với `identifier` (email hoặc SĐT) và `password` (`P/phi/service/AuthService.java:login`). Tìm bằng `findByEmailOrPhoneNumber`, so mật khẩu bằng BCrypt (`passwordEncoder.matches`), trả cùng thông báo "Sai tai khoan hoac mat khau" cho cả hai lỗi. Tài khoản không `ACTIVE` bị 403. Token chứa `sub`=userId, `email`, `name`, `roles`, `tenantId`, hạn 480 phút.

[CODE] Người thuê nối với hồ sơ `tenants` qua SĐT: `AuthService.findTenantId` tìm `tenants` theo `phone_number` của user, và từ chối nếu `users.created_at` sớm hơn `tenants.created_at` (SĐT bị tái sử dụng).

[CODE] Khi `app.security.enabled=false`, không ai cần đăng nhập, `@AuthenticationPrincipal AuthUser` là `null`, và các API cần biết người thao tác báo lỗi 401 "Can dang nhap..." (ví dụ `PaymentService.user`). Chi tiết ở Phần 8.

[CODE] Tài khoản chủ trọ tự đăng ký qua `POST /api/auth/register` (UC1, `RegistrationService`), nhân viên do `LANDLORD` tạo (`POST /api/staff`), người thuê do `LANDLORD`/`MANAGER` cấp (`POST /api/tenants/{id}/account`).

---

# Phần 3. Danh sách use case (theo code thực tế)

Mã UC lấy theo file Excel `docs/Nhom_7_47_usecase_khutro_4MM_PhanCongMoi.xlsx` (sheet "Phân Công", 132 UC) và comment trong code. Bảng dưới liệt kê các use case ĐÃ có code. UC chưa có code hoặc chưa có giao diện xem Phần 15.

Viết tắt: "Staff" = LANDLORD, MANAGER (và thêm CASHIER, METER_READER khi nói rõ). "Bảo mật bật" = `app.security.enabled=true`. Mọi API của nhân viên khi bảo mật bật chỉ cho 4 vai trò nhân viên (`SecurityConfig.anyRequest().hasAnyRole(...)`).

## 3.1 Tài khoản và người dùng

| Mã | Tên | Tác nhân | Tiền điều kiện | Các bước chính | Luồng lỗi/thay thế | Kết quả | Dữ liệu thay đổi | Bằng chứng |
|---|---|---|---|---|---|---|---|---|
| UC1 | Đăng ký chủ trọ | Khách | email chưa dùng; mật khẩu >= 8 ký tự gồm chữ và số | `POST /api/auth/register` (họ tên, email, mật khẩu) -> tạo user `INACTIVE` + role `LANDLORD` -> sinh OTP 6 số gửi qua `NotificationSender` | email trùng và tài khoản đã kích hoạt: lỗi; đăng ký lại trên tài khoản chờ xác thực: ghi đè; email sai định dạng 400 | user `INACTIVE` chờ xác thực | `users`, `user_roles`, `user_verifications` | `P/phi/service/RegistrationService.java`, `AuthController.register` |
| UC2 | Xác thực email | Khách | đã đăng ký | `POST /api/auth/verify-email` (email, code); `POST /api/auth/resend-verification` | mã sai, hết hạn, đã dùng: 400 cùng thông báo `BAD_CODE`; xin lại trong 60 giây: 429 | user chuyển `ACTIVE` | `users.status`, `user_verifications` | `RegistrationService`, `OtpService` |
| UC2 (SĐT) | Xác thực SĐT bằng OTP SMS | | | KHÔNG có code: `OtpService` hỗ trợ type `PHONE` nhưng không có endpoint dùng (chỉ `"EMAIL"` ở `RegistrationService.java:27`) | | | | `grep "PHONE"` chỉ thấy ở DB CHECK và `OtpService` mô tả |
| UC3 | Đăng nhập, đăng xuất | Mọi người dùng | tài khoản `ACTIVE` | `POST /api/auth/login` -> JWT; `POST /api/auth/logout` chỉ ghi nhật ký `LOGOUT` (token vẫn còn hiệu lực đến hết hạn) | sai tài khoản/mật khẩu 401; tài khoản không ACTIVE 403; người thuê chưa nối hồ sơ 409 | token + thông tin người dùng | `user_activity_logs` (LOGIN, LOGOUT) | `AuthService.login`, `AuthController.logout` |
| UC4 | Quên và đặt lại mật khẩu | Khách | tài khoản `ACTIVE` | `POST /api/auth/forgot-password` (luôn trả cùng một thông báo); sinh OTP `RECOVERY`, gửi qua `NotificationSender` (xem 3.1 ghi chú); `POST /api/auth/reset-password` | mã sai: 400; mật khẩu yếu: 400 | mật khẩu đổi | `users.password_hash`, `user_verifications`, `user_activity_logs` | `PasswordRecoveryService` |
| UC5 | Đổi mật khẩu | Đã đăng nhập | biết mật khẩu cũ | `POST /api/auth/password` | mật khẩu cũ sai, yếu | mật khẩu đổi; token cũ vẫn dùng được | `users.password_hash`, nhật ký | `AccountService`, `AccountController` |
| UC6 | Hồ sơ cá nhân | Đã đăng nhập | | `GET/PUT /api/auth/profile` | người thuê không đổi được SĐT; PUT không gửi `phoneNumber` thì giữ nguyên | thông tin cập nhật | `users` | `AccountService`, `AccountController` |
| UC7 | Ảnh đại diện | Đã đăng nhập | ảnh <= 2 MB | `PUT /api/auth/avatar` (multipart), `DELETE`, `GET /api/auth/avatars/{name}` (công khai khi bảo mật bật) | sai loại, quá lớn | ảnh lưu `uploads/avatars` | `users`, file | `AccountService.AVATAR_MAX_BYTES`, `FileStorage` |
| UC8 | Thêm nhân viên | LANDLORD | | `POST /api/staff` -> mật khẩu tạm 10 ký tự trả về một lần | thiếu email và SĐT, vai trò không hợp lệ (chỉ MANAGER, CASHIER, METER_READER) | tài khoản nhân viên `ACTIVE` | `users`, `user_roles` | `StaffService`, `StaffController` |
| UC9 | Sửa, khóa/mở khóa nhân viên | LANDLORD | | `PUT /api/staff/{id}`, `PATCH /api/staff/{id}/status` (ACTIVE/LOCKED) | id không phải nhân viên: 404 | thông tin, vai trò, trạng thái đổi | `users`, `user_roles`, nhật ký | `StaffService` |
| UC10 | Cấp tài khoản người thuê | LANDLORD, MANAGER | hồ sơ `tenants` có SĐT | `POST /api/tenants/{id}/account` -> user `ACTIVE` role `TENANT`, mật khẩu tạm 10 ký tự trả một lần; `GET .../account`; `PATCH .../account/status` (ACTIVE/LOCKED) | SĐT đã dùng bởi nhân viên 409; tài khoản đã có 409; tài khoản cũ tạo trước hồ sơ: bị vô hiệu hóa (INACTIVE) và giải phóng SĐT/email | tài khoản người thuê | `users`, `user_roles` | `TenantAccountService` |
| UC11, UC12 | Phân quyền theo vai trò | hệ thống | | quyền kiểm bằng `@PreAuthorize` + luật URL; bảng quyền mặc định `RolePermissions` chỉ để tra cứu: `GET /api/permissions`, `/api/roles/permissions`, `/api/auth/permissions` | | | | `SecurityConfig`, `RolePermissions`, `PermissionController` |
| UC13 | Nhật ký thao tác | LANDLORD (toàn bộ), mọi người (của mình) | | `GET /api/activity-logs` (+ `/export`), `GET /api/auth/activity` | `from` sau `to`: 400; xuất tối đa 5.000 dòng | danh sách | đọc `user_activity_logs` | `ActivityLogService`, `ActivityLogController` |

Ghi chú UC1/UC4: [CODE] `NotificationSender` được inject là `LogNotificationSender` (`P/shared/notification/LogNotificationSender.java`), chỉ in nội dung ra log (`log.info("[THONG BAO GIA LAP] ...")`). Không có lớp nào gửi email/SMS thật. Mã OTP chỉ xem được trong log của server. Đây là mô tả hiện trạng, không phải đánh giá.

## 3.2 Hợp đồng

Phòng và khách thuê là bảng của Chi nên `ContractService` đọc/ghi qua `EntityManager` (`ContractService.java` javadoc).

| Mã | Tên | Tác nhân | Tiền điều kiện | Các bước chính | Luồng lỗi/thay thế | Kết quả | Dữ liệu thay đổi | Bằng chứng |
|---|---|---|---|---|---|---|---|---|
| UC41 | Lập hợp đồng | LANDLORD, MANAGER | phòng `VACANT`, chưa có hợp đồng ACTIVE/EXPIRING_SOON | `POST /api/contracts`: giá thuê và cọc mặc định lấy từ phòng, chu kỳ mặc định 1 tháng, ngày hạn mặc định 5, phạt trễ mặc định 0, trạng thái `ACTIVE`, phòng sang `RENTED`, mã `HDyyyyMMdd-<mã phòng>` | ngày kết thúc <= ngày bắt đầu: 400; phòng không trống: 409; phạt âm: 400 | hợp đồng ACTIVE | `rental_contracts`, `rooms.status`, nhật ký | `ContractService.create` |
| UC42-44 | Sửa giá/cọc/chu kỳ, điều khoản thanh toán, điều khoản điện nước | Staff | hợp đồng ACTIVE hoặc EXPIRING_SOON | `PUT /api/contracts/{id}/pricing`, `/payment-terms`, `/utility-terms` | hợp đồng EXPIRED/TERMINATED: 409 | giá trị mới; ghi `contract_adjustments` | `rental_contracts`, `contract_adjustments` | `updatePricing`, `updatePaymentTerms`, `updateUtilityTerms` |
| UC45 | PDF hợp đồng | Staff | | `GET /api/contracts/{id}/pdf` tạo PDF văn bản lúc tải, không lưu file | | PDF | `pdf_contract_url` đặt thành `/api/contracts/{id}/pdf` | `ContractService.pdf`, `pdfLines` |
| UC46 | Gửi hợp đồng và ký | Staff, Người thuê | hợp đồng còn hiệu lực | `POST /{id}/send` tạo `user_notifications` cho user có SĐT trùng; `POST /{id}/sign` (nhân viên) hoặc `POST /api/me/contracts/{id}/sign` (người thuê) lưu ảnh chữ ký <= 1 MB | đã ký rồi: 409; khách chưa có tài khoản: trả thông báo văn bản, không lỗi | `digital_signature_url` đặt | `rental_contracts`, `user_notifications`, file | `send`, `sign`, `signByTenant` |
| UC47 | Tra cứu hợp đồng | Staff; người thuê của mình | | `GET /api/contracts?keyword&status&page&size`, `GET /{id}` (kèm lịch sử), `GET /api/me/contracts`, `/api/me/contracts/{id}` | trạng thái sai: 400; người thuê xem hợp đồng người khác: 404 | danh sách trang (<= 100/trang) | đọc | `search`, `detail`, `detailOfTenant` |
| UC48 | Gia hạn | Staff | không TERMINATED; ngày mới > ngày hết hạn hiện tại | `PUT /{id}/extend`; hợp đồng EXPIRED thì chỉ gia hạn nếu phòng chưa có hợp đồng khác | ngày không hợp lệ 400; phòng đã có hợp đồng khác 409 | hợp đồng ACTIVE với ngày mới | `rental_contracts`, `contract_adjustments`, `rooms.status` | `ContractService.extend` |
| UC49 | Điều chỉnh giá thuê, đổi người thuê | Staff | hợp đồng còn hiệu lực | `PATCH /api/contracts/{id}` (giá hoặc `tenantId`) | giá <= 0: 400; đổi người thì xóa chữ ký (phải ký lại) | | `rental_contracts`, `contract_adjustments` | `adjust` |
| UC50 | Chuyển phòng | Staff | hợp đồng còn hiệu lực, phòng mới VACANT | `POST /{id}/transfer`; tùy chọn chốt chỉ số điện nước phòng cũ; chuyển người ở cùng sang phòng mới; phòng cũ VACANT, phòng mới RENTED | phòng mới trùng/không trống 409; chốt chỉ số thiếu tham số 400 | | `rental_contracts.room_id`, `rooms.status`, `room_residents`, `meter_readings` | `transferRoom` |
| UC51 | Chấm dứt hợp đồng | Staff | hợp đồng còn hiệu lực | `POST /{id}/terminate` (bắt buộc lý do <= 255 ký tự): hợp đồng `TERMINATED`, phòng `VACANT`, ghi `resident_stay_history`, ngừng toàn bộ người ở cùng | thiếu lý do 400 | | `rental_contracts`, `rooms`, `resident_stay_history`, `room_residents`, `contract_adjustments` | `terminate` |
| UC52 | Nhắc hợp đồng sắp hết hạn | Hệ thống / Staff | | `ContractExpiryService.refreshStatuses`: ACTIVE/EXPIRING_SOON có `end_date < hôm nay` -> EXPIRED; ACTIVE có `end_date` trong 30 ngày -> EXPIRING_SOON; ghi `contract_notifications` + gửi thông báo cho khách và LANDLORD/MANAGER. Gọi bằng `POST /api/contracts/refresh-status` hoặc `VietJobs` lúc 00:30 nếu `minihouse.scheduler.enabled=true`; `GET /api/contracts/expiring?days=` | | | `rental_contracts.status`, `contract_notifications`, `user_notifications` | `ContractExpiryService`, `EquipmentController:110,117`, `VietJobs.java:30` |

## 3.3 Chỉ số điện nước, đơn giá (phần của Toàn)

[CODE] Các controller `P/toan/controller/*` không có `@PreAuthorize` (grep 0 kết quả trong `toan/`). Khi bảo mật bật, mọi vai trò nhân viên (kể cả `CASHIER`, `METER_READER`) gọi được các API này.

| Mã | Tên | Tác nhân | Các bước chính và luồng lỗi | Dữ liệu thay đổi | Bằng chứng |
|---|---|---|---|---|---|
| UC53 | Tạo kỳ ghi chỉ số | Staff | `POST /api/meter-periods` (khu, tên kỳ, ngày bắt đầu/kết thúc). Ngày kết thúc trước ngày bắt đầu: 400. Trùng tên kỳ trong khu: 409. `PATCH /api/meter-periods/{id}/closed?value=` đóng/mở kỳ thủ công | `utility_reading_periods` | `MeterReadingService.createPeriod`, `closePeriod` |
| UC54-55 | Nhập chỉ số, ảnh công tơ | Staff | `POST /api/meter-readings`: kỳ đã đóng 409; phòng ngoài khu 400; đã có chỉ số trong kỳ 409; chỉ số mới < chỉ số cũ 400. Chỉ số cũ lấy từ request, nếu thiếu lấy chỉ số mới của kỳ trước, nếu không có thì lấy `rooms.initial_electric_meter/initial_water_meter`. Mới nhập `is_verified=false`. `POST /api/meter-readings/{id}/photos` chỉ lưu URL (không upload file) | `meter_readings`, `meter_photos` | `recordReading`, `resolveOldValues`, `addPhoto` |
| UC56 | Xác nhận, điều chỉnh chỉ số | Staff | `POST /{id}/confirm` đặt `is_verified=true` (KHÔNG kiểm tra kỳ đã đóng); `PUT /{id}/adjust` bắt buộc lý do, chỉ sửa được khi kỳ chưa đóng, ghi `meter_adjustment_logs`, tự xác nhận | `meter_readings`, `meter_adjustment_logs` | `confirmReading`, `adjustReading` |
| UC57 | Cảnh báo tăng đột biến | Hệ thống | khi nhập hoặc điều chỉnh, so sánh mức tiêu thụ với kỳ trước: tăng >= 50% (và kỳ trước dùng >= 1) thì ghi `meter_anomaly_alerts` (phần trăm tối đa 999,99). `GET /api/meter-alerts`, `POST .../resolve`. Job `BillingJobs` đóng kỳ quá hạn 7 ngày | `meter_anomaly_alerts` | `detectAnomalies`, `checkUsage`, `BillingJobs.closeExpiredMeterPeriods` |
| UC58-59 | Lịch sử và so sánh tiêu thụ | Staff | `GET /api/rooms/{id}/consumption`, `GET /api/properties/{id}/consumption-comparison` | đọc | `roomHistory`, `propertyComparison` |
| UC60 | Chỉ số lúc vào/ra | Staff | `POST /api/meter-readings/move-in` GHI ĐÈ `rooms.initial_electric_meter/initial_water_meter`; `POST .../move-out` = nhập một chỉ số vào kỳ chọn | `rooms`, `meter_readings` | `recordMoveIn`, `recordMoveOut` |
| UC61, UC64 | Đơn giá điện nước, lịch sử | Staff | `POST/PUT /api/service-rate-plans` (giá điện, giá nước, ngày áp dụng); tạo bảng giá mới thì đóng bảng giá đang mở (`end = start mới - 1 ngày`); giá mới phải có ngày bắt đầu sau ngày bắt đầu bảng giá đang mở (409 nếu không) | `service_rate_plans`, `rate_change_histories` | `ServiceRateService` |
| UC62-63 | Phí dịch vụ, phụ thu/giảm trừ | Staff | `POST/PUT/DELETE /api/property-service-rates` (loại tính: `FIXED_ROOM`, `PER_PERSON`, `PER_VEHICLE`, `PER_METER_UNIT`), `/api/room-custom-rates`; xóa là xóa cứng (`deleteById`) | `property_service_rates`, `room_custom_rates` | `ServiceRateService.java:133,168` |

## 3.4 Tính tiền tháng (Phi)

| Mã | Tên | Tác nhân | Các bước và luồng lỗi | Dữ liệu thay đổi | Bằng chứng |
|---|---|---|---|---|---|
| UC66 | Tạo kỳ tính tiền | LANDLORD, MANAGER | `POST /api/billing/periods` (khu, `YYYY-MM`) -> trạng thái `DRAFT`; trùng khu và tháng: 409; trả danh sách hợp đồng đủ điều kiện | `monthly_billing_periods` | `BillingService.createPeriod` |
| UC67 | Kiểm tra dữ liệu đầu vào | như trên | `GET /periods/{id}/validate` báo: chưa có đơn giá hiệu lực, chưa có kỳ ghi chỉ số, phòng thiếu chỉ số, chỉ số chưa xác nhận | đọc | `validate` |
| UC68-73 | Tính tiền | như trên | `POST /periods/{id}/calculate` (chỉ khi `DRAFT`, 409 nếu khác): xóa bảng tính cũ của kỳ rồi dựng lại cho từng hợp đồng; giữ lại khoản nhập tay (tiền tố `✎ `). Công thức ở Phần 6 | `calculated_monthly_fees`, `fee_item_details` | `BillingService.calculate` |
| UC74 | Điều chỉnh khoản phí | như trên | `PUT /fee-items/{id}`, `POST /fees/{id}/items`, `DELETE /fee-items/{id}`: chỉ khi kỳ `DRAFT`; tổng được tính lại từ các dòng | `fee_item_details`, `calculated_monthly_fees` | `updateItem`, `addItem`, `deleteItem`, `recompute` |
| UC75 | Kiểm tra bất thường, duyệt | như trên | `GET /periods/{id}/anomalies`: tổng > 1,5 lần trung bình các kỳ khác của phòng; `POST /periods/{id}/approve`: kỳ phải có bảng tính, chuyển `APPROVED` và đóng kỳ ghi chỉ số cùng tháng (`is_closed=true`) | `monthly_billing_periods.status`, `utility_reading_periods.is_closed` | `anomalies`, `approve` |

## 3.5 Hóa đơn và thanh toán

| Mã | Tên | Tác nhân | Các bước và luồng lỗi | Dữ liệu thay đổi | Bằng chứng |
|---|---|---|---|---|---|
| UC77, UC79 | Phát hành hóa đơn | Staff (toàn bộ vai trò nhân viên khi bảo mật bật) | `POST /api/invoices` (một phòng) hoặc `/batch` (cả kỳ, phòng nào chưa có hóa đơn): kỳ `DRAFT` thì 409; đã có hóa đơn 409; hạn trước ngày phát hành 400; hạn mặc định = `payment_due_day` của hợp đồng (mặc định 5) trong tháng phát hành, nếu đã qua thì sang tháng sau. Tạo thông báo `SYSTEM_APP`, tự cấn trừ tiền dư (`autoDeduct`). Mã `INV-yyyyMM-<mã phòng>-<id>` | `invoices`, `invoice_notifications`, `tenant_prepayments` | `InvoiceService.issueOne` |
| UC78, UC84 | Xem, in, tra cứu | Staff | `GET /api/invoices` (lọc), `/{id}`, `/{id}/detail`, `/{id}/print` (HTML để in), `/payable`, `/summary`, `/summary/{monthYear}` | đọc | `InvoiceService`, `InvoiceController` |
| UC80-81 | Gửi hóa đơn | Staff | `POST /{id}/send-email`: khách phải có email, nhưng `InvoiceMailGateway` KHÔNG có lớp cài đặt nào nên trạng thái ghi là `PENDING`. `POST /{id}/send-app` tạo thông báo `SYSTEM_APP` và link `/api/invoices/share/{uuid}` | `invoice_notifications` | `sendEmail`, `dispatchEmail`, `sendToApp` |
| UC82 | Nhắc trước hạn, thông báo quá hạn | Hệ thống | `BillingJobs` 08:00 mỗi ngày: nhắc hóa đơn còn 3 ngày và 1 ngày; hóa đơn quá hạn chuyển `OVERDUE` và thông báo tối đa 1 lần mỗi 7 ngày | `invoices.payment_status`, `invoice_notifications` | `InvoiceService.sendDueReminders`, `markOverdueAndNotify`, `BillingJobs` |
| UC83 | Điều chỉnh tổng hóa đơn | Staff | `PUT /{id}/total`: bắt buộc lý do, tổng >= 0, không điều chỉnh hóa đơn `CANCELLED`; KHÔNG chặn hóa đơn đã `PAID`; tính lại trạng thái; tạo thông báo và email | `invoices` | `adjustTotal` |
| UC85-88 | Thanh toán | LANDLORD, MANAGER, CASHIER | `POST /api/payments` (tiền mặt hoặc chuyển khoản, ghi `SUCCESS` và cộng ngay vào hóa đơn); `POST /api/payments/qr` (tạo giao dịch chờ, trạng thái `FAILED`, trả tài khoản ngân hàng mặc định + nội dung = mã hóa đơn); `POST /api/payments/{id}/confirm` (nhân viên nhập số tiền thực nhận); `POST /api/payments/callback`; `POST /api/payments/match` (tìm hóa đơn có mã trong nội dung chuyển khoản); `GET /api/payments`; người thuê tạo QR bằng `POST /api/me/payments/qr` | `payment_transactions`, `invoices.paid_amount/payment_status`, `invoice_notifications` | `PaymentService`, `CashierPaymentController` |
| UC89-90 | Phiếu thu, biên nhận | Staff | `POST /api/receipts`, `GET /api/receipts`, `/{id}/print`, `/{id}/send-confirmation` | `receipt_vouchers` | `ReceiptService` |
| UC91 | Thanh toán một phần | Staff | `POST /api/invoices/{id}/partial-payments` cộng `paid_amount` và ghi `partial_payment_logs` (KHÔNG tạo `payment_transactions`); `PUT /api/partial-payments/{id}` sửa số tiền | `partial_payment_logs`, `invoices` | `PartialPaymentService` |
| UC92 | Tiền dư, cấn trừ, hoàn, đảo giao dịch | Staff | `POST /api/invoices/{id}/overpayment/to-balance`, `POST /api/prepayments/deduct`, `DELETE /api/prepayments/deductions/{id}`, `POST /api/prepayments/refund`, `POST /api/payment-transactions/{id}/reverse` | `tenant_prepayments`, `prepayment_deductions`, `payment_transactions.status=REVERSED`, `invoices` | `PrepaymentService` |

## 3.6 Công nợ, trả phòng, sự cố, thông báo, chi phí (Việt) và báo cáo (Phi)

| Mã | Tên | Tác nhân | Các bước và luồng lỗi | Dữ liệu thay đổi | Bằng chứng |
|---|---|---|---|---|---|
| UC93-98 | Công nợ | LANDLORD, MANAGER, CASHIER (đọc); LANDLORD, MANAGER (ghi); người thuê xem của mình | `POST /api/debts/recalculate` (hoặc job 07:30): gom hóa đơn chưa trả theo cặp (khách, phòng) thành một dòng `tenant_debts`; tổng nợ, nợ kỳ này = hóa đơn mới nhất, nợ kỳ trước = tổng - kỳ này; số ngày quá hạn lớn nhất; trạng thái `NORMAL`, `WARNING_OVERDUE`, `LONG_TERM_DEBT` (>= 30 ngày), `CLEARED`; ghi `debt_penalty_logs` khi số ngày quá hạn đổi và hợp đồng có phí trễ hạn > 0. `GET /api/debts`, `/summary`, `/export`, `/{id}`; `GET /api/me/debts`, `/api/me/debts/invoices` | `tenant_debts`, `debt_penalty_logs` | `DebtService.recalculate` |
| UC99 | Nhắc nợ tự động | LANDLORD, MANAGER / job 08:30 | `POST /api/debts/reminders/run`: mức 1 (quá hạn 1-6 ngày), 2 (>= 7), cuối (>= 15); bỏ qua nếu khách hẹn ngày trả chưa tới; không gửi trùng mức trong cùng đợt quá hạn | `debt_reminders`, `user_notifications` | `DebtService.sendReminders` |
| UC100 | Cam kết thanh toán, xử lý công nợ | LANDLORD, MANAGER | `POST /api/debts/{id}/processing-logs` | `debt_processing_logs` | `DebtController` |
| UC101-107 | Trả phòng | LANDLORD, MANAGER; người thuê gửi yêu cầu | `POST /api/checkout-requests` (người thuê: `/api/me/checkout-requests`) `PENDING` -> `PATCH .../confirm` `INSPECTING` -> `PUT .../inspection` (chỉ số cuối, hư hỏng, bồi thường) -> `GET .../settlement` -> `POST .../refund-voucher` (`PENDING`) -> `POST /api/refund-vouchers/{id}/pay` (`REFUNDED`, gạch nợ hóa đơn bằng tiền cọc) -> `POST .../finalize` (`FINALIZED`, hợp đồng `TERMINATED`, phòng `VACANT`). `PATCH .../cancel`, `GET .../handover-report` | `checkout_requests`, `checkout_inspections`, `deposit_refund_vouchers`, `invoices`, `rental_contracts`, `rooms`, `tenant_debts` | `CheckoutService` |
| UC108-109 | Yêu cầu thiết bị, phí tiện ích bổ sung | LANDLORD, MANAGER; người thuê gửi | `/api/equipment-requests` (review, install tạo `room_addon_fees`, remove tắt phụ phí), `/api/room-addon-fees`, `/api/me/equipment-requests` | `room_equipment_requests`, `room_addon_fees` | `EquipmentService`, `EquipmentController` |
| UC110-115 | Sự cố, sửa chữa | LANDLORD, MANAGER; người thuê gửi và đánh giá | `OPEN -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED`; điều chỉnh giảm hóa đơn sau sự cố (`POST /{id}/invoice-adjustment`); người thuê đánh giá 1-5 sao một lần; danh mục nhóm sự cố `/api/incident-categories` | `maintenance_requests`, `maintenance_logs`, `invoices`, `incident_categories` | `MaintenanceService`, `EquipmentController` |
| UC116 | Dashboard | LANDLORD, MANAGER | `GET /api/dashboard?propertyId`: số phòng, tỷ lệ lấp đầy, doanh thu tháng, đã xuất hóa đơn, chi phí, công nợ, số hóa đơn quá hạn, hợp đồng sắp hết hạn | đọc | `ReportService.dashboard` |
| UC117 | Chi phí vận hành | LANDLORD, MANAGER | `/api/expenses` CRUD (xóa cứng), `/summary`, `/categories` | `operating_expenses` | `OperatingExpenseService`, `ExpenseController` |
| UC118 | Báo cáo doanh thu - chi phí | LANDLORD, MANAGER | `GET /api/reports/revenue?year`, `/export?format=xlsx|pdf` | đọc | `ReportService.revenueReport` |
| UC119 | Tài khoản ngân hàng, QR | LANDLORD, MANAGER (ghi) | `/api/properties/{id}/bank-accounts`, `/api/bank-accounts/{id}` (sửa, đặt mặc định, xóa, tải ảnh QR) | `bank_accounts`, file QR | `BankAccountService` |
| UC120 | Lịch tự động | LANDLORD | `GET /api/admin/jobs`, `POST /api/admin/jobs/{name}/run`; chỉ job `PurgeExpiredVerificationsJob` (xóa mã OTP cũ > 1 ngày) đăng ký vào framework chung | | `JobScheduler`, `JobController`, `PurgeExpiredVerificationsJob` |
| UC121 | Mẫu thông báo | LANDLORD, MANAGER | `/api/notification-templates` | `notification_templates` | `NotificationTemplateService` |
| UC122 | Sao lưu, phục hồi | LANDLORD | `GET/POST /api/admin/backups`, `POST /api/admin/backups/{name}/restore` | file `.bak` | `BackupService` |
| UC123 | Trung tâm thông báo | mọi người đã đăng nhập (đường dẫn `/api/notifications` cho nhân viên, `/api/me/notifications` cho người thuê) | xem, đánh dấu đã đọc, xóa | `user_notifications` | `NotificationCenterService`, `NotificationController` |
| UC (phụ) | Xe và người ở cùng | Staff | `/api/tenants/{id}/vehicles`, `/api/vehicles/{id}`, `/api/rooms/{id}/residents`, `/api/residents/{id}`; xóa là ngừng (`is_active=false`); số người tối đa = `rooms.max_occupants` (tính cả người đứng tên hợp đồng) | `tenant_vehicles`, `room_residents` | `OccupancyService` |

Các UC thuộc phần của Chi (UC14-40) và UC124-132 KHÔNG có code backend. Xem Phần 15.

---

# Phụ lục. Tự kiểm tra (Bước 3)

Mở lại code để kiểm 10 nhận định trong tài liệu. Ba nhận định đầu thuộc nhóm "điểm chủ dự án nghi ngờ".

| # | Nhận định | Kết quả | Bằng chứng khi kiểm lại |
|---|---|---|---|
| 1 | Callback trùng cho giao dịch đã `SUCCESS` trả 409 | ĐÚNG | `PaymentService.confirmWith`: `!"FAILED".equals(t.getStatus())` ném 409 |
| 2 | Người thuê không tạo được QR cho hóa đơn của người khác | ĐÚNG | `PaymentService.createQrForTenant`: so `tenantId`, sai thì `notFound` |
| 3 | `BillingJobs` bật mặc định, `VietJobs` tắt mặc định | ĐÚNG | `BillingJobs.java:24` `matchIfMissing = true`; `VietJobs.java:17` không có `matchIfMissing` |
| 4 | Không có endpoint hủy hóa đơn | ĐÚNG | grep `cancel` trong `toan/controller` và `InvoiceService` chỉ thấy phép so sánh `CANCELLED` ở `adjustTotal:259`; không có `setPaymentStatus("CANCELLED")` ở đâu |
| 5 | `InvoiceMailGateway` không có lớp cài đặt | ĐÚNG | grep toàn `src/main` chỉ thấy interface và nơi `InvoiceService` dùng nó |
| 6 | `BillingService` không đọc phụ phí thiết bị `RoomAddonFee` | ĐÚNG | `grep -c RoomAddonFee BillingService.java` = 0 |
| 7 | Người thuê không đổi được SĐT trong hồ sơ | ĐÚNG | `AccountService.updateProfile:86` `roles.contains("TENANT")` |
| 8 | `PATCH /meter-periods/{id}/closed` đặt thẳng giá trị theo tham số `value` | ĐÚNG | `MeterReadingController:34-38` |
| 9 | Chỉ có `phi` gọi `ActivityLogService` | ĐÚNG | grep: 15 file, tất cả trong `phi/` |
| 10 | Có 56 bảng và 56 entity, tên cột khớp | ĐÚNG | script so sánh: `only SQL []`, `only entity []`, `column diffs: 0` |

Sai sót tìm thấy khi kiểm lại và đã sửa trong tài liệu:

- Số dòng sai: `CashierPaymentController` callback (97 -> 99), `MeterReadingService.recordReading` kiểm kỳ đóng (121 -> 115), `OtpService` (60 -> 45), `StaffService` (187/197/199 -> 188/196/200).
- Gọi nhầm tên bảng yêu cầu thuê: là `booking_requests`, không phải `rental_requests`.
- Thiếu: SĐT khi đăng ký chủ trọ cũng được kiểm 9-15 chữ số (`RegistrationService:65`).
- Cột `Null?` của khóa chính trong Phần 4 ban đầu ghi `NULL`; đã sửa thành `NOT NULL`.
- Mô tả `BackupServiceTest`: ban đầu ghi "dùng đối tượng giả" như sự thật; đã hạ xuống [SUY RA] vì chưa đọc test.
