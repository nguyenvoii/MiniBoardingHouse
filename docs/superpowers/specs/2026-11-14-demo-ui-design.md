# Thiet ke: Giao dien xem thu (demo UI) cho quan ly tai khoan

Ngay: 2026-11-14. Phi chot phuong an A (trang web tinh do Spring Boot phuc vu), muon xem thu tren may nay voi SQL that.

## Muc dich
Cho Phi nhin thay va bam thu cac chuc nang da lam (dang nhap, nhan vien, khoa tai khoan, nhat ky, cap tai khoan nguoi thue)
truoc khi chot, khong can Postman hay Swagger.

## Phuong an da can nhac
- A. Trang web tinh (HTML + JS thuan) do Spring Boot phuc vu (CHON): khong can Node/build, cung goc nen khong vuong CORS.
- B. Frontend tach rieng (React/Vite): dep va sat thuc te hon nhung can cai Node, ton cong; de nhom chot cong nghe frontend sau.
- C. Chi dung Swagger: da co, khong phai giao dien de xem.

## Pham vi
- Man hinh dang nhap; thanh tren hien ten, vai tro, nut Dang xuat; menu doi theo vai tro:
  moi nguoi: Ho so, Doi mat khau, Nhat ky cua toi. LANDLORD: Nhan vien (them, sua, vai tro, khoa/mo khoa), Nhat ky he thong (loc),
  Cap tai khoan nguoi thue. MANAGER: Nhan vien (chi xem/tim kiem). TENANT: chi 3 muc chung.
- Token luu o sessionStorage (mat khi dong tab); gap 401 thi quay ve man hinh dang nhap kem thong bao.
- Hien mat khau tam 1 lan trong hop thong bao (nhan vien moi, tai khoan nguoi thue).
- Giao dien tieng Viet co dau, responsive, sang/toi theo he thong. Moi noi dung do nguoi dung nhap duoc dua vao trang bang
  `textContent` (chong XSS), khong dung innerHTML voi du lieu tu API.

## Khong lam
Dep nhu san pham that, quan ly phong/hop dong/hoa don (chua co API), Node/build, test giao dien tu dong (kiem tra bang `node --check`
va bang cac loi goi API da co kich ban chay that).

## Quyet dinh cua toi
1. GIAO DIEN CHI TREN MAY NAY: de trong thu muc `demo-ui/` o goc du an, them `demo-ui/` vao `.gitignore`, KHONG dua len GitHub.
   Phuc vu bang cau hinh nam trong `application-local.properties` (da gitignore): `spring.web.resources.static-locations=file:./demo-ui/`
   va `spring.mvc.static-path-pattern=/ui/**`. Mo `http://localhost:8080/ui/index.html`.
2. Phan DUY NHAT dua len repo: `SecurityConfig` cho phep cong khai `/ui/**` (neu khong, khi bat bao mat thi trang bi chan 401 truoc
   khi nguoi dung kip dang nhap). Co test. May khong co thu muc demo-ui thi `/ui/**` chi tra 404.
3. Chay that: bat `app.security.enabled=true` + cau hinh tren trong `application-local.properties`; app tro vao DB that
   `MiniBoardingHouseDB`; du lieu thu (nhan vien, khach thue thu) nam lai trong DB that, se liet ke de xoa.
4. Ban xem thu giu chay tren cong 8080 sau khi lam xong; huong dan tat.
5. Khong goi reviewer rieng cho giao dien (chi local, khong lo ra ngoai); phan commit duoc (SecurityConfig + test) tu ra soat.
