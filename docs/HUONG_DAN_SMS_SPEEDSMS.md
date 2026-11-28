HUONG DAN SMS SPEEDSMS (MA OTP GUI QUA TIN NHAN)

CACH HOAT DONG
- Man hinh Quen mat khau: nguoi dung nhap SO DIEN THOAI thi ma 6 so gui bang SMS (SpeedSMS); nhap EMAIL thi gui email (Brevo) nhu cu.
- App TU SINH ma (nhu email): hieu luc 10 phut, sai toi da 5 lan thi huy, xin lai cach nhau 60 giay. Nhap ma dung o buoc 2 roi moi dat mat khau o buoc 3.
- Xac thuc so dien thoai cua tai khoan (UC2) cung gui SMS qua SpeedSMS khi bat.
- Tai khoan khong co email thi luon gui SMS.
- Noi dung SMS ngan, khong dau (1 tin duoi 160 ky tu).
- SMS loi (het tien, so sai...) thi app khong bao loi chi tiet ra ngoai (tranh lo tai khoan ton tai), chi ghi log; nguoi dung co the dung email.
- Mac dinh TAT. Tat thi ma OTP khong gui di dau, chi in ra log may chu khi app.notification.log-otp=true (dung de thu).

BUOC 1. TREN SPEEDSMS (https://connect.speedsms.vn)
1. Dang ky tai khoan (da co). Vi tien hien thi o dau trang (Nap tien de them).
2. Lay ACCESS TOKEN (thuong o Settings, Profile hoac Quan ly tai khoan; tim dong "Access token"). Day la bi mat, khong gui vao chat, khong commit.
3. Gui OTP bang brandname co san "Verify" (type 4): khong can dang ky brandname voi nha mang, khong phi dang ky hay duy tri.
4. Bang gia xem o menu Quan ly tai khoan, Bang gia. Moi SMS tru tien tu vi.
Neu dung brandname rieng (type 3) thi phai dang ky va dien ten vao app.sms.speedsms.sender.

BUOC 2. CAU HINH CHAY MAY MINH (src/main/resources/application-local.properties)
  app.sms.speedsms.enabled=true
  app.sms.speedsms.access-token=<Access token>
  app.otp.sms-on-phone=true
Co the them app.sms.speedsms.type (mac dinh 4) va app.sms.speedsms.sender (chi can voi type 3).
Bat enabled=true ma thieu access-token thi app khong khoi dong (de khoi chay nham).

BUOC 3. CAU HINH TREN SERVER (CapRover, App Configs, Environment Variables)
  APP_SMS_SPEEDSMS_ENABLED=true
  APP_SMS_SPEEDSMS_ACCESS_TOKEN=<Access token>
  APP_OTP_SMS_ON_PHONE=true

THU
- Quen mat khau, nhap so dien thoai cua tai khoan (dung dang da luu, vd 0901234567), bam Gui ma.
- So dang 0xxxxxxxxx tu doi thanh 84xxxxxxxxx khi goi SpeedSMS.
- Nhan SMS co ma 6 so, nhap ma, Tiep tuc, roi dat mat khau moi.
- Xem ket qua gui o SpeedSMS: menu SMS, Bao cao.

LUU Y
- Theo tai lieu cua SpeedSMS, access-token nam trong URL goi API; app khong bao gio ghi URL ra log.
- Neu SpeedSMS bao loi noi dung (ma 110/113) hay tu choi mau tin cua Verify thi doi noi dung trong OtpMail.recoverySms.
- Twilio van con trong code nhung tat; tai khoan Twilio dung thu khong gui duoc noi dung tu do (chi mau co san).
