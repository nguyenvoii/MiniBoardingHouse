# Thiet ke: Tai khoan dang nhap cho nguoi thue (UC10, UC11, UC36, UC37, UC94)

Ngay: 2026-11-14. Trang thai: CHO PHI DUYET (chua code).

## Muc dich
Excel yeu cau 3 vai tro: Chu tro, Nhan vien, Nguoi thue. Nguoi thue dang nhap va chi xem du lieu cua chinh minh
(phong, hop dong, hoa don, cong no). Hien DB khong co cach noi tai khoan voi ho so khach thue.

## Rang buoc quan trong
DB do thay cung cap => KHONG them bang, KHONG them/sua cot cua bang co san. Chi duoc them DU LIEU (role TENANT trong seed).

## Y tuong chinh: noi bang so dien thoai, khong sua DB
- `tenants.phone_number` la UNIQUE NOT NULL; `users.phone_number` unique khi co gia tri (filtered index).
- Tai khoan nguoi thue = 1 dong `users` co `phone_number` TRUNG voi `tenants.phone_number`, co role `TENANT` trong `user_roles`.
- Khi dang nhap, neu user co role TENANT thi tim `tenants` theo so dien thoai de lay `tenantId`, dua vao JWT.
- API cua nguoi thue lay `tenantId` tu token (`AuthUser.tenantId()`), KHONG nhan tenantId tu client => khong xem duoc du lieu nguoi khac.

## Phuong an da can nhac
- A. Noi bang so dien thoai, khong doi DB (DUOC CHON): hop rang buoc cua thay, ca 2 ben deu da UNIQUE nen 1-1.
  Danh doi: doi so dien thoai phai cap nhat ca `tenants` va `users` cung luc.
- B. Them cot `tenants.user_id` hoac bang noi moi: noi chinh xac nhat nhung vi pham rang buoc "DB cua thay".
- C. Noi bang email: `tenants.email` cho phep NULL nen khong dang tin.

## Luong nguoi dung (cach b)
Khach den xem phong -> nhan vien/chu tro tao ho so khach thue (UC30) -> cap tai khoan (UC10, tao user + role TENANT,
mat khau tam) -> nguoi thue dang nhap xem phong/hop dong/hoa don/cong no (UC36, UC94); gui yeu cau thue/giu cho (UC37)
khi da co ho so. Khong co man hinh tu dang ky cong khai.

## Pham vi lam (neu duoc duyet)
1. `db/seed.sql`: them role TENANT (chay lai an toan).
2. `AuthUser` them `tenantId` (null voi nhan vien); `JwtService` ghi/doc claim `tenantId`.
3. `AuthService.login`: neu co role TENANT thi tra cuu tenant theo SDT; neu khong thay ho so thi bao loi ro rang.
4. UC10: `POST /api/tenants/{tenantId}/account` (chi LANDLORD/MANAGER): tao user (SDT, email, ten lay tu ho so,
   mat khau tam duoc tra ve 1 lan, BCrypt), gan role TENANT; tu choi neu da co tai khoan hoac ho so thieu SDT.
5. Doc quy uoc cho dong nghiep (README): API nguoi thue dung `user.tenantId()`, API nhan vien gan `@PreAuthorize`.
6. Test don vi cho phan JWT/tenantId; chay app that kiem chung dang nhap TENANT.

## Khong lam
Tu dang ky cong khai, quen mat khau cho nguoi thue (dung chung UC4 sau), sua bat ky bang nao cua thay,
API xem phong/hop dong/hoa don/cong no (UC36, UC94 la viec cua Chi, Toan, Viet, chi can dung `tenantId` tu token).

## Quyet dinh cua toi (Phi co the sua)
- Phi/Chi tra cuu tenant theo SDT: tam dung repository chi-doc `TenantReadRepository` trong package `phi`;
  khi Chi lam xong `TenantService` thi doi sang goi service do.
- Mat khau tam: sinh ngau nhien 10 ky tu, tra ve 1 lan trong response cua UC10.
- Ten role: `TENANT` (cung kieu voi LANDLORD, MANAGER...).

## Rui ro / chua biet
- SDT trong `tenants` va `users` phai cung dinh dang (chi chu so); se `trim` khi luu/so khop.
- Token mang `tenantId` luc dang nhap; doi SDT sau do thi token cu van dung toi khi het han (8 gio).
