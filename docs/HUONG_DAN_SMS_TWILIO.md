HUONG DAN SMS TWILIO (MA OTP GUI QUA TIN NHAN)

CACH HOAT DONG
- Man hinh Quen mat khau: nguoi dung nhap SO DIEN THOAI thi ma 6 so gui bang SMS (Twilio); nhap EMAIL thi gui email (Brevo) nhu cu.
- Ma co hieu luc 10 phut, sai toi da 5 lan thi huy, xin lai cach nhau 60 giay. Nhap ma dung o buoc 2 roi moi dat mat khau o buoc 3.
- Xac thuc so dien thoai cua tai khoan (UC2) cung gui SMS qua Twilio khi bat.
- Tai khoan khong co email thi luon gui SMS.
- Noi dung SMS ngan, khong dau (1 tin duoi 160 ky tu) de khong ton nhieu tin.
- SMS loi (het tien, so sai...) thi app khong bao loi chi tiet ra ngoai (tranh lo tai khoan ton tai), chi ghi log; nguoi dung co the dung email.
- Mac dinh TAT. Tat thi ma OTP khong gui di dau, chi in ra log may chu khi app.notification.log-otp=true (dung de thu).

BUOC 1. TREN TWILIO (https://console.twilio.com)
1. Tao tai khoan. Trang chu console co Account SID (dang AC...) va Auth Token. Auth Token la bi mat, khong gui vao chat, khong commit.
2. Co so gui (From): mot so dien thoai Twilio (hoac Messaging Service / ma gui chu duoc dang ky). Dien vao app.sms.twilio.from, vi du +1xxxxxxxxxx.
3. Bat quyen gui SMS toi Viet Nam: Messaging, Settings, Geo permissions, tick Vietnam.
4. Tai khoan dung thu (trial) thuong chi gui duoc toi so da xac minh (Verified Caller IDs) va tin co tien to "Sent from your Twilio trial account". Nang cap tai khoan de gui toi moi so.
5. Moi SMS bi tinh phi theo bang gia cua Twilio (xem trang Pricing, SMS, Vietnam). App chi gui khi nguoi dung yeu cau.

BUOC 2. CAU HINH CHAY MAY MINH (src/main/resources/application-local.properties)
  app.sms.enabled=true
  app.sms.twilio.account-sid=<Account SID>
  app.sms.twilio.auth-token=<Auth Token>
  app.sms.twilio.from=<so gui, vd +1xxxxxxxxxx>
  app.otp.sms-on-phone=true

BUOC 3. CAU HINH TREN SERVER (CapRover, App Configs, Environment Variables)
  APP_SMS_ENABLED=true
  APP_SMS_TWILIO_ACCOUNT_SID=<Account SID>
  APP_SMS_TWILIO_AUTH_TOKEN=<Auth Token>
  APP_SMS_TWILIO_FROM=<so gui>
  APP_OTP_SMS_ON_PHONE=true
Bat app.sms.enabled=true ma thieu 3 gia tri twilio thi app khong khoi dong (de khoi chay nham).

THU
- Quen mat khau, nhap so dien thoai cua tai khoan (dung dung dang da luu, vd 0901234567), bam Gui ma. So dang 0xxxxxxxxx tu doi thanh +84xxxxxxxxx.
- Nhan SMS co ma 6 so, nhap ma, Tiep tuc, roi dat mat khau moi.

GHI CHU
- Dich vu OTP qua Zalo (eOTP) da ngung dung, xem docs/HUONG_DAN_EOTP.md (code van con nhung tat, khong anh huong).
