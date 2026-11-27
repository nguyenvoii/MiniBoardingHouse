# Các API đã có (phần của Phi)

Tài liệu các API do Phi làm. API của Chi (`/api/chi/**`), Toàn và Việt xem trong Swagger (`http://localhost:8080/swagger-ui.html`). Mọi API đều xem được trong Swagger.

## 1. Tài khoản và phân quyền (UC1-13)

| API | Ai gọi được | Mô tả |
|---|---|---|
| `POST /api/auth/login` | Công khai | Đăng nhập bằng email hoặc SĐT, trả JWT |
| `POST /api/auth/logout` | Đã đăng nhập | Ghi nhật ký đăng xuất (client tự bỏ token) |
| `GET /api/auth/me` | Đã đăng nhập | Thông tin người đang đăng nhập |
| `POST /api/auth/register` | Công khai | UC1: đăng ký chủ trọ (xem mục 2) |
| `POST /api/auth/verify-email`, `/resend-verification` | Công khai | UC2: xác thực email bằng OTP |
| `POST /api/auth/forgot-password`, `/verify-reset-code`, `/reset-password` | Công khai | UC4: quên mật khẩu (3 bước: xin mã, nhập mã, đặt mật khẩu mới) |
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

## 2. Tính năng dùng chung (UC1, 2, 4, 7, 12, 119, 120, 122 và khung xuất file)

**Đăng ký, xác thực email, quên mật khẩu (UC1, 2, 4).** Mã OTP 6 số, hiệu lực 10 phút, dùng một lần, xin lại cách nhau 60 giây, đoán sai một lần là mã bị hủy.
- `POST /api/auth/register` (họ tên, email, mật khẩu, SĐT tùy chọn): tạo tài khoản chủ trọ ở trạng thái `INACTIVE` và gửi mã. `POST /api/auth/verify-email` (email, code): nhập đúng mã thì thành `ACTIVE`. Đăng ký lại bằng email đang chờ xác thực sẽ ghi đè tên và mật khẩu, để người chiếm email trước không giữ được mật khẩu.
- `POST /api/auth/forgot-password` (email hoặc SĐT): luôn trả cùng một thông báo dù tài khoản có tồn tại hay không. `POST /api/auth/verify-reset-code` (identifier, code): chỉ kiểm tra mã, chưa dùng hết mã, để giao diện cho sang bước nhập mật khẩu mới. `POST /api/auth/reset-password` (identifier, code, newPassword) kiểm tra lại mã rồi mới đổi mật khẩu. Mỗi mã được nhập sai tối đa 5 lần (đếm trong bộ nhớ app), sai lần thứ 5 thì mã bị hủy; xin mã mới thì mã cũ bị hủy và phải cách nhau 60 giây.
- Gửi mã đi qua `shared/notification/NotificationSender`. Bản mặc định `LogNotificationSender` chỉ ghi nội dung ra log (tìm dòng `THONG BAO GIA LAP`). Gửi thật qua email: đặt `app.mail.enabled=true`, `spring.mail.*` và `app.mail.from` (ví dụ Brevo `smtp-relay.brevo.com:587`); thư OTP dựng ở `OtpMail` (HTML + chữ thường, tên người gửi Stayly).
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

## 3. Hợp đồng thuê phòng (UC41-51)

Nhân viên (LANDLORD, MANAGER) quản lý; người thuê xem và ký hợp đồng của mình qua `/api/me/contracts`. Mọi thay đổi quan trọng ghi vào `contract_adjustments` (xem được ở chi tiết hợp đồng). Phòng và khách thuê hiện đọc qua `EntityManager` (chưa chuyển sang `RoomService`, `TenantService` của Chi).

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

## 4. Tính tiền hàng tháng (UC65-75)

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

## 5. Xe và người ở cùng

Nguồn số lượng thật cho dịch vụ gửi xe và dịch vụ theo đầu người. Xem: mọi nhân viên; thêm, sửa, xóa: LANDLORD, MANAGER. Xóa chỉ là ngừng dùng (giữ lịch sử).

| API | Mô tả |
|---|---|
| `GET/POST /api/tenants/{tenantId}/vehicles` | Danh sách xe đang hoạt động, đăng ký xe `{licensePlate, vehicleType, brand, color}` (biển số không trùng; xe đã bỏ đăng ký lại được) |
| `PUT/DELETE /api/vehicles/{id}` | Sửa xe, bỏ xe |
| `GET/POST /api/rooms/{roomId}/residents` | Danh sách người ở cùng, thêm người ở cùng (không vượt số người tối đa của phòng, tính cả người đứng tên) |
| `PUT/DELETE /api/residents/{id}` | Sửa, cho rời phòng |

## 6. Thanh toán hóa đơn (UC85-88)

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

## 7. Dashboard và báo cáo (UC116, UC118)

LANDLORD, MANAGER. `propertyId` bỏ trống là gộp mọi khu trọ.

| API | Mô tả |
|---|---|
| `GET /api/dashboard?propertyId=` | Số phòng (đang thuê, trống), tỷ lệ lấp đầy, doanh thu/đã lập hóa đơn/chi phí của tháng này, tổng công nợ, số hóa đơn chưa trả và quá hạn, hợp đồng sắp hết hạn |
| `GET /api/reports/revenue?propertyId=&year=` | Doanh thu, chi phí, lợi nhuận 12 tháng. Doanh thu = các giao dịch SUCCESS theo ngày thanh toán |
| `GET /api/reports/revenue/export?propertyId=&year=&format=xlsx\|pdf` | Xuất báo cáo ra Excel hoặc PDF |
