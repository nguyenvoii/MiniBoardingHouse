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
| `APP_SECURITY_ENABLED=true` | Bắt buộc đăng nhập JWT. Mặc định trong code là `false` (mở toang), nên **không bao giờ để false trên server** |
| `APP_NOTIFICATION_LOG_OTP=false` | Không in OTP và mật khẩu tạm ra log |
| `APP_UPLOAD_DIR=/data/uploads` | Nơi lưu file upload, quản trị gắn ổ đĩa bền vào đường dẫn này |
| `SERVER_FORWARD_HEADERS_STRATEGY=framework` | Để Spring hiểu mình đứng sau proxy HTTPS |
| `SPRINGDOC_*=false` | Tắt Swagger trên server (chạy local vẫn dùng bình thường) |

### Workflow làm gì

File `deploy.yml` chạy mỗi khi push lên `main` (hoặc bấm tay ở tab Actions):

1. `checkout`: lấy code.
2. Đặt tên image viết thường (GHCR bắt buộc).
3. `login-action`: đăng nhập GHCR bằng `GITHUB_TOKEN`, token có sẵn của mỗi lần chạy, em **không cần tạo PAT** để đẩy image.
4. `build-push-action`: build image và đẩy lên GHCR với hai tag, `latest` và `sha-<mã commit>` (tag theo commit để sau này quay lại bản cũ được).
5. `deploy-from-github`: gọi CapRover bằng 3 secret để kéo `:latest` về và chạy.

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
- Đăng nhập thử, rồi **đổi ngay mật khẩu tài khoản admin mặc định** (mật khẩu mặc định đang công khai trong repo).

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
