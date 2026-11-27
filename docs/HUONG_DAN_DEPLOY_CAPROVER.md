# Hướng dẫn deploy MiniBoardingHouse lên CapRover bằng GitHub Actions

Tài liệu này không chứa bí mật nào, commit vào repo được.

## 1. Bức tranh tổng thể

```
 em push lên main
        │
        ▼
 GitHub Actions (máy ảo của GitHub)
   1. build giao diện (Vite) + backend (Maven) trong Docker
   2. đẩy image lên GHCR:  ghcr.io/nguyenvoii/miniboardinghouse:latest
   3. gọi CapRover: "kéo image mới về và chạy"
        │
        ▼
 CapRover (server của quản trị)
   app Spring Boot  ──(mạng nội bộ)──►  SQL Server
```

Em chỉ làm việc ở phía GitHub. Mọi thứ trên server do quản trị CapRover lo.

## 2. Ai làm gì

| Việc | Em | Quản trị CapRover |
|---|---|---|
| Viết code, Dockerfile, workflow | ✅ | |
| Khai báo 3 GitHub Secrets | ✅ | |
| Đặt gói GHCR thành Public | ✅ | |
| Tạo app CapRover, bật App Token, cổng 8080, thư mục lưu file | | ✅ |
| Đặt mật khẩu DB, JWT secret, kết nối DB | | ✅ |
| Nạp / cập nhật schema database | | ✅ |

**Em sẽ không được biết mật khẩu database.** Cố ý như vậy: pipeline không cần nó, và code học tập không nên giữ khóa thật.

## 3. Em nhận được gì

Quản trị sẽ đưa em:

| Thông tin | Dùng làm gì |
|---|---|
| Địa chỉ CapRover (dạng `https://captain.ten-mien.vn`) | Secret `CAPROVER_SERVER` |
| Tên app CapRover | Secret `APP_NAME` |
| Deploy token (App Token) | Secret `APP_TOKEN` |
| Tên database SQL Server | Để em biết mình đang làm việc với DB nào |

Token chỉ có quyền deploy vào **đúng một app** đó. Dù vậy, hãy coi nó như mật khẩu: không dán vào code, không commit, không gửi vào nhóm chat. Lỡ lộ thì báo quản trị tạo lại ngay.

## 4. Bước 1: thêm 3 file vào repo

Bộ file đi kèm tài liệu này, đặt ở gốc repo:

```
MiniBoardingHouse/
├── Dockerfile
├── .dockerignore
└── .github/workflows/deploy.yml
```

### Dockerfile làm gì

Ba tầng, mỗi tầng một việc:

1. **Tầng `fe`**: chạy `npm run build` trong `frontend/`. Kết quả nằm trong `src/main/resources/static`, và thư mục này **bị `.gitignore`** nên trên GitHub không có sẵn, bắt buộc build lại trong Docker.
2. **Tầng `be`**: lấy kết quả tầng 1 rồi chạy `mvn package`, ra một file JAR chứa cả backend lẫn giao diện.
3. **Tầng cuối**: chỉ có JRE 17 + JAR. Image nhỏ, không chứa Maven hay Node. Có cài thêm font DejaVu để xuất PDF không mất dấu tiếng Việt.

Khối `ENV` ở cuối là cấu hình **không bí mật**:

| Biến | Ý nghĩa |
|---|---|
| `APP_SECURITY_ENABLED=true` | Bật xác thực JWT và phân quyền. Ứng dụng yêu cầu `APP_JWT_SECRET` riêng, dài ít nhất 32 ký tự |
| `APP_NOTIFICATION_LOG_OTP=false` | Không in OTP và mật khẩu tạm ra log |
| `APP_UPLOAD_DIR=/data/uploads` | Gắn ổ lưu bền và cho UID/GID `10001:10001` đọc ghi dữ liệu upload |
| `SERVER_FORWARD_HEADERS_STRATEGY=framework` | Để Spring hiểu mình đứng sau proxy HTTPS |
| `SPRINGDOC_*=false` | Tắt Swagger trên server (chạy local vẫn dùng bình thường) |

### Workflow làm gì

Pull request vào `main` chạy build frontend, kiểm thử Java và kiểm thử SQL Server trong cơ sở dữ liệu riêng cho CI. Push lên `main` sẽ deploy sau khi các bước kiểm thử thành công. Khi chạy thủ công từ nhánh `main`, chọn `deploy` nếu muốn triển khai.

1. `checkout`: lấy code.
2. Đặt tên image viết thường (GHCR bắt buộc).
3. `login-action`: đăng nhập GHCR bằng `GITHUB_TOKEN`, token có sẵn của mỗi lần chạy, em **không cần tạo PAT** để đẩy image.
4. `build-push-action`: build image và đẩy lên GHCR với hai tag, `latest` và `sha-<mã commit>` (tag theo commit để sau này quay lại bản cũ được).
5. `deploy-from-github` gọi CapRover bằng 3 secret để kéo đúng image theo tag `sha-<mã commit>`.

## 5. Bước 2: khai báo 3 secret

Repo trên GitHub → **Settings → Secrets and variables → Actions → New repository secret**:

| Name (gõ đúng từng chữ) | Value |
|---|---|
| `CAPROVER_SERVER` | địa chỉ CapRover, **không có dấu `/` ở cuối** |
| `APP_NAME` | tên app |
| `APP_TOKEN` | deploy token |

Tên secret sai một chữ là workflow chạy với giá trị rỗng và báo lỗi khó hiểu.

## 6. Bước 3: push lần đầu và đặt gói thành Public

1. Commit 3 file, push lên `main`.
2. Mở tab **Actions** của repo, theo dõi lần chạy.
3. Lần đầu, hai bước build/push sẽ xanh, nhưng bước **Deploy to CapRover có thể đỏ** vì CapRover chưa có quyền kéo image (gói mới tạo mặc định là private).
4. Sửa: GitHub → ảnh đại diện → **Your profile → Packages → `miniboardinghouse` → Package settings → Change visibility → Public**.
5. Quay lại Actions, bấm **Re-run failed jobs**.

Vì sao Public được: repo của em đã public, và image chỉ chứa code đã có trong repo. Mật khẩu DB và JWT secret nằm trên server, không nằm trong image. Chính vì vậy **tuyệt đối không bao giờ ghi bí mật vào Dockerfile hay code** — nó sẽ nằm trong một image công khai.

## 7. Bước 4: kiểm tra

- Mở `https://<tên-app>.<tên-miền-caprover>/api/ping` → thấy `{"success":true,"message":"OK","data":"pong"}` là backend sống.
- Mở trang gốc `/` → giao diện Stayly.
- Kiểm tra đăng nhập bằng tài khoản quản trị đã được cấp riêng theo quy trình bên dưới.

## Trước khi triển khai bản vá bảo mật

1. Quản trị CapRover giới hạn truy cập công khai trong lúc xử lý K1. Xác nhận dữ liệu nào là dữ liệu thử và tạo bản sao lưu do hạ tầng quản lý. Kiểm tra bản sao ngoài container có thể khôi phục được.
2. Nếu giữ cơ sở dữ liệu hiện tại, quản trị khóa các tài khoản thử đang dùng băm mật khẩu seed, đặt lại bằng mật khẩu riêng cho từng tài khoản cần giữ, rồi chạy truy vấn K1 bên dưới. Nếu dựng lại, dùng quy trình cấp chủ trọ cho cơ sở dữ liệu sạch.
3. Với cơ sở dữ liệu đang chạy, áp dụng `db/patch_19_revoked_sessions.sql`, `db/patch_20_invoice_share_expiration.sql` và `db/patch_21_business_time_defaults.sql` theo thứ tự trước khi đưa bản mới lên `main`. SQL Server lưu ngày giờ mới theo giờ Việt Nam từ UTC. Dữ liệu cũ có thể trộn nhiều nguồn giờ nên cần xác minh nguồn trước khi hiệu chỉnh.
4. Đổi `APP_JWT_SECRET` khi xử lý sự cố để kết thúc các phiên đã cấp. Đặt quyền đọc ghi ổ upload cho UID/GID ở trên. Sau khi deploy, người dùng cần đăng nhập lại và tạo mới những liên kết chia sẻ hóa đơn cũ. Liên kết mới có hạn bảy ngày.
5. Giữ `APP_BACKUP_ENABLED=false` và quyền SQL của ứng dụng ở mức đọc/ghi dữ liệu. Quản trị lên lịch sao lưu qua hạ tầng, lưu bản sao ngoài container và kiểm thử phục hồi trên môi trường riêng. Nếu bật UC122 cho môi trường QA, nhật ký ứng dụng ghi người yêu cầu và người hoàn tất phục hồi.

### Xác nhận K1 trên cơ sở dữ liệu đang chạy

Chạy truy vấn trong cơ sở dữ liệu Stayly. Kết quả chỉ gồm số lượng tài khoản. Trước khi mở lại truy cập, cột tài khoản hoạt động phải bằng 0. K1 hoàn tất khi cả hai cột bằng 0.

```sql
SELECT COUNT_BIG(*) AS accounts_using_demo_hash,
       COUNT_BIG(CASE WHEN [status] = N'ACTIVE' THEN 1 END) AS active_accounts_using_demo_hash
FROM dbo.[users]
WHERE [password_hash] COLLATE Latin1_General_100_BIN2 =
    N'$2a$10$Pc6uubpMhGt6uuP/TqMCyulqTZc2GQVvfgNqWPYx7ciLqNtJzs3uy';
```

### Cấp chủ trọ cho cơ sở dữ liệu sạch

1. Quản trị chạy `db/group-7.sql`. Dùng một phiên SSMS để chạy `EXEC sys.sp_set_session_context N'stayly_skip_demo_owner', 1;` trước `db/seed.sql`. Kết quả có vai trò và danh mục nền. Bộ nạp mẫu `db/minihouse-full.sql` dành cho QA.
2. Trong phiên đã chọn đúng cơ sở dữ liệu, đặt các giá trị dưới đây qua `sys.sp_set_session_context`. Tạo băm BCrypt từ mật khẩu mới bằng công cụ nội bộ và truyền giá trị trong phiên quản trị.

   | Khóa phiên SQL | Giá trị |
   |---|---|
   | `stayly_owner_database` | Tên cơ sở dữ liệu Stayly đã chọn |
   | `stayly_owner_email` | Email đăng nhập riêng của chủ trọ |
   | `stayly_owner_name` | Tên chủ trọ |
   | `stayly_owner_bcrypt` | Băm BCrypt của mật khẩu mới |

3. Chạy `db/provision-owner.sql` trong cùng phiên. Script tạo tài khoản đầu tiên và gán vai trò `LANDLORD` trong một giao dịch. Chạy lại truy vấn K1 và xác nhận đăng nhập bằng tài khoản riêng trước khi mở truy cập.

### Kiểm thử trước khi đưa lên main

- `mvn -B test` chạy bộ kiểm thử nhanh. `mvn -B -Psql-integration verify` chạy thêm SQL Server 2022 qua Testcontainers. Nếu máy đã có SQL Server QA, đặt `STAYLY_SQL_TEST_USER`, `STAYLY_SQL_TEST_PASSWORD` và nếu cần `STAYLY_SQL_TEST_PORT`. Bộ kiểm thử tự tạo và dọn cơ sở dữ liệu riêng trên `127.0.0.1`.
- Các kịch bản `audit/e2e` đọc `E2E_ADMIN_IDENTIFIER`, `E2E_ADMIN_PASSWORD` và `BASE_URL`. Dùng tài khoản QA và kiểm tra proxy frontend cũng chuyển tiếp vào backend QA cục bộ.
- Kịch bản `restore-flow.mjs` cần `E2E_RESTORE_DB` trỏ tới cơ sở dữ liệu QA dùng riêng và `APP_BACKUP_ENABLED=true` trên backend QA đó.
- Chạy `mvn org.owasp:dependency-check-maven:check` để tạo báo cáo thành phần phụ thuộc trong `target/`. Trên GitHub Actions, có thể chọn `dependency_scan` và để ô `deploy` trống để nhận báo cáo trong artifact `test-reports`. Nếu repo có secret `NVD_API_KEY`, bước quét dùng key đó.

Quản trị sẽ cho em biết địa chỉ chính xác của app.

## 8. Khi em sửa cấu trúc database

Em không tự chạy SQL lên server được. Quy trình:

1. Viết file `db/patch_NN_ten.sql` theo đúng kiểu các patch hiện có: chạy lại nhiều lần vẫn an toàn (kiểm tra `IF NOT EXISTS`/`COL_LENGTH` trước khi sửa).
2. Test trên máy em.
3. Gửi file cho quản trị chạy **trước khi** push bản code cần nó. Vì `ddl-auto=validate`, code mới chạy với DB cũ sẽ không khởi động được.

## 9. Gặp lỗi thì xem ở đâu

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| Đỏ ở bước build, lỗi `tsc` | Lỗi TypeScript. Chạy `cd frontend && npm run build` trên máy, sửa rồi push |
| Đỏ ở bước build, lỗi Maven | Chạy `mvn -DskipTests package` trên máy để thấy lỗi |
| `denied` / `403` khi push image | Thiếu khối `permissions: packages: write` trong workflow |
| Đỏ ở Deploy, `401` / `token` | `APP_TOKEN` sai hoặc đã bị tạo lại |
| Đỏ ở Deploy, không kết nối được | `CAPROVER_SERVER` sai, có `/` cuối, hoặc thiếu `https://` |
| Đỏ ở Deploy, `unauthorized` khi kéo image | Gói GHCR chưa Public (bước 3.4) |
| Workflow xanh nhưng web `502` | App chưa lên: nhờ quản trị đọc **App Logs** |
| Log có `Login failed` / `Cannot open database` | Cấu hình kết nối DB trên server: việc của quản trị |
| Log có `Schema-validation: missing table/column` | DB chưa nạp schema hoặc lệch với entity: báo quản trị |

Muốn thử build Docker ngay trên máy trước khi push: `docker build -t mbh .` (cần Docker Desktop).

## 10. Quy tắc giữ an toàn

1. Không commit bí mật. File `application-local.properties` đã nằm trong `.gitignore`, hãy giữ nguyên.
2. Không sửa `APP_SECURITY_ENABLED` thành `false` trong Dockerfile.
3. Không đổi `ENV` rồi quên: mọi thứ trong Dockerfile là công khai.
4. Không xin mở cổng database ra internet. Cần xem dữ liệu thì nhờ quản trị.
5. Dự án này chạy trên hạ tầng dùng chung. Đừng cố truy cập các app khác trên server và báo ngay nếu thấy điều gì lạ.
