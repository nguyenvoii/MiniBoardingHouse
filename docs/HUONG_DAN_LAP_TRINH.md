# Hướng dẫn lập trình chung

Dành cho cả nhóm: cấu trúc code, quy ước Git và DB, cách viết API (đăng nhập, phân quyền, API người thuê, job, xuất file). Làm theo để code của bốn người ghép vào nhau không lỗi.

## 1. Cấu trúc code

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

## 2. Quy ước làm chung

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

## 3. Hướng dẫn viết API

### 3.1. Các class dùng chung (đã có trên `main`, pull về dùng)

| Class | Cách dùng |
|---|---|
| `shared/dto/ApiResponse` | Mọi API trả `ApiResponse.ok(data)` hoặc `ApiResponse.error(msg)` |
| `shared/dto/PageResponse` | Dùng cho API phân trang |
| `shared/exception/BusinessException` | Ném từ service khi vi phạm nghiệp vụ, ví dụ `throw BusinessException.notFound("Phòng")` |
| `shared/exception/GlobalExceptionHandler` | Tự đổi exception thành response chuẩn (sai phương thức, sai kiểu, thiếu tham số trả 405/415/400; trùng dữ liệu trả 409) |
| `shared/security/AuthUser` | Người đang đăng nhập, lấy trong controller bằng `@AuthenticationPrincipal AuthUser user` |
| `ActivityLogService.log(userId, "HANH_DONG", "mô tả", ip)` | Ghi nhật ký thao tác từ chức năng của mình |
| `shared/controller/PingController` | Controller mẫu, thử bằng `GET /api/ping` |

### 3.2. Bật bảo mật và phân quyền

- MẶC ĐỊNH đang MỞ (`app.security.enabled=false`): test API KHÔNG cần token, `@PreAuthorize` tạm bị bỏ qua.
- Bật `app.security.enabled=true` trong `application-local.properties` để bắt buộc đăng nhập (dùng khi demo/bảo vệ).
- Test khi đã bật: mở Swagger, gọi `POST /api/auth/login` với `admin@minihouse.local` / `Admin@123`, copy `accessToken`, bấm Authorize và dán token. Ngoài Swagger thì gửi header `Authorization: Bearer <token>`.
- Phân quyền theo vai trò (`LANDLORD`, `MANAGER`, `CASHIER`, `METER_READER`, `TENANT`): gắn `@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")` lên hàm controller.
- Khóa tài khoản và đổi vai trò có hiệu lực NGAY: mỗi request đều tra trạng thái và vai trò hiện tại trong DB, không lấy từ token.
- Khi viết test `@WebMvcTest` có `@Import(SecurityConfig.class)` phải thêm `@MockBean UserStatusChecker` (xem `StaffControllerSecurityTest` làm mẫu).
- Thiết kế chi tiết: `docs/thiet-ke/dang-nhap-jwt.md`.

### 3.3. API của người thuê (UC36, UC37, UC94...)

- Đường dẫn đặt dưới `/api/me/**`, ví dụ `/api/me/invoices`.
- Lấy mã khách thuê bằng `Integer tenantId = CurrentUser.requireTenantId(user)`, TUYỆT ĐỐI không nhận `tenantId` từ client (nếu không người này xem được dữ liệu người khác).
- Khi bật bảo mật: `/api/me/**` chỉ người thuê vào được, mọi đường dẫn khác chỉ nhân viên vào được (người thuê bị chặn mặc định, không cần gắn gì).
- Khi để mặc định (mở) thì không có token nên `user` là null: hãy test API người thuê bằng token người thuê (bật bảo mật), nếu không sẽ nhận 401.
- Cách tài khoản người thuê hoạt động: tài khoản là 1 dòng `users` có `phone_number` TRÙNG với `tenants.phone_number` của khách thuê; chủ trọ/quản lý cấp bằng `POST /api/tenants/{tenantId}/account` (trả mật khẩu tạm 1 lần); token đăng nhập có thêm `tenantId`.
- Phần của Phi hiện đọc khách thuê qua `TenantReadRepository` (package `phi`), chưa dùng `TenantService` của Chi. Nếu sau này đổi sang service đó, hoặc khi đổi số điện thoại khách thuê, phải cập nhật luôn tài khoản `users` tương ứng.
- Chi tiết: `docs/thiet-ke/tai-khoan-nguoi-thue.md`.

### 3.4. Thêm job tự động

Viết một class `@Component implements ScheduledJob` (tên, mô tả, cron mặc định, hàm `run()`). Khung `JobScheduler` tự tìm và lên lịch, không phải đăng ký ở đâu. Chi tiết ở `docs/API_PHI.md` mục 2.

### 3.5. Xuất Excel/PDF

Tạo `TableData(tiêuĐề, tênCột, cácDòng)` rồi gọi `TableExporter.toExcel(...)` hoặc `toPdf(...)`. Chi tiết và ví dụ ở `docs/API_PHI.md` mục 2.
