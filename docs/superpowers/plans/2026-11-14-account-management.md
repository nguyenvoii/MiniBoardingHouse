# Quan ly tai khoan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Doi mat khau, ho so ca nhan, quan ly nhan vien (them/sua/khoa), xem nhat ky, dang xuat; khoa co hieu luc ngay.

**Architecture:** Tat ca trong package `phi` (+ `shared/security`, `shared/util`). Moi nhom chuc nang la 1 service + 1 controller; ghi nhat ky qua `ActivityLogService` dung chung. Kiem tra khoa tai khoan trong `JwtAuthFilter` qua interface `UserStatusChecker`.

**Tech Stack:** Java 17, Spring Boot 3.3.5, Spring Data JPA, JUnit 5 + Mockito, `@WebMvcTest`, SQL Server (E2E tren DB tam).

**Spec:** `docs/superpowers/specs/2026-11-14-account-management-design.md`

## Global Constraints

- KHONG sua cau truc DB. Thong bao loi tieng Viet khong dau; API tra `ApiResponse`; loi nghiep vu `BusinessException`; phan trang `PageResponse`.
- Code moi trong `phi/{controller,service,repository,dto}`, `shared/security`, `shared/util`; khong sua `chi/toan/viet`.
- Vai tro nhan vien: `MANAGER`, `CASHIER`, `METER_READER`. Mat khau moi >= 8 ky tu, co chu va so.
- Lenh Maven: `export JAVA_HOME="C:\\Program Files\\Java\\jdk-17"; MVN="/c/Program Files/NetBeans-12.6/netbeans/java/maven/bin/mvn.cmd"`. Chay app thu o cong 18090+, tat dung tien trinh minh mo (PowerShell loc CommandLine theo cong). Test ghi du lieu chi tren DB tam `ScratchE2EDB` (xoa sau khi xong).
- Commit nho, tieng Viet khong dau, KHONG them dong Co-Authored-By. Khong push (Phi noi "sync").

## Review Focus

1. Nguoi thue doi SDT trong ho so lam dut lien ket tai khoan: bi chan 409 (Task 2).
2. Khoa tai khoan phai chan NGAY token da cap, ke ca nguoi thue bi vo hieu hoa (Task 4).
3. Nhan vien/chu tro khong tu khoa minh; khong thao tac duoc tren LANDLORD/TENANT qua `/api/staff` (Task 3).
4. Trung email/SDT khi them/sua nhan vien va khi doi ho so: 409, tru chinh minh (Task 2, 3).
5. Doi mat khau: sai mat khau cu 401/400, mat khau yeu hoac trung mat khau cu bi tu choi (Task 2).
6. Phan trang/tim kiem: size > 100 bi cat, tu khoa rong tra het, tu khoa co ky tu `%` khong gay loi (Task 3, E2E).

---

### Task 1: Nen tang dung chung (nhat ky, bo sinh mat khau, logout)

**Files:** Create `shared/util/PasswordGenerator.java`, `phi/service/ActivityLogService.java`, test `PasswordGeneratorTest`, `ActivityLogServiceTest`; Modify `phi/service/TenantAccountService.java`, `phi/service/AuthService.java`, `phi/controller/AuthController.java`, `phi/controller/TenantAccountController.java`, their tests.

**Interfaces:**
- Produces: `PasswordGenerator.generate() -> String` (10 ky tu, bo `0O1lI`); `ActivityLogService.log(Integer userId, String actionType, String description, String ip)` (`@Transactional`, tim `User` bang `userRepository.getReferenceById`); `AuthController POST /api/auth/logout` tra `ApiResponse<Void>` va ghi `LOGOUT`; `TenantAccountService.createAccount(Integer tenantId, Integer actorUserId, String ip)`.

- [ ] **Step 1: Test** `PasswordGeneratorTest`: 200 lan `generate()` deu dai 10, chi chua ky tu hop le, khong chua `0O1lI`, khong 2 lan lien tiep giong nhau. `ActivityLogServiceTest` (Mockito): `log(5,"LOGOUT","x","1.2.3.4")` luu `UserActivityLog` co user id 5, actionType, description, ip.
- [ ] **Step 2: Chay** `"$MVN" test -Dtest='PasswordGeneratorTest,ActivityLogServiceTest'` -> FAIL (chua co lop).
- [ ] **Step 3: Implement** `PasswordGenerator` (chuyen logic tu `TenantAccountService`), `ActivityLogService`; `TenantAccountService` dung `PasswordGenerator` va ghi `CREATE_TENANT_ACCOUNT` khi `actorUserId != null` (cap nhat `TenantAccountServiceTest`: constructor moi + 1 test xac nhan co ghi nhat ky); `AuthService.login` dung `ActivityLogService`; them `logout` endpoint (user null -> bo qua ghi, van 200).
- [ ] **Step 4: Chay** `"$MVN" test` -> tat ca PASS. **Commit.**

### Task 2: Doi mat khau (UC5) va ho so ca nhan (UC6)

**Files:** Create `phi/dto/ChangePasswordRequest.java`, `ProfileResponse.java`, `UpdateProfileRequest.java`, `phi/service/AccountService.java`, `phi/controller/AccountController.java`, test `AccountServiceTest`.

**Interfaces:**
- Consumes: `UserRepository.findById/save/findByPhoneNumber`, `ActivityLogService.log`, `PasswordEncoder`, `UserRoleRepository.findRoleCodesByUserId`.
- Produces: `AccountService.changePassword(Integer userId, String current, String next, String ip)`; `getProfile(Integer userId) -> ProfileResponse(userId, fullName, email, phoneNumber, roles)`; `updateProfile(Integer userId, UpdateProfileRequest(fullName, phoneNumber), String ip) -> ProfileResponse`. Endpoints `POST /api/auth/password` (`ChangePasswordRequest(currentPassword, newPassword)`), `GET/PUT /api/auth/profile`.

- [ ] **Step 1: Test** (Mockito) cho `AccountServiceTest`: doi mat khau dung -> hash moi khop mat khau moi + ghi `CHANGE_PASSWORD`; sai mat khau cu -> `BusinessException` 400 va khong luu; mat khau moi < 8, khong co so, khong co chu -> 400; trung mat khau cu -> 400; cap nhat ho so doi ten + SDT hop le -> luu va ghi `UPDATE_PROFILE`; SDT da thuoc nguoi khac -> 409; SDT cua chinh minh khong tinh trung; **nguoi thue doi SDT -> 409 "lien he chu tro"** (Review Focus 1, nhung nguoi thue gui lai SDT hien tai khong bi loi); SDT sai dinh dang (`abc`) -> 400; SDT rong -> thanh null cho nhan vien.
- [ ] **Step 2: Chay** `"$MVN" test -Dtest=AccountServiceTest` -> FAIL.
- [ ] **Step 3: Implement** theo test; validation dinh dang bang `@Pattern("^\\+?[0-9]{9,15}$")` tren DTO (cho phep rong), kiem tra o service cho chinh sach mat khau. Controller lay `@AuthenticationPrincipal AuthUser` va `http.getRemoteAddr()`, dung `CurrentUser`-style: user null -> 401.
- [ ] **Step 4: Chay** `"$MVN" test` -> PASS. **Commit.**

### Task 3: Quan ly nhan vien (UC8, UC9)

**Files:** Create `phi/dto/{CreateStaffRequest,UpdateStaffRequest,UpdateStatusRequest,StaffResponse,StaffCreatedResponse}.java`, `phi/service/StaffService.java`, `phi/controller/StaffController.java`, test `StaffServiceTest`; Modify `phi/repository/UserRepository.java` (them `searchStaff`), `UserRoleRepository.java` (them `findRolesByUserIds`, `deleteByUserIdAndRoleIds`-tuong-duong).

**Interfaces:**
- Produces: `StaffService.create(CreateStaffRequest(fullName, email, phoneNumber, roles), Integer actorId, String ip) -> StaffCreatedResponse(staff, temporaryPassword)`; `search(String keyword, String status, int page, int size) -> PageResponse<StaffResponse>`; `get(Integer id) -> StaffResponse`; `update(Integer id, UpdateStaffRequest(fullName, email, phoneNumber, roles), Integer actorId, String ip) -> StaffResponse`; `setStatus(Integer id, String status, Integer actorId, String ip) -> StaffResponse`. `StaffResponse(userId, fullName, email, phoneNumber, status, roles, createdAt)`. Endpoints `POST /api/staff`, `GET /api/staff`, `GET /api/staff/{id}`, `PUT /api/staff/{id}`, `PATCH /api/staff/{id}/status`; `@PreAuthorize("hasRole('LANDLORD')")` cho ghi, `hasAnyRole('LANDLORD','MANAGER')` cho doc.

- [ ] **Step 1: Test** (Mockito) `StaffServiceTest`: tao nhan vien hop le (co email hoac SDT, roles MANAGER) -> user ACTIVE, hash BCrypt khop mat khau tam, role luu dung, ghi `CREATE_STAFF`; thieu ca email va SDT -> 400; roles rong / chua LANDLORD hoac TENANT / ten role la -> 400; trung email -> 409; trung SDT -> 409; role chua nap trong DB -> 500 ro rang; `update` doi thong tin va thay tap vai tro (ghi `UPDATE_STAFF`), trung email cua nguoi khac -> 409, email cua chinh no khong tinh trung; `setStatus` LOCKED -> `status == "LOCKED"` va ghi `LOCK_USER`; mo khoa ghi `UNLOCK_USER`; **tu khoa chinh minh -> 409**; trang thai la (`INACTIVE`, `x`) -> 400; **nguoi dung la LANDLORD hoac TENANT hoac khong ton tai -> 404** (Review Focus 3); `search` cat size > 100 ve 100, size < 1 ve 20, page am ve 0, tu khoa `null` thanh chuoi rong va escape `%` `_`.
- [ ] **Step 2: Chay** `"$MVN" test -Dtest=StaffServiceTest` -> FAIL.
- [ ] **Step 3: Implement** `searchStaff` bang `@Query` dung tham so chuoi rong thay cho null (`:kw = '' or ...`, `:status = '' or ...`, join `UserRole` chi lay role trong tap nhan vien, `distinct`), bat `Pageable` sap xep `createdAt desc`; lay vai tro theo lo bang 1 truy van `findRolesByUserIds(List<Integer>)` tra `List<Object[]>` roi nhom theo user.
- [ ] **Step 4: Chay** `"$MVN" test` -> PASS. **Commit.**

### Task 4: Khoa tai khoan co hieu luc ngay

**Files:** Create `shared/security/UserStatusChecker.java`, `phi/service/UserStatusCheckerImpl.java`; Modify `shared/security/JwtAuthFilter.java`, `shared/config/SecurityConfig.java`, `phi/repository/UserRepository.java` (`@Query` lay status theo id), `SecurityRulesTest.java`; Test `UserStatusCheckerImplTest`, them case vao `SecurityRulesTest`.

**Interfaces:**
- Produces: `interface UserStatusChecker { boolean isActive(Integer userId); }` (user khong ton tai -> false); `JwtAuthFilter(JwtService, UserStatusChecker)` bo qua token cua user khong active (request thanh chua dang nhap -> 401).

- [ ] **Step 1: Test** `UserStatusCheckerImplTest`: status `ACTIVE` -> true; `LOCKED` / `INACTIVE` / khong ton tai -> false. `SecurityRulesTest` (`@WebMvcTest`, `@MockBean UserStatusChecker`): token hop le nhung checker tra false -> 401 tren `/api/probe/staff` va `/api/me/probe`; checker tra true -> nhu cu. Cap nhat cac test hien co de checker tra true.
- [ ] **Step 2: Chay** `"$MVN" test -Dtest='UserStatusCheckerImplTest,SecurityRulesTest'` -> FAIL.
- [ ] **Step 3: Implement**; `SecurityConfig` nhan `UserStatusChecker` va truyen vao filter; chi kiem tra khi token hop le (khong them truy van cho request khong token).
- [ ] **Step 4: Chay** `"$MVN" test` -> PASS. **Commit.**

### Task 5: Xem nhat ky thao tac (UC13)

**Files:** Create `phi/dto/ActivityLogResponse.java`, `phi/controller/ActivityLogController.java`; Modify `phi/repository/UserActivityLogRepository.java` (`search`), `phi/service/ActivityLogService.java` (them `search`, `mine`); Test them vao `ActivityLogServiceTest`.

**Interfaces:**
- Produces: `ActivityLogService.search(Integer userId, String actionType, LocalDateTime from, LocalDateTime to, int page, int size) -> PageResponse<ActivityLogResponse(logId, userId, actionType, description, ipAddress, createdAt)>`; `mine(Integer userId, int page, int size)`. Endpoints `GET /api/activity-logs` (`hasRole('LANDLORD')`; tham so `userId, actionType, from, to, page, size`), `GET /api/auth/activity`.

- [ ] **Step 1: Test** (Mockito) cat size, `from > to` -> 400, `mine` luon loc theo user hien tai.
- [ ] **Step 2: Chay** test -> FAIL. **Step 3: Implement** (tham so rong thay null trong `@Query`: `userId = 0` nghia la tat ca, `actionType = ''`, moc thoi gian mac dinh `1900-01-01` / `9999-12-31`). **Step 4: Chay** `"$MVN" test` -> PASS. **Commit.**

### Task 6: Kiem chung chay that va tai lieu

**Files:** Modify `README.md`, spec (trang thai), memory `miniboardinghouse-progress`.

- [ ] **Step 1: Dung DB tam** `ScratchE2EDB` (sed doi ten cho `db/group-7.sql` + `db/seed.sql`, tao user `mbh_app` db_owner), chen 1 khach thue; chay app o cong 18090 voi `APP_SECURITY_ENABLED=true` + `SPRING_DATASOURCE_URL` (chi 1 tham so qua `-Dspring-boot.run.arguments`).
- [ ] **Step 2: Kich ban curl**, mong doi: them nhan vien 200 + mat khau tam; trung email 409; nhan vien dang nhap; MANAGER goi `POST /api/staff` -> 403; tim kiem co phan trang, tu khoa `%` khong loi, size 1000 bi cat; khoa nhan vien -> **token cu cua nhan vien do bi 401 ngay**, dang nhap lai bi 403; mo khoa -> dang nhap duoc; nhan vien tu doi mat khau (sai mat khau cu bi tu choi, dung thi dang nhap lai bang mat khau moi); nguoi thue doi SDT -> 409; chu tro xem `GET /api/activity-logs` thay LOGIN/CREATE_STAFF/LOCK_USER; `GET /api/auth/activity` chi thay cua minh; `PUT /api/staff/{id}` doi vai tro; khoa chinh minh -> 409; khoa tai khoan nguoi thue qua `/api/staff` -> 404.
- [ ] **Step 3: Don dep**: tat dung tien trinh cong 18090, `DROP DATABASE ScratchE2EDB`, xac nhan DB that khong doi (users/tenants/roles).
- [ ] **Step 4: Cap nhat README** (cac API moi, nhat ky, khoa tuc thi), spec, memory. **Commit.**
