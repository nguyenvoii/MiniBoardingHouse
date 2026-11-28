DA NGUNG DUNG (2026-11-28): chuyen sang gui ma bang SMS Twilio, xem docs/HUONG_DAN_SMS_TWILIO.md.
Code eOTP van con trong repo nhung mac dinh TAT (app.otp.provider=local) nen khong anh huong gi; co the xoa sau.

HUONG DAN NOI EOTP (MA OTP GUI QUA ZALO)

CACH HOAT DONG
- Nguoi dung o man hinh Quen mat khau nhap SO DIEN THOAI: app goi eOTP, eOTP tu sinh ma 6 so va gui qua Zalo. Nhap EMAIL thi van gui email nhu cu (Brevo).
- eOTP tu kiem ma. Sai 5 lan eOTP huy ma, ma het han sau vai phut (cau hinh trong du an eOTP, mac dinh 5 phut).
- Moi ma chi xac thuc duoc 1 lan. Vi vay sau khi nhap ma dung o buoc 2, app giu "giay phep" 10 phut de buoc 3 (dat mat khau moi) khong phai hoi eOTP lan nua.
- Xac thuc so dien thoai (UC2) cung dung eOTP khi bat.
- Mac dinh TAT (app.otp.provider=local). Bat bang app.otp.provider=eotp.

BUOC 1. TREN EOTP (https://eotp.com.vn/app/register)
1. Tao tai khoan, tao du an.
2. Xac minh so huu ten mien stayly.io.vn (them ban ghi DNS TXT hoac dat file theo huong dan o trang du an) va cho du an duoc duyet.
3. Kenh gui mac dinh cua du an chon Zalo (goi FREE chi gui duoc mot kenh).
4. Bat CHE DO THU o tab "API key & tich hop": gui toi so thu +84 901 888 484 khong gui tin that va khong tru tien, verify bang ma 000000.
5. Lay API Key (dang eo_live_...) va API Secret (dang eo_sec_..., CHI HIEN 1 LAN, copy luon). Khong gui khoa vao chat, khong commit.
Goi FREE: tang 100 OTP trong 30 ngay, toi da 20 OTP/ngay. Het thi tru vi (Zalo 1.200d/tin, SMS 2.000d/tin).

BUOC 2. CAU HINH CHAY MAY MINH (src/main/resources/application-local.properties)
  app.otp.provider=eotp
  app.otp.eotp.api-key=<API Key>
  app.otp.eotp.api-secret=<API Secret>
Khong dat khoa thi app khong khoi dong khi bat eotp (de khoi chay nham ma khong gui duoc).

BUOC 3. CAU HINH TREN SERVER (CapRover, App Configs, Environment Variables)
  APP_OTP_PROVIDER=eotp
  APP_OTP_EOTP_API_KEY=<API Key>
  APP_OTP_EOTP_API_SECRET=<API Secret>

THU O LOCAL (che do thu cua eOTP)
- Tao hoac sua mot tai khoan co so dien thoai 0901888484.
- Quen mat khau, nhap 0901888484, bam Gui ma. eOTP khong gui tin that.
- Nhap ma 000000, bam Tiep tuc, roi dat mat khau moi.
- Muon thu gui that: dung so dien thoai Zalo cua ban (se tru 1 tin trong 100 tin tang).

GIOI HAN CAN BIET
- Ma gui qua Zalo chi khi nguoi dung nhap dung SO DIEN THOAI dang dung cua tai khoan. Tai khoan khong co SDT thi dung email.
- request_id giu trong bo nho app: khoi dong lai app giua luc dang cho ma thi nguoi dung xin ma moi (cach nhau 60 giay).
- Neu eOTP loi hoac het tien, app khong bao loi chi tiet ra ngoai (tranh lo tai khoan ton tai), chi ghi log; nguoi dung co the dung email.
- Twilio SMS cu van con trong code nhung nen de tat (app.sms.enabled=false).

GUI DUYET DU AN TREN EOTP (neu bi tu choi)
eOTP duyet tu dong, yeu cau trang web phai cong khai du 4 thu sau. Stayly da co san, chi can cau hinh thong tin lien he:
1. Ai chiu trach nhiem (ten + lien he): hien o chan trang chu va moi trang phap ly. Dat tren server (CapRover, Environment Variables):
     APP_PUBLIC_OWNER_NAME=<ten don vi/ca nhan chiu trach nhiem>
     APP_PUBLIC_OWNER_EMAIL=<email lien he>
     APP_PUBLIC_OWNER_PHONE=<so dien thoai lien he>
     APP_PUBLIC_OWNER_ADDRESS=<dia chi, tuy chon>
   Khong ghi gia tri that vao file trong repo (repo cong khai).
2. Bang gia va hoan tien: https://stayly.io.vn/pricing (hien ghi Mien phi; sua noi dung o src/main/resources/public-pages/pricing.html neu doi chinh sach).
3. Chinh sach bao mat: https://stayly.io.vn/privacy  va  Dieu khoan: https://stayly.io.vn/terms (la van ban that, co noi ro OTP qua email Brevo hoac Zalo eOTP).
4. Mo ta du an (dan vao o Project description roi Resubmit):
   Stayly (stayly.io.vn) la phan mem quan ly nha tro cho chu tro va nguoi thue: quan ly phong, khach thue, hop dong, ghi dien nuoc, tinh tien, xuat hoa don va doi soat tien thue. OTP duoc dung o hai buoc: (1) Quen mat khau: nguoi dung nhap so dien thoai da dang ky, he thong gui ma OTP 6 so qua Zalo de xac nhan danh tinh roi cho dat mat khau moi; (2) Xac thuc so dien thoai cua tai khoan sau khi dang nhap. Ma chi gui khi nguoi dung chu dong yeu cau, co hieu luc vai phut, sai toi da 5 lan, khong dung cho quang cao. Dich vu hien mien phi. Chinh sach bao mat, dieu khoan, bang gia va thong tin lien he cong khai tai stayly.io.vn/privacy, /terms, /pricing, /contact.
Chi bam Resubmit SAU KHI ban co trang chu moi da deploy len stayly.io.vn va da dat 3-4 bien APP_PUBLIC_OWNER_* (eOTP chi con 2 lan duyet tu dong).
