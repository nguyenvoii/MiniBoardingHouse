MINIBOARDINGHOUSE - CAC PHAN CHUA HOAN TOAN HOAN THIEN (cap nhat 27/11/2026)
=========================================================================

Tong quan: 123/123 use case da co giao dien + API + bang chung E2E tren SQL Server QA (458 JUnit dat, 19 kich ban
Playwright trong audit/e2e). Cac muc duoi day la phan CHUA that su dung duoc voi dich vu that, hoac con gioi han,
de lam not trong tuong lai. Khong co loi P0/P1 da biet.

A. DICH VU GIA / CHUA CO KHOA API NGOAI (can ban cung cap khoa roi cau hinh)
-----------------------------------------------------------------------------
1. UC86 - Thanh toan payOS
   - Hien trang: da noi giao dien khach ("Thanh toan online (payOS)"), API tao giao dich GATEWAY, goi payOS, doi soat.
     Thu toan luong voi payOS GIA chay HTTPS cuc bo: audit/e2e/fake-payos.mjs + payos-wiring-flow.mjs.
   - Con thieu: khoa that. Cau hinh trong application-local.properties:
       app.payment.payos.enabled=true
       app.payment.payos.client-id=...      app.payment.payos.api-key=...      app.payment.payos.checksum-key=...
       app.payment.payos.return-url=https://<ten-mien>/#/me/invoices   (bat buoc https)
       app.payment.payos.cancel-url=https://<ten-mien>/#/me/invoices
     (app.payment.payos.base-url va checkout-host chi doi khi thu nghiem, mac dinh la payOS that)
   - Can lam them khi co khoa: thu thanh toan that 1 hoa don nho; xem xet dung webhook payOS thay cho nut "Kiem tra";
     ky bi mat callback app.payment.callback-secret cho POST /api/payments/callback (doi soat ngan hang tu dong).
2. UC80, UC1 - Gui email that (hoa don, ma xac thuc, nhac no)
   - Hien trang: code SMTP da co (SmtpNotificationSender, SmtpInvoiceMailGateway). Chua co nha cung cap that.
     Da thu bang may chu nhan thu gia cuc bo: audit/e2e/smtp-sink.mjs + email-smtp-flow.mjs.
   - Con thieu: tai khoan SMTP. Cau hinh: app.mail.enabled=true, spring.mail.host/port/username/password,
     spring.mail.properties.mail.smtp.auth/starttls, app.mail.from, app.public-base-url=https://<ten-mien>
     (de duong link trong thu tro dung trang xem hoa don #/share/<token>).
   - Mac dinh (khong cau hinh) he thong dung bo gui GHI LOG: ma OTP/mat khau tam chi hien trong log backend.
3. UC2 - SMS OTP (xac thuc so dien thoai)
   - Hien trang: TwilioSmsSender co code, chua co tai khoan. QA lay ma tu log.
   - Con thieu: app.sms.enabled=true, app.sms.twilio.account-sid, app.sms.twilio.auth-token, app.sms.twilio.from.
     Luu y noi dung SMS hien khong dau (de vua 1 tin).
4. UC122 - Phuc hoi du lieu
   - Hien trang: chay that tren SQL Server QA (restore-flow.mjs) SAU KHI cap quyen dbcreator cho mbh_app.
   - Con thieu: tren moi truong that can tai khoan SQL du quyen restore, va quy trinh van hanh (thong bao dung he thong,
     sao luu truoc khi phuc hoi). Hoan tac quyen o QA: ALTER SERVER ROLE dbcreator DROP MEMBER mbh_app;
     Thu muc luu file .bak la thu muc mac dinh cua SQL Server (khong tu xoa ban cu, chua co chinh sach giu bao nhieu ban).

B. CAU HINH CAN BAT KHI CHAY THAT
---------------------------------
- app.security.enabled=true (mac dinh false de dev). QA/E2E da chay voi true.
- app.jwt.secret: dat chuoi bi mat rieng >= 32 ky tu (gia tri mac dinh la dev-only).
- Tac vu tu dong (nhac han, nhac no, het han hop dong, chot chi so, tinh nhap, phat hanh): chi tu chay khi
  app.jobs.enabled=true (hoac minihouse.scheduler.enabled=true). Gio chay sua tren Cai dat > Lich tu dong.
  Hai tac vu "tinh nhap" va "phat hanh hoa don" mac dinh TAT (cron "-"), bat co chu dich.
- Ap dung db/patch_11 ... patch_16 len CSDL that (patch_16 = bang job_settings). Khong co migration pha huy.
- Mat khau tai khoan seed admin@minihouse.local / Admin@123: doi ngay.

C. GIOI HAN THIET KE / VIEC NEN LAM TIEP
-----------------------------------------
- Chi phi van hanh (OperatingExpense) chua gan khu tro: khoan "Dien nuoc chung" chi chia cho khu mac dinh (khu 1).
- Anh chu ky hop dong (/api/contracts/signatures/<uuid>) cong khai theo ten UUID ngau nhien; nen siet bang dang nhap
  (can doi giao dien hien anh bang blob co token nhu anh CCCD).
- Canh bao hoa don bat thuong so voi 150% trung binh TONG tien (tien phong chiem phan lon nen can tang rat manh moi bao).
  Nen tinh theo dien/nuoc rieng.
- Nhac no: moi dot qua han chi nhac 1 lan moi muc (lan 1, lan 2, canh bao); chua co lich nhac lap lai cho no keo dai.
- Du lieu cu chi so chuyen phong truoc patch_14 khong tu gan hop dong dong (patch_14 khong suy luan du lieu cu).
- Gui nhac/thong bao cho khach chi qua trong ung dung + email; chua co kenh SMS cho nhac no/hoa don.
- UC59: bieu do tieu thu dang cot don gian (the meter), chua co bieu do duong/so sanh nhieu phong.
- Phan trang: man Cong no nap tat ca trang (toi da 6000 dong); cac man khac van gioi han 50-200 dong moi lan.
- Phu hoi du lieu tu giao dien khong co "dry run"/kiem tra dung luong dia truoc.

   Tu ra soat code (27/11):
   - Nhac no chi ghi lich su (DebtReminder) khi gui duoc vao tai khoan cong khach; khach chua co tai khoan khong co dong lich su.
   - BillingJobs.remindAndMarkOverdue/closeExpiredMeterPeriods dung LocalDate.now() thay vi Clock (anh huong test theo gio).
   - confirm-cleaned chi nhan phong CLEANING; phong REPAIRING doi trang thai bang "Doi trang thai".
   - GET man hinh chi so/ky ghi chi so nay can vai tro quan ly/chu tro/nguoi ghi chi so (thu ngan khong doc duoc).

D. MOI TRUONG KIEM THU (khong anh huong chay that)
--------------------------------------------------
- May khong co Maven: bien dich bang javac (target/start-qa.sh, target/qa-classes), JUnit bang
  junit-platform-console-standalone. Nen dung Maven khi co mang/cai dat.
- audit/e2e/billing-flow.mjs dung thang qua khu 1 (2025-01..2026-05) vi chi phi khong ghi duoc o tuong lai: con khoang 15 lan chay.
  Het thi can CSDL QA moi. DB QA da tich luy nhieu du lieu tong hop E2E (phong/khach/hop dong/ky thang 2029-2050).
- Mot so kich ban phu thuoc gio may (nhac han tinh theo ngay): chay gan 00:00 theo gio dia phuong co the lech 1 ngay.
- Cac may chu gia (payOS gia HTTPS tu ky + may chu SMTP nhan thu) chi de kiem thu: fake-payos.mjs, smtp-sink.mjs.
  Chay payOS gia can truststore rieng (target/payos/trust.p12) va NODE_TLS_REJECT_UNAUTHORIZED=0 cho script Node.
- Bang nghiem thu docs/NGHIEM_THU_123_USE_CASE.xlsx chua duoc cap nhat vi bi Excel khoa luc lam:
  ket qua nam o docs/KET_QUA_E2E_NGHIEM_THU.xlsx; dong Excel roi chay: python docs/merge_nghiem_thu_e2e.py
