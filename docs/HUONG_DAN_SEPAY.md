HUONG DAN NOI SEPAY (TIEN CHUYEN KHOAN VE LA TU GACH HOA DON)

CACH HOAT DONG
1. Khach (hoac nhan vien) bam tao QR cho hoa don. App tra QR dong cua SePay, da co san so tien va noi dung chuyen khoan la ma hoa don (vd BA202611-201).
2. Khach quet QR va chuyen khoan.
3. SePay thay tien vao tai khoan nen goi vao app: POST /api/payments/sepay-webhook, kem khoa trong header Authorization: Apikey <khoa>.
4. App tim ma hoa don trong noi dung (bo qua dau gach, chu hoa chu thuong, vi nhieu ngan hang xoa dau "-"), neu dung so tien thi hoa don chuyen sang DA THANH TOAN.
Moi giao dich SePay chi xu ly mot lan (ma SEPAY-<id>), SePay goi lai cung id thi bo qua.

APP KHONG TU GACH, CHI GHI NHAT KY SEPAY_UNMATCHED DE NHAN VIEN XU LY, KHI:
- so tien lech so con phai tra (chua co giao dich cho cung so tien),
- noi dung khong co ma hoa don nao, hoac co nhieu ma cung do dai,
- hoa don da thanh toan hoac da huy (can hoan tien cho khach).
Tien chuyen RA khoi tai khoan thi bo qua.

BUOC 1. TAO KHOA (chi ban biet, khong gui vao chat, khong commit)
Mo PowerShell, chay:
  -join ((1..32) | % { '{0:x2}' -f (Get-Random -Max 256) })
Ket qua la chuoi 64 ky tu. Dung CUNG MOT khoa o buoc 2 va 3.

BUOC 2. CAU HINH TREN SEPAY (my.sepay.vn, muc Tich hop Webhooks, Them webhooks)
- Su kien: Co tien vao.
- Tai khoan ngan hang: chon tai khoan nhan tien cua khu tro.
- Goi den URL: https://stayly.io.vn/api/payments/sepay-webhook
- Kieu chung thuc: API Key, gia tri la khoa o buoc 1.
- Kieu noi dung: application/json.
- KHONG bat tuy chon "bo qua neu noi dung khong co ma thanh toan" (app tu tim ma hoa don).
Ten muc tren giao dien SePay co the khac mot chut so voi mo ta nay.

BUOC 3. CAU HINH TREN SERVER (CapRover, App Configs, Environment Variables)
  APP_PAYMENT_SEPAY_ENABLED=true
  APP_PAYMENT_SEPAY_API_KEY=<khoa o buoc 1>
Chay may minh thi them vao src/main/resources/application-local.properties:
  app.payment.sepay.enabled=true
  app.payment.sepay.api-key=<khoa>

BUOC 4. KIEM TRA THONG TIN NGAN HANG TRONG APP (Cai dat, Ngan hang)
- So tai khoan phai dung tai khoan da lien ket SePay.
- Ten ngan hang phai la ten ngan trong https://qr.sepay.vn/banks.json (Vietcombank, MBBank, BIDV, VietinBank...). Khoang trang tu bo ("MB Bank" thanh "MBBank").

THU KHONG CAN TIEN THAT (thay <khoa>, <ma hoa don>, <so tien>)
  curl -X POST https://stayly.io.vn/api/payments/sepay-webhook -H "Content-Type: application/json" -H "Authorization: Apikey <khoa>" -d "{\"id\":1,\"content\":\"<ma hoa don>\",\"transferType\":\"in\",\"transferAmount\":<so tien>}"
Chi lam khi dung hoa don that va so tien that, vi hoa don se duoc gach.

CHI DEPLOY BAN CO SEPAY SAU KHI: da dat APP_JWT_SECRET, da chay db/patch_all.sql tren DB server (xem docs/HUONG_DAN_DEPLOY_CAPROVER.md). Truoc do webhook tren production chua ton tai.
