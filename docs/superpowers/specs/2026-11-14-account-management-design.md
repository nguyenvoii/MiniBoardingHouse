# Thiet ke: Quan ly tai khoan (UC5, UC6, UC8, UC9, UC13 + phan con lai UC3)

Ngay: 2026-11-14. Phi uy quyen chon phuong an tot nhat va quy trinh day du (khong duyet tung buoc).

## Muc dich
Sau dang nhap (UC3) va cap tai khoan nguoi thue (UC10), moi nguoi dung can: doi mat khau (nguoi thue can doi mat khau tam),
cap nhat ho so, chu tro quan ly nhan vien (them, sua, khoa/mo khoa), xem nhat ky thao tac, dang xuat.

## Muc tieu
- UC5 `POST /api/auth/password` doi mat khau (moi nguoi da dang nhap, ke ca nguoi thue).
- UC6 `GET/PUT /api/auth/profile` xem/sua ho ten + so dien thoai cua chinh minh.
- UC8/UC9 `/api/staff`: them nhan vien (LANDLORD), danh sach/tim kiem/xem (LANDLORD, MANAGER), sua thong tin + vai tro (LANDLORD),
  khoa/mo khoa (LANDLORD).
- UC13 `GET /api/activity-logs` (LANDLORD, loc theo nguoi dung/hanh dong/thoi gian, phan trang) va `GET /api/auth/activity` (nhat ky cua chinh minh).
- UC3 con lai: `POST /api/auth/logout` ghi nhat ky LOGOUT (JWT khong luu phien, client bo token).
- Khoa tai khoan co hieu luc NGAY: moi request co token deu kiem tra `users.status` trong DB.

## Khong lam (non-goals)
Quen mat khau/OTP (UC1,2,4), upload anh (UC7), doi email, thu hoi token sau khi doi mat khau, ghi nhat ky dang nhap that bai,
bang/cot moi (khong sua DB), tao/xoa chu tro tu API.

## Quyet dinh cua toi (Phi uy quyen; co the sua sau)
1. Mat khau nhan vien moi: he thong sinh ngau nhien 10 ky tu, tra ve 1 lan (giong UC10), nhan vien doi bang UC5.
   Tai su dung bo sinh mat khau -> tach thanh `shared/util/PasswordGenerator`.
2. Chinh sach mat khau moi: >= 8 ky tu, co chu va so, khac mat khau cu.
3. Vai tro nhan vien hop le: MANAGER, CASHIER, METER_READER. Khong gan/doi LANDLORD hoac TENANT qua API nhan vien.
   Chi thao tac duoc tren tai khoan co it nhat 1 vai tro nhan vien va khong phai LANDLORD; con lai tra 404.
4. Nguoi thue KHONG duoc doi so dien thoai trong ho so (vi tai khoan noi voi ho so khach thue bang SDT) -> 409, huong dan lien he chu tro.
5. Khong cho khoa chinh minh. Trang thai hop le: ACTIVE, LOCKED.
6. Phuong an khoa tuc thi: kiem tra `users.status` trong DB moi request co token (1 truy van theo khoa chinh), qua interface
   `UserStatusChecker` trong `shared/security` (cai dat o `phi`). Da can nhac: (A) kiem tra DB moi request (chon: dung ca khi
   khoi dong lai/nhieu may, chi 1 query PK), (B) danh sach thu hoi trong bo nho (mat khi restart), (C) token ngan + refresh (qua lon cho do an).
7. Tim kiem nhan vien: tu khoa (ten/email/SDT) + trang thai, phan trang (size toi da 100). Tai khoan bi cat vai tro/khong con vai tro nhan vien khong hien.
8. Mọi hanh dong quan trong ghi `user_activity_logs`: CHANGE_PASSWORD, UPDATE_PROFILE, CREATE_STAFF, UPDATE_STAFF, LOCK_USER,
   UNLOCK_USER, CREATE_TENANT_ACCOUNT (bo sung phan hoan lai cua UC10), LOGOUT, LOGIN.
9. Thong bao loi tieng Viet khong dau; moi API tra `ApiResponse`; trung email/SDT -> 409.

## Rui ro / chap nhan
Doi mat khau khong thu hoi token cu (toi da 8 gio); khong co gioi han so lan dang nhap sai.
`@PreAuthorize` chi co tac dung khi `app.security.enabled=true` (mac dinh mo de dong doi test).
