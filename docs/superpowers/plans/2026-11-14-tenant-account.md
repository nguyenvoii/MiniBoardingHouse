# Tai khoan dang nhap cho nguoi thue - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nguoi thue dang nhap duoc (role `TENANT`), token mang `tenantId`, va chu tro/quan ly cap tai khoan cho khach thue (UC10) ma khong sua cau truc DB.

**Architecture:** Tai khoan nguoi thue la 1 dong `users` co `phone_number` bang `tenants.phone_number` + role `TENANT`. Luc dang nhap, `AuthService` tra cuu tenant theo SDT va dua `tenantId` vao JWT (`AuthUser.tenantId`). UC10 la 1 endpoint rieng trong package `phi`.

**Tech Stack:** Java 17, Spring Boot 3.3.5, Spring Security, jjwt 0.12.6, JUnit 5 + Mockito (da co trong spring-boot-starter-test), SQL Server.

**Spec:** `docs/superpowers/specs/2026-11-14-tenant-account-design.md`

## Global Constraints

- KHONG them/sua/xoa bang hay cot cua DB; chi them DU LIEU seed (role `TENANT`).
- Thong bao loi bang tieng Viet khong dau; moi API tra `ApiResponse`; loi nghiep vu dung `BusinessException`.
- Code moi nam trong package `phi` (controller/service/repository/dto) va `shared/security`; khong sua package `chi/toan/viet`.
- Lenh Maven: `export JAVA_HOME="C:\\Program Files\\Java\\jdk-17"; MVN="/c/Program Files/NetBeans-12.6/netbeans/java/maven/bin/mvn.cmd"`; chay thu app o cong 18080+ va tat dung tien trinh minh mo (loc CommandLine theo cong), khong tat `java.exe` hang loat.
- Test ghi du lieu chi tren DB tam `ScratchE2EDB` (xoa sau khi xong), khong ghi vao `MiniBoardingHouseDB` ngoai seed.
- Commit nho, thong diep tieng Viet khong dau, ket thuc bang dong `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Khong push (Phi noi "sync" moi push).

## Review Focus

1. Tai khoan co role `TENANT` nhung ho so khach thue da bi xoa/doi SDT: dang nhap tra loi ro rang, khong NullPointerException (Task 2).
2. Cap tai khoan 2 lan cho cung 1 khach thue: lan 2 tra 409, khong 500 (Task 3).
3. Email cua khach thue da bi `users` khac dung (UNIQUE): tra 409 ro rang, khong loi SQL (Task 3).
4. Token nguoi thue goi endpoint cua nhan vien khi bat bao mat: 403 (Task 4, lan dau thu duoc nhanh 403).
5. SDT cua khach thue co khoang trang dau/cuoi: van noi duoc tai khoan (Task 2 + Task 4).

---

### Task 1: Role TENANT va tenantId trong token

**Files:**
- Modify: `db/seed.sql` (them role), `src/main/java/com/group7/minihouse/shared/security/AuthUser.java`, `JwtService.java`, `src/main/java/com/group7/minihouse/phi/service/AuthService.java` (chi sua cho khop constructor), `src/test/java/com/group7/minihouse/shared/security/JwtServiceTest.java`

**Interfaces:**
- Produces: `record AuthUser(Integer userId, String email, String fullName, List<String> roles, Integer tenantId)`; `boolean isTenant()` = `roles.contains("TENANT")`. `JwtService.generate/parse` ghi/doc claim `"tenantId"` (null voi nhan vien).

- [ ] **Step 1: Sua JwtServiceTest** cho constructor moi `AuthUser(7, "a@b.c", "An", List.of("TENANT"), 42)` va them test `tenantIdRoundTrip`: sau `parse(generate(u))`, `assertEquals(42, back.tenantId())` va `assertTrue(back.isTenant())`; them `staffHasNullTenantId`: user `List.of("MANAGER")`, tenantId null -> `assertNull(back.tenantId())`.
- [ ] **Step 2: Chay** `"$MVN" test -Dtest=JwtServiceTest` -> FAIL bien dich (AuthUser chua co tenantId).
- [ ] **Step 3: Implement** them component `Integer tenantId` vao `AuthUser` + `isTenant()`; `JwtService.generate` them `.claim("tenantId", user.tenantId())`; `parse` doc `c.get("tenantId", Integer.class)`. Sua cho `AuthService` goi `new AuthUser(..., roles, null)` tam thoi.
- [ ] **Step 4: Them role vao `db/seed.sql`**: them dong `('TENANT', N'Nguoi thue', N'Xem phong, hop dong, hoa don, cong no cua chinh minh')` vao VALUES cua khoi INSERT roles (giu `WHERE NOT EXISTS`). Chay `sqlcmd -S localhost -E -C -f 65001 -i db\seed.sql` hai lan; `SELECT role_code FROM roles` co 5 dong, khong trung.
- [ ] **Step 5: Chay** `"$MVN" test` -> tat ca PASS.
- [ ] **Step 6: Commit** `git add -A && git commit` ("Them role TENANT va tenantId vao token").

### Task 2: Dang nhap nguoi thue tra tenantId

**Files:**
- Create: `phi/repository/TenantReadRepository.java`, `src/test/java/com/group7/minihouse/phi/service/AuthServiceTest.java`
- Modify: `phi/service/AuthService.java`

**Interfaces:**
- Consumes: `AuthUser(..., Integer tenantId)` (Task 1); `UserRoleRepository.findRoleCodesByUserId(Integer) -> List<String>`.
- Produces: `interface TenantReadRepository extends JpaRepository<Tenant, Integer>` voi `@Query("select t from Tenant t where trim(t.phoneNumber) = :phone") Optional<Tenant> findByPhone(@Param("phone") String phone)` (tam thoi, chi doc; doi sang `TenantService` cua Chi khi co).

- [ ] **Step 1: Viet AuthServiceTest (Mockito, khong can DB)** dung `@ExtendWith(MockitoExtension.class)`, mock 5 phu thuoc cua AuthService (+ `TenantReadRepository`), PasswordEncoder that (`BCryptPasswordEncoder`), `JwtService` that. Test:
  - `tenantLoginCarriesTenantId`: user ACTIVE co phone "0901234567", roles `["TENANT"]`, tenant id 42 -> `login(...).user().tenantId() == 42`.
  - `staffLoginHasNullTenantId`: roles `["MANAGER"]` -> `tenantId() == null`, khong goi `tenantReadRepository`.
  - `tenantWithoutProfileGetsClearError` (Review Focus 1): roles `["TENANT"]`, `findByPhone` tra `Optional.empty()` -> nem `BusinessException` voi status `CONFLICT`, message chua "ho so khach thue".
  - `tenantWithNullPhoneGetsClearError`: user phone null -> cung `BusinessException` CONFLICT, khong NPE.
- [ ] **Step 2: Chay** `"$MVN" test -Dtest=AuthServiceTest` -> FAIL.
- [ ] **Step 3: Implement** `TenantReadRepository` va them vao constructor cua `AuthService`; sau khi lay `roles`, neu co `"TENANT"`: `tenantId = phone == null ? throw : tenantReadRepository.findByPhone(phone.trim()).map(Tenant::getTenantId).orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Tai khoan chua lien ket ho so khach thue, lien he chu tro"))`; truyen `tenantId` vao `AuthUser`.
- [ ] **Step 4: Chay** `"$MVN" test` -> PASS.
- [ ] **Step 5: Commit** ("Dang nhap nguoi thue: tra cuu tenant theo SDT, dua tenantId vao token").

### Task 3: Cap tai khoan cho nguoi thue (UC10)

**Files:**
- Create: `phi/repository/RoleRepository.java`, `phi/dto/TenantAccountResponse.java`, `phi/service/TenantAccountService.java`, `phi/controller/TenantAccountController.java`, `src/test/java/com/group7/minihouse/phi/service/TenantAccountServiceTest.java`
- Modify: `phi/repository/UserRepository.java` (them `boolean existsByEmail(String)`, `boolean existsByPhoneNumber(String)`)

**Interfaces:**
- Consumes: `TenantReadRepository.findById(Integer)` (Task 2), `UserRepository.save`, `UserRoleRepository.save`, `PasswordEncoder`.
- Produces: `record TenantAccountResponse(Integer userId, String phoneNumber, String temporaryPassword)`; `TenantAccountService.createAccount(Integer tenantId) -> TenantAccountResponse` (`@Transactional`); `RoleRepository.findByRoleCode(String) -> Optional<Role>`; endpoint `POST /api/tenants/{tenantId}/account` co `@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")`, tra `ApiResponse<TenantAccountResponse>`.

- [ ] **Step 1: Viet TenantAccountServiceTest (Mockito)**:
  - `createsUserWithTenantRoleAndTempPassword`: tenant 42 phone " 0901234567 " email "t@x.vn" ten "Tran An" -> user luu co `phoneNumber == "0901234567"` (da trim), `email == "t@x.vn"`, `fullName == "Tran An"`, `status == "ACTIVE"`, `passwordHash` la BCrypt cua `temporaryPassword`; `temporaryPassword.length() == 10`; `UserRole` luu co role `TENANT`.
  - `rejectsSecondAccountForSamePhone` (Review Focus 2): `existsByPhoneNumber` true -> `BusinessException` status `CONFLICT`, khong `save`.
  - `rejectsEmailAlreadyUsed` (Review Focus 3): email co, `existsByEmail` true -> `CONFLICT`.
  - `tenantNotFound`: `findById` rong -> status `NOT_FOUND`.
  - `missingTenantRole`: `RoleRepository` rong -> `BusinessException` 500-class ("Chua nap role TENANT, chay db/seed.sql").
  - tenant email null: khong goi `existsByEmail`, user.email null.
- [ ] **Step 2: Chay** `"$MVN" test -Dtest=TenantAccountServiceTest` -> FAIL.
- [ ] **Step 3: Implement** service theo test; mat khau tam sinh bang `SecureRandom`, 10 ky tu chu+so bo ky tu de nham (`0O1lI`); `UserRole` dat `new UserRoleId(userId, roleId)`. Controller chi goi service va boc `ApiResponse.ok`.
- [ ] **Step 4: Chay** `"$MVN" test` -> PASS.
- [ ] **Step 5: Commit** ("UC10: cap tai khoan dang nhap cho nguoi thue").

### Task 4: Kiem chung chay that va tai lieu

**Files:**
- Modify: `README.md`, `docs/superpowers/specs/2026-11-14-tenant-account-design.md` (trang thai thanh "DA DUYET va DA LAM"), memory `miniboardinghouse-progress`

- [ ] **Step 1: Tao DB tam** `ScratchE2EDB`: `sed 's/MiniBoardingHouseDB/ScratchE2EDB/g'` cho `db/group-7.sql` va `db/seed.sql`, chay bang `sqlcmd ... -i "$(cygpath -w file)"`; chay them `CREATE USER mbh_app FOR LOGIN mbh_app; ALTER ROLE db_owner ADD MEMBER mbh_app;` trong DB tam; `INSERT` 1 tenant voi phone `' 0901234567'` (co khoang trang dau), email NULL, CCCD bat ky.
- [ ] **Step 2: Chay app bat bao mat tren DB tam** (cong 18090): dat bien moi truong `APP_SECURITY_ENABLED=true`, `SPRING_DATASOURCE_URL=jdbc:sqlserver://localhost:1433;databaseName=ScratchE2EDB;encrypt=true;trustServerCertificate=true`; chi truyen `--server.port=18090` qua `-Dspring-boot.run.arguments` (nhieu tham so cach nhau dau cach bi loi tren may nay).
- [ ] **Step 3: Kiem chung bang curl** va ghi ket qua mong doi:
  - dang nhap admin -> token; `POST /api/tenants/{id}/account` bang token admin -> 200, co `temporaryPassword`;
  - goi lan 2 -> 409 (Review Focus 2);
  - dang nhap bang SDT + mat khau tam -> 200, `user.tenantId` bang id tenant, roles `["TENANT"]` (Review Focus 5);
  - `GET /api/auth/me` bang token nguoi thue -> co `tenantId`;
  - `POST /api/tenants/{id}/account` bang token nguoi thue -> **403** (Review Focus 4, nhanh 403 cua `@PreAuthorize`);
  - xoa/doi SDT tenant trong DB tam roi dang nhap lai -> 409 "ho so khach thue", khong 500 (Review Focus 1).
- [ ] **Step 4: Don dep**: tat dung tien trinh cong 18090 (PowerShell loc CommandLine), `DROP DATABASE ScratchE2EDB`; xac nhan `SELECT COUNT(*) FROM users` tren DB that van la 1 va `git status` chi con file tai lieu.
- [ ] **Step 5: Cap nhat README** muc "Giai doan 0": tai khoan nguoi thue (role `TENANT`, noi bang SDT), API nguoi thue lay `user.tenantId()` tu `@AuthenticationPrincipal AuthUser`, khong nhan tenantId tu client; API nhan vien gan `@PreAuthorize`. Cap nhat spec va memory tien do.
- [ ] **Step 6: Commit** ("Kiem chung tai khoan nguoi thue, cap nhat tai lieu").
