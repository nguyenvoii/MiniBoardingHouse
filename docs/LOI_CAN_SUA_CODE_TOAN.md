DANH SÁCH VIỆC CẦN SỬA CHO CODE CỦA TOÀN (review file MiniBoardingHouse-Toan.zip)

Người review: Phi, ngày 2026-11-16. Mình đã đọc code, chưa chạy. Đọc kỹ rồi làm theo thứ tự từ trên xuống. Cách đẩy code lên git xem file HUONG_DAN_GIT.md.

Điểm tốt cần giữ: dùng BigDecimal cho tiền, link chia sẻ hóa đơn dùng UUID, có hàm esc() chống XSS khi in biên nhận, có DynamicQuery cho tìm kiếm, job nhắc hạn cấu hình được, có InvoiceMailGateway tách phần gửi mail.


PHẦN A. BẮT BUỘC (không làm thì không ghép được vào main)

A1. Đưa code vào đúng cấu trúc package.
   Zip đang theo cấu trúc cũ: com.group7.minihouse.controller, service, repository, dto, entity... Main hiện tại tách như sau:
   - shared: dùng chung (entity, exception, security, config, dto ApiResponse/PageResponse, util)
   - phi, chi, toan, viet: mỗi người một package, trong đó có 4 thư mục controller, service, repository, dto
   Việc cần làm:
   - Chuyển code của Toàn vào com.group7.minihouse.toan.{controller,service,repository,dto}.
   - XÓA toàn bộ 56 file entity trong zip. Entity đã có sẵn ở com.group7.minihouse.shared.entity (bản trong main có thêm @DynamicInsert). Chỉ cần đổi import thành com.group7.minihouse.shared.entity.TênEntity.
   - Scheduler (BillingJobs, SchedulerConfig) đặt trong package toan.scheduler hoặc toan.service tùy bạn, miễn là nằm trong package toan.
   - Không đụng vào pom.xml và application.properties của main. Chỉ thêm vào application.properties hai dòng của bạn:
       minihouse.scheduler.enabled=true
       minihouse.scheduler.meter-grace-days=7
   - Không copy lại db/group-7.sql, MiniBoardingHouseApplication, package-info.java của zip.
   Quy tắc phụ thuộc: package toan được gọi shared và chi, KHÔNG được gọi phi hay viet (xem `docs/HUONG_DAN_LAP_TRINH.md`, mục Cấu trúc code, quy tắc phụ thuộc một chiều).

A2. Đổi cách báo lỗi sang khung chung.
   Code đang ném ResponseStatusException (13 chỗ). GlobalExceptionHandler của main chưa bắt loại này nên rất có thể lỗi 400/404/409 sẽ biến thành 500. Hãy đổi:
   - Không tìm thấy: throw BusinessException.notFound("Hóa đơn")
   - Lỗi khác: throw new BusinessException(HttpStatus.BAD_REQUEST, "thông báo")  (hoặc CONFLICT, v.v.)
   - Controller trả về ApiResponse.ok("thông báo", data) thay vì trả thẳng object, để mọi API của nhóm cùng định dạng.
   - Thông báo lỗi viết tiếng Việt CÓ DẤU.
   - Thêm @Valid cho các request body và ràng buộc (@NotNull, @NotBlank, @Positive...) trong DTO.

A3. Thêm bảo mật.
   Hiện không có @PreAuthorize nào. Xem docs/thiet-ke/dang-nhap-jwt.md để biết cách dùng. Việc cần làm:
   - Gắn @PreAuthorize cho từng API theo vai trò. Gợi ý: chi phí vận hành, đơn giá, điều chỉnh chỉ số, hủy/đảo giao dịch: LANDLORD, MANAGER. Ghi chỉ số: thêm METER_READER. Thu tiền, phiếu thu, thanh toán từng phần: thêm CASHIER.
   - API dành cho người thuê (xem hóa đơn, công nợ của chính mình) phải nằm dưới /api/me/** và lấy tenantId bằng CurrentUser.requireTenantId(user), KHÔNG nhận tenantId từ client.
   - Link chia sẻ hóa đơn GET /api/invoices/share/{token}: khi bật app.security.enabled=true sẽ bị chặn vì mặc định chỉ nhân viên vào được. Nếu muốn khách mở được không cần đăng nhập thì phải thêm vào danh sách công khai trong SecurityConfig (báo Phi, vì SecurityConfig nằm ở shared). Nhớ rằng ai có link là xem được hóa đơn, nên chỉ trả những thông tin cần thiết.


PHẦN B. LỖI NGHIỆP VỤ (quan trọng vì liên quan đến tiền)

B1. Sinh mã bằng "đếm + 1" bị trùng khi có 2 request cùng lúc.
   Chỗ có lỗi: ReceiptService.nextCode, sinh mã hóa đơn (countByInvoiceCodeStartingWith), PartialPaymentService (installmentNumber = count + 1).
   Cách sửa: bắt DataIntegrityViolationException rồi thử sinh lại, hoặc dùng SEQUENCE của SQL Server, hoặc khóa dòng khi sinh mã. Nhớ cột mã phải có unique để DB chặn trùng.

B2. Thanh toán không có khóa cạnh tranh (PartialPaymentService.record).
   Code đọc paid, cộng thêm rồi ghi lại. Hai thu ngân bấm cùng lúc sẽ đè lên nhau và mất một khoản tiền.
   Cách sửa: khóa hóa đơn khi đọc (@Lock(LockModeType.PESSIMISTIC_WRITE) trên truy vấn findById của InvoiceRepository), hoặc thêm @Version cho Invoice. Áp dụng cho mọi chỗ cập nhật paidAmount: thanh toán từng phần, thanh toán dư, cấn trừ tạm ứng, đảo giao dịch.

B3. PartialPaymentService.update không kiểm tra hóa đơn đã CANCELLED. Cần chặn sửa đợt thanh toán của hóa đơn đã hủy.

B4. ReceiptService.create:
   - Cho tạo phiếu thu không gắn giao dịch (transactionId = null) và số tiền tùy ý, tức là lập được phiếu thu "ma".
   - Cần quy định: phiếu thu loại MONTHLY_RENT phải gắn giao dịch hợp lệ và amount phải khớp (hoặc không vượt quá) số tiền giao dịch.

B5. Rà lại các chỗ cập nhật trạng thái hóa đơn: tập trung qua InvoiceBalanceCalculator (đang làm tốt), đảm bảo không chỗ nào tự set trạng thái riêng.


PHẦN C. KIỂM THỬ

C1. Chưa có test nào (không có thư mục src/test). Tối thiểu cần unit test cho:
   - InvoiceBalanceCalculator (đủ các trạng thái UNPAID, PARTIALLY_PAID, PAID, OVERDUE, CANCELLED)
   - Tính tiêu thụ điện nước (chỉ số cuối nhỏ hơn chỉ số đầu, ghi chỉ số trùng kỳ)
   - Thanh toán từng phần: vượt số còn phải trả, hóa đơn đã trả đủ, hóa đơn đã hủy
   - Sinh mã phiếu thu / hóa đơn
   Xem các test mẫu trong src/test/java/com/group7/minihouse/phi/service (dùng Mockito, không cần DB).
C2. Chạy thử API thật trên DB tạm: tạo kỳ ghi chỉ số, ghi chỉ số, tạo hóa đơn, thu tiền từng phần, thu đủ, đảo giao dịch.


PHẦN D. NHỎ

D1. Thông báo lỗi, log và comment đang không dấu, viết lại có dấu.
D2. Ảnh đồng hồ hiện chỉ nhận URL dạng chuỗi (photoUrl). Nếu cần upload thật thì báo Phi, sẽ có util dùng chung.
D3. Đừng đẩy file zip lên GitHub nữa. Đẩy code bằng lệnh git theo HUONG_DAN_GIT.md.


THỨ TỰ LÀM ĐỀ XUẤT

1. A1 (chuyển package, xóa entity bản sao) cho build được.
2. A2 (BusinessException, ApiResponse).
3. A3 (bảo mật).
4. B1, B2, B3, B4.
5. C1, C2.
Mỗi mục xong có thể mở một Pull Request riêng cho dễ review.
