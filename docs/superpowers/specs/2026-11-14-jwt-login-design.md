# Thiet ke: Dang nhap JWT va phan quyen theo role (Phi)

Ngay: 2026-11-14. Nguoi duyet: Phi (da chot phuong an A trong hoi thoai).

## Muc dich
Dong nghiep (Chi, Toan, Viet) can gan duoc `@PreAuthorize` va test API co dang nhap ngay tu dau,
de khong phai quay lai sua tung controller sau nay.

## Muc tieu
- `POST /api/auth/login` (email hoac SDT + mat khau) tra ve JWT.
- `GET /api/auth/me` tra ve nguoi dang dang nhap.
- Moi API khac bat buoc dang nhap; phan quyen theo `role_code` (LANDLORD, MANAGER, CASHIER, METER_READER)
  bang `@PreAuthorize("hasRole('MANAGER')")`.
- Loi 401/403/400 deu tra ve `ApiResponse` dung dinh dang chung.
- Swagger co nut Authorize de dan token.

## Khong lam (non-goals)
Refresh token, dang ky, quen mat khau/OTP (la phan cua Chi), khoa tai khoan sau nhieu lan sai,
bang `role_permissions`, thu hoi token truoc han.

## Cac phuong an da can nhac
- A. JWT tu viet bang jjwt + 1 filter (DUOC CHON): don gian, de doc cho ca nhom, hop Swagger va frontend tach rieng.
  Danh doi: khong thu hoi duoc token truoc khi het han (toi da 8 gio).
- B. Spring OAuth2 Resource Server: it code hon nhung kho hieu, van phai tu tao token.
- C. Session cookie: don gian, thu hoi duoc, nhung bat tien khi test bang Swagger va can bat lai CSRF.

## Quyet dinh
- Token HS256, song 8 gio (`app.jwt.expiration-minutes`), bi mat `app.jwt.secret` (co mac dinh dev, doi trong application-local.properties).
- Dang nhap thanh cong ghi `user_activity_logs` (action LOGIN). Sai tai khoan/mat khau dung chung mot thong bao.
- Mac dinh BAT BUOC dang nhap cho moi API tru `/api/auth/login`, `/api/ping`, Swagger (Phi dong y).
- CORS cho `http://localhost:*` va `http://127.0.0.1:*`.
- Mat khau BCrypt. Tat tai khoan mac dinh cua Spring (khong con "generated security password").

## Cach dung cho dong nghiep
1. Swagger (`/swagger-ui.html`) -> `POST /api/auth/login` voi `admin@minihouse.local` / `Admin@123` -> copy `accessToken`.
2. Bam Authorize, dan token. Hoac gui header `Authorization: Bearer <token>`.
3. Gan quyen: `@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")`. Lay nguoi dang nhap: `@AuthenticationPrincipal AuthUser user`.

## Ma nguon
shared/security (AuthUser, JwtService, JwtAuthFilter), shared/config (SecurityConfig, OpenApiConfig),
shared/exception/GlobalExceptionHandler (them 401/403/400), phi/{controller,service,repository,dto} (AuthController, AuthService...),
test: JwtServiceTest.

## Da kiem chung
3 unit test JwtServiceTest. Chay app that: login dung -> 200 + token; sai mat khau -> 401; thieu truong -> 400;
khong token/token sai -> 401; co token -> /api/auth/me 200; Swagger 200; log LOGIN ghi vao DB.
Chua kiem chung: nhanh 403 cua `@PreAuthorize` (chua co endpoint nao gan quyen).
