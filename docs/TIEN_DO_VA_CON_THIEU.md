# Tiến độ và những việc còn thiếu của cả nhóm

Cập nhật ngày 17/11/2026, đối chiếu từng use case trong file Excel phân công với code thực tế trên nhánh `main` (commit `05a16cf`), giao diện Stayly và bộ test (279 test). Đây là bản rà soát để cả nhóm biết còn thiếu gì, ai đẩy code mới thì tự sửa dòng của mình ở mục 3.

Cách đọc trạng thái:

- **Xong**: có cả backend (API) lẫn giao diện dùng được.
- **Một phần**: làm được một phần yêu cầu của use case, phần còn thiếu ghi ngay sau tên use case.
- **Chỉ có backend**: API đã có nhưng giao diện chưa có màn nào để dùng (giao diện do Phi làm, cần bàn giao với người phụ trách).
- **Chưa làm**: chưa có code.

## 1. Tổng hợp

| Thành viên | Được giao | Xong | Một phần | Chỉ có backend | Chưa làm | Giờ của các use case chưa làm |
|---|---|---|---|---|---|---|
| Nguyễn Anh Phi | 45 UC (239.8 giờ) | 45 | 0 | 0 | 0 | 0 |
| Nguyễn Trần Bảo Chi | 31 UC (160.7 giờ) | 6 | 5 | 12 | 8 | 36.1 |
| Phạm Khánh Toàn | 28 UC (160.6 giờ) | 14 | 1 | 9 | 4 | 20.5 |
| Vũ Văn Quốc Việt | 28 UC (161.9 giờ) | 13 | 1 | 11 | 3 | 14.8 |
| **Cả nhóm** | 132 UC | **78** | **7** | **32** | **15** | |

Ghi chú về Phi: 45 use case của Phi đều đã có code và giao diện. Các màn giao diện cho phần của Chi, Toàn, Việt cũng do Phi làm (xem mục 5).

## 2. Việc ưu tiên trước khi nộp

1. **Toàn**: gắn `@PreAuthorize` cho toàn bộ API của Toàn (xem mục 4, lỗi 1) và làm 4 use case chưa có (126 đến 129).
2. **Việt**: làm 3 use case chưa có (130, 131, 132).
3. **Chi**: làm 8 use case chưa có (14, 15, 16, 18, 24, 29, 31, 125), trong đó 18 (sửa phòng) và 31 (sửa hồ sơ khách) là hai thao tác cơ bản người dùng sẽ tìm ngay.
4. **Cả nhóm**: chốt các điểm trùng và mâu thuẫn ở mục 4 (thiết bị phòng, chấm dứt hợp đồng, phụ phí vào hóa đơn).
5. **Phi**: dựng các màn giao diện còn thiếu (mục 5), ưu tiên màn trả phòng và biên bản nhận phòng vì là luồng nghiệp vụ chính.

## 3. Chi tiết từng người

### Nguyễn Trần Bảo Chi

Xong 6, một phần 5, chỉ có backend 12, chưa làm 8.

**Chưa làm** (8):

- UC14 Cập nhật thông tin khu nhà trọ (tên, địa chỉ) & liên hệ hỗ trợ: chỉ có đọc danh sách khu (`GET /api/chi/properties`), chưa có API sửa tên, địa chỉ, SĐT hỗ trợ.
- UC15 Quản lý tầng của khu nhà trọ (thêm, sửa, xóa): chỉ đọc được tầng kèm khu trọ, chưa thêm, sửa, xóa tầng.
- UC16 Quản lý loại phòng & thiết lập giá thuê theo loại phòng: chưa có khái niệm "loại phòng" trong DB và code; nhóm cần chốt bỏ hay thêm bảng.
- UC18 Cập nhật phòng (mã, diện tích, sức chứa, giá thuê, tiền cọc): chỉ có thêm phòng và đổi trạng thái; chưa có API sửa mã, diện tích, giá thuê, tiền cọc (PUT).
- UC24 Quản lý danh mục thiết bị & nội thất chuẩn của khu trọ: chưa có danh mục thiết bị, nội thất chuẩn của khu trọ.
- UC29 Quản lý phòng chờ vệ sinh & xác nhận sẵn sàng cho thuê lại: chưa có trạng thái "chờ vệ sinh" và bước xác nhận sẵn sàng cho thuê lại.
- UC31 Cập nhật hồ sơ khách thuê & ngày bắt đầu / kết thúc cư trú: chưa có API sửa hồ sơ khách (PUT) và ngày bắt đầu, kết thúc cư trú.
- UC125 Xuất danh sách phòng & khách thuê đang ở ra Excel (lọc theo tầng, trạng thái): chưa có xuất danh sách phòng, khách ra Excel (khung `TableExporter` của Phi có sẵn để dùng).

**Một phần** (5):

- UC19 Tra cứu thông tin chi tiết phòng theo mã phòng: xem chi tiết theo ID (có trong giao diện), chưa tra theo mã phòng.
- UC20 Tìm kiếm & lọc phòng (tầng, diện tích, giá thuê, tình trạng sử dụng): lọc theo khu, tầng (API) và theo trạng thái (giao diện); chưa lọc theo diện tích, giá thuê.
- UC27 Quản lý công tơ điện & đồng hồ nước của phòng: mới có chỉ số điện, nước đầu của phòng (ghi khi nhận phòng); chưa quản lý thiết bị công tơ, đồng hồ.
- UC35 Tra cứu khách thuê (SĐT, CCCD) & lịch sử thuê phòng: tìm theo tên, SĐT, CCCD có; chưa có lịch sử thuê phòng của khách.
- UC37 Gửi yêu cầu thuê & giữ chỗ (chọn phòng, ngày thuê, số người ở): nhân viên tạo yêu cầu được; người thuê tự gửi yêu cầu thuê chưa có API và giao diện.

**Chỉ có backend** (12):

- UC22 Thêm hình ảnh phòng trọ (tải lên nhiều ảnh): API tải ảnh lên có, giao diện chưa có màn.
- UC23 Cập nhật & xóa hình ảnh phòng trọ: API đặt ảnh bìa, xóa ảnh có, giao diện chưa có màn.
- UC25 Ghi nhận trang thiết bị có sẵn trong phòng (thêm, sửa, xóa): API thêm, sửa, xóa nội thất có; giao diện mới chỉ hiển thị tiện nghi, chưa chỉnh sửa được.
- UC26 Ghi nhận tình trạng nội thất phòng: tình trạng nằm trong mục nội thất; giao diện chưa có.
- UC28 Cập nhật trạng thái phòng (trống / đang thuê / sửa chữa / ngưng): API đổi trạng thái có (khóa theo hợp đồng hiệu lực), giao diện chưa có nút.
- UC32 Quản lý CCCD (số, ảnh 2 mặt) & xác minh thông tin khách thuê: lưu ảnh CCCD bằng đường dẫn và xác minh có API; chưa có tải ảnh lên, chưa có giao diện.
- UC38 Xác nhận / từ chối yêu cầu thuê phòng (kèm lý do): API duyệt, từ chối có; giao diện chưa có màn.
- UC39 Xác nhận số tiền & ghi nhận thanh toán tiền đặt cọc: API ghi nhận tiền cọc của yêu cầu có; giao diện chưa có.
- UC40 Quản lý mẫu hợp đồng thuê phòng (thêm, sửa, xóa): API quản lý mẫu có; giao diện chưa có và chưa chọn mẫu khi lập hợp đồng.
- UC108 Quản lý yêu cầu sử dụng thêm / lắp đặt thiết bị trong phòng: API có nhưng trùng với phần của Việt (xem mục 4); giao diện chưa có.
- UC109 Quản lý phí tiện ích bổ sung (thiết bị điện, dịch vụ theo kỳ): API có nhưng phụ phí chưa được tính vào hóa đơn (xem mục 4); giao diện chưa có.
- UC124 Lập biên bản nhận phòng khi bắt đầu thuê (đối chiếu hiện trạng, thiết bị, chỉ số ban đầu, ảnh bàn giao): API biên bản nhận phòng có; giao diện chưa có màn.

**Xong** (6):

- UC17 Thêm phòng trọ mới (mã phòng, tầng, loại phòng, diện tích, sức chứa): có API và nút "Thêm phòng" trên giao diện.
- UC21 Xem sơ đồ phòng theo tầng & trạng thái từng phòng: sơ đồ phòng theo tầng, theo khu trọ đã chọn.
- UC30 Thêm hồ sơ khách thuê mới (cá nhân, liên hệ, cư trú, nghề nghiệp): có API và nút "Thêm khách thuê" trên giao diện.
- UC33 Quản lý người ở cùng phòng (thêm, sửa, xóa): Phi đã làm phần xem, thêm, sửa trên giao diện (cùng API của Chi).
- UC34 Quản lý phương tiện của khách thuê (biển số, loại xe): Phi đã làm phần xem, thêm, sửa xe trên giao diện.
- UC36 Người thuê xem thông tin phòng, hợp đồng & người ở cùng của mình: qua màn "của tôi" của người thuê (`/api/me/**`, Phi làm).

### Phạm Khánh Toàn

Xong 14, một phần 1, chỉ có backend 9, chưa làm 4.

**Chưa làm** (4):

- UC126 Hủy hóa đơn phát hành nhầm (chỉ khi chưa thu tiền, bắt buộc ghi lý do, lưu nhật ký): chưa có API hủy hóa đơn (trạng thái CANCELLED đã có trong DB nhưng chưa có nơi nào đặt).
- UC127 Báo cáo thu tiền theo ngày / theo người thu & đối soát cuối ngày (xuất Excel): chưa có báo cáo thu tiền theo ngày, theo người thu.
- UC128 Xem lịch sử thao tác trên hóa đơn & giao dịch (phát hành, điều chỉnh, hủy, đảo giao dịch): chưa có lịch sử thao tác trên hóa đơn và giao dịch.
- UC129 Xuất bảng kê hóa đơn & phiếu thu theo tháng ra Excel / PDF: chưa có xuất bảng kê hóa đơn, phiếu thu ra Excel, PDF (khung `TableExporter` của Phi có sẵn để dùng).

**Một phần** (1):

- UC92 Xử lý thanh toán dư: cấn trừ kỳ sau, hoàn tiền, hủy giao dịch sai: hủy giao dịch sai có giao diện; cấn trừ kỳ sau và hoàn tiền dư mới có API.

**Chỉ có backend** (9):

- UC55 Ghi nhận người chốt chỉ số & tải ảnh công tơ / đồng hồ nước: API tải ảnh công tơ có, giao diện chưa có.
- UC58 Tra cứu lịch sử tiêu thụ điện, nước của phòng: API lịch sử tiêu thụ của phòng có, giao diện chưa có.
- UC59 So sánh lượng điện, nước tiêu thụ giữa các tháng (biểu đồ): API so sánh giữa các tháng có, chưa vẽ biểu đồ.
- UC60 Ghi nhận chỉ số điện nước khi bắt đầu thuê & khi trả phòng: API ghi chỉ số khi nhận, trả phòng có, giao diện chưa có.
- UC64 Quản lý thời điểm áp dụng & lịch sử thay đổi đơn giá, bảng giá: API lịch sử thay đổi đơn giá có, giao diện chưa hiển thị.
- UC81 Gửi hóa đơn qua thông báo trên website & liên kết chia sẻ: API gửi thông báo trong app và link chia sẻ có, giao diện chưa có.
- UC89 Tạo phiếu thu (tiền phòng, tiền đặt cọc, dịch vụ phát sinh): API phiếu thu có, giao diện chưa có.
- UC90 Gửi xác nhận thanh toán, tải & in biên nhận: API gửi xác nhận, in biên nhận có, giao diện chưa có.
- UC91 Ghi nhận thanh toán một phần / nhiều lần cho một hóa đơn: API thanh toán một phần, nhiều lần có; giao diện thu tiền chưa dùng.

**Xong** (14):

- UC53 Tạo kỳ ghi nhận chỉ số điện nước: API và giao diện.
- UC54 Nhập chỉ số điện nước đầu / cuối kỳ theo phòng (tự tính tiêu thụ): API và giao diện.
- UC56 Xác nhận & điều chỉnh chỉ số điện nước đã nhập (kèm lý do): xác nhận và điều chỉnh chỉ số kèm lý do.
- UC57 Cảnh báo chỉ số điện / nước tăng đột biến: cảnh báo tăng đột biến.
- UC61 Thiết lập đơn giá điện (kWh) & nước (m³): đơn giá điện, nước.
- UC62 Thiết lập phí dịch vụ hàng tháng (vệ sinh, Internet, gửi xe, rác): phí dịch vụ hàng tháng.
- UC63 Thiết lập khoản phụ thu phát sinh & miễn giảm tiền phòng: phụ thu, miễn giảm theo phòng.
- UC76 Xem bảng tổng hợp tiền phải thu theo tháng & toàn khu: tổng hợp tiền phải thu theo tháng.
- UC78 Xem chi tiết hóa đơn (tiền phòng, điện, nước, dịch vụ) & tải / in PDF: xem chi tiết hóa đơn, in bằng trình duyệt; chưa có file PDF tạo từ server.
- UC79 Phát hành hóa đơn cho khách thuê & thông báo hóa đơn mới: phát hành hàng loạt.
- UC80 Gửi hóa đơn cho khách thuê qua email: gửi qua cổng email nhưng mới ghi log, chưa gửi email thật.
- UC82 Nhắc thanh toán trước hạn & thông báo hóa đơn quá hạn: job chạy 8h mỗi ngày: nhắc trước 3 ngày, 1 ngày và đánh dấu quá hạn.
- UC83 Thông báo khi hóa đơn được điều chỉnh: có thông báo khi sửa tổng hóa đơn.
- UC84 Tra cứu hóa đơn (mã, phòng, kỳ) & xem hóa đơn cần thanh toán: tra cứu theo mã, phòng, kỳ.

### Vũ Văn Quốc Việt

Xong 13, một phần 1, chỉ có backend 11, chưa làm 3.

**Chưa làm** (3):

- UC130 Gửi thông báo chung tới người thuê (toàn khu hoặc theo tầng) & theo dõi số người đã đọc: chưa có gửi thông báo chung cho cả khu hoặc theo tầng.
- UC131 Thống kê sự cố & lịch sử bảo trì theo phòng, theo nhóm sự cố (xuất Excel): chưa có thống kê sự cố, lịch sử bảo trì theo phòng, theo nhóm.
- UC132 Báo cáo hoàn tiền đặt cọc & quyết toán trả phòng theo tháng: chưa có báo cáo hoàn cọc, quyết toán trả phòng theo tháng.

**Một phần** (1):

- UC112 Gửi yêu cầu sửa chữa phòng trọ (kèm ảnh) & phân loại sự cố: người thuê gửi yêu cầu có; phần đính kèm ảnh chưa kiểm chứng.

**Chỉ có backend** (11):

- UC52 Tự động nhắc hợp đồng sắp hết hạn: job hằng ngày và API `/api/contracts/expiring` có, giao diện chưa có màn danh sách.
- UC101 Tiếp nhận yêu cầu trả phòng & xác nhận lịch kiểm tra phòng: API tiếp nhận, xác nhận yêu cầu trả phòng (cả của người thuê) có; giao diện chưa có.
- UC102 Ghi nhận hiện trạng phòng khi trả (ảnh chụp, thiết bị hư hỏng): API ghi nhận hiện trạng khi trả có; giao diện chưa có.
- UC103 Tính chi phí bồi thường tài sản hư hỏng: tính bồi thường nằm trong biên bản kiểm tra; giao diện chưa có.
- UC104 Quyết toán tiền phòng, dịch vụ & công nợ khi trả phòng: API quyết toán có; giao diện chưa có.
- UC105 Khấu trừ công nợ & chi phí hư hỏng vào tiền đặt cọc: khấu trừ vào tiền cọc nằm trong quyết toán; giao diện chưa có.
- UC106 Lập phiếu & ghi nhận thanh toán hoàn tiền đặt cọc: API phiếu hoàn cọc và ghi nhận hoàn tiền có; giao diện chưa có.
- UC107 Lập biên bản bàn giao phòng & xác nhận thanh lý hợp đồng: API biên bản bàn giao, thanh lý có; giao diện chưa có; trùng với cách chấm dứt hợp đồng của Phi (xem mục 4).
- UC111 Ghi nhận yêu cầu kiểm tra công tơ, đồng hồ nước & điều chỉnh hóa đơn: API điều chỉnh hóa đơn từ sự cố có; giao diện chưa có.
- UC113 Quản lý danh mục nhóm sự cố & nhóm thiết bị: API quản lý nhóm sự cố có; giao diện mới chỉ đọc danh sách.
- UC121 Quản lý mẫu thông báo & kênh gửi (email, website): API quản lý mẫu thông báo, xem trước có; giao diện chưa có.

**Xong** (13):

- UC93 Xác định số tiền còn nợ của từng phòng, từng khách thuê: công nợ từng phòng, từng khách.
- UC94 Người thuê xem lịch sử thanh toán & công nợ của mình: người thuê xem công nợ của mình.
- UC95 Cập nhật số dư công nợ, ghi nhận nợ phát sinh & chuyển kỳ sau: tính lại số dư công nợ.
- UC96 Tính số ngày quá hạn & tiền phạt chậm thanh toán: số ngày quá hạn và tiền phạt.
- UC97 Phân loại tình trạng thanh toán & khách thuê có công nợ kéo dài: phân loại tình trạng công nợ.
- UC98 Xem lịch sử, tổng công nợ toàn khu & xuất danh sách công nợ ra Excel: lịch sử, tổng công nợ, xuất Excel.
- UC99 Nhắc nợ tự động (lần 1, lần 2, cảnh báo quá hạn): nhắc nợ tự động, chạy tay được.
- UC100 Ghi nhận cam kết thanh toán, lý do chậm & lịch sử xử lý công nợ: cam kết thanh toán, lịch sử xử lý.
- UC110 Ghi nhận sự cố mất điện / nước & yêu cầu sửa chữa điện, nước: ghi nhận sự cố điện, nước qua màn Sửa chữa.
- UC114 Phân công xử lý, cập nhật tiến độ, chi phí & xác nhận hoàn tất: phân công, cập nhật tiến độ, hoàn tất.
- UC115 Đánh giá chất lượng xử lý sự cố: người thuê đánh giá sau khi sửa xong.
- UC117 Quản lý chi phí vận hành khu trọ (bảo trì, vệ sinh, thuế, ...): chi phí vận hành.
- UC123 Trung tâm thông báo của người dùng (xem, đánh dấu đã đọc, xóa): chuông thông báo: xem, đánh dấu đã đọc.

## 4. Lỗi, rủi ro và chỗ mâu thuẫn giữa các phần

Đã kiểm tra bằng cách đọc code và chạy thử. Mục nào ghi tên người thì người đó nên xử lý, mục ghi "cả nhóm" cần họp chốt trước.

1. **Toàn: API không có phân quyền.** Cả bốn controller của Toàn (hóa đơn, chỉ số điện nước, thu tiền, đơn giá) không có `@PreAuthorize`. Khi bật bảo mật, thu ngân hay nhân viên ghi số đều gọi được API đổi đơn giá, sửa tổng hóa đơn, hoàn tiền. Gợi ý: chủ trọ và quản lý cho đơn giá, sửa tổng, cấn trừ và hoàn tiền; thu ngân cho thu tiền và phiếu thu; nhân viên ghi số cho chỉ số điện nước. Cách viết xem `viet/controller/DebtController` hoặc `chi/controller/*`.
2. **Toàn: job tự bật.** `BillingJobs` chạy khi thiếu cấu hình (`matchIfMissing`). Khi chạy thử trên máy thì đặt `minihouse.scheduler.enabled=false`. Nên đổi mặc định thành tắt.
3. **Toàn và Việt: chưa có test.** Thư mục test chỉ có phần của Phi, của Chi và phần dùng chung. Tối thiểu nên có test cho các hàm tính toán (cộng dồn công nợ, tiền phạt, quyết toán, số dư thanh toán).
4. **Toàn: lỗi cũ chưa đối chiếu hết.** Xem `docs/LOI_CAN_SUA_CODE_TOAN.md`, nhiều mục chưa sửa.
5. **Chi và Việt cùng làm yêu cầu thiết bị phòng (UC108) và phí tiện ích (UC109).** Chi: `/api/chi/equipment-requests` (duyệt, từ chối, đổi trạng thái tùy ý) và `/api/chi/addon-fees`. Việt: `/api/equipment-requests` (duyệt, lắp, gỡ), `/api/room-addon-fees` và `/api/me/equipment-requests` cho người thuê. Hai bên ghi vào cùng bảng nhưng luồng trạng thái khác nhau, dùng lẫn sẽ ra dữ liệu lệch. **Cả nhóm cần chốt một bên làm chuẩn.** Đề xuất: giữ bản của Việt cho yêu cầu thiết bị (có phân quyền và có phía người thuê), Chi giữ phần quản lý phụ phí, bỏ phần trùng còn lại.
6. **Phụ phí tiện ích chưa vào hóa đơn.** `BillingService` không đọc bảng `room_addon_fees`, nên phụ phí ghi nhận nhưng không bao giờ được thu. Làm sau khi chốt mục 5 (người làm: Phi).
7. **Hai đường chấm dứt hợp đồng.** Phi: `POST /api/contracts/{id}/terminate` (UC51). Việt: `POST /api/checkout-requests/{id}/finalize` (UC107). Hai đường đổi trạng thái hợp đồng và phòng theo logic khác nhau. Đề xuất: trả phòng qua luồng của Việt (có kiểm tra phòng, quyết toán, hoàn cọc) là đường chuẩn, `terminate` của Phi chỉ dùng cho trường hợp đặc biệt, và hai bên gọi chung một hàm đổi trạng thái.
8. **Doanh thu và báo cáo bỏ sót một số khoản.** Báo cáo chỉ cộng giao dịch thành công trong `payment_transactions`, bỏ qua thanh toán một phần (`partial_payment_logs`), tiền dư cấn trừ kỳ sau và tiền cọc gạch nợ. Cần thống nhất nguồn số liệu giữa Phi và Toàn.
9. **Email, SMS, OTP chưa gửi thật.** `InvoiceMailGateway` của Toàn và `NotificationSender` chỉ ghi ra log (mã OTP cũng chỉ in ra log). Nếu thầy yêu cầu gửi thật thì cần tích hợp SMTP, SMS.
10. **Bảo mật mặc định tắt.** Cấu hình mặc định `app.security.enabled=false` để đồng đội làm song song. Trước khi demo hoặc nộp phải bật `true` và kiểm tra lại các vai trò.
11. **Phi đọc phòng, khách thuê qua `EntityManager`.** Phần hợp đồng, tính tiền của Phi chưa chuyển sang `RoomService`, `TenantService` của Chi. Chạy tốt, chỉ là hai nơi cùng đọc một dữ liệu.
12. **Bảng mới của Chi.** Bảng `checkin_inspections` đã thêm vào `db/group-7.sql`. DB đã tạo từ trước phải chạy `db/patch_02_checkin_inspections.sql`, nếu không app báo lỗi khi khởi động.
13. **Chi cần lấy lại `main`.** Nhánh `chi` của Chi có lịch sử Git tách rời `main`. Chi nên `git checkout main`, `git pull origin main` và làm tiếp trên nhánh mới từ `main`.

## 5. Màn hình giao diện còn thiếu

Giao diện do Phi làm. Các màn dưới đây đã có API, đang chờ làm màn hình (mỗi người có thể tự làm màn của mình nếu muốn, xem `frontend/src/pages/` làm mẫu).

- **Phần của Chi:** sửa, xóa phòng và đổi trạng thái phòng; ảnh phòng; chỉnh sửa nội thất; yêu cầu thuê và đặt cọc (duyệt, từ chối); mẫu hợp đồng và chọn mẫu khi lập hợp đồng; biên bản nhận phòng; yêu cầu thiết bị và phụ phí; tải ảnh CCCD.
- **Phần của Toàn:** ảnh công tơ; lịch sử tiêu thụ và biểu đồ so sánh các tháng; ghi chỉ số khi nhận, trả phòng; lịch sử đơn giá; phiếu thu và biên nhận; thanh toán một phần; cấn trừ và hoàn tiền dư; thông báo hóa đơn trong app và link chia sẻ.
- **Phần của Việt:** trả phòng (cho nhân viên và cho người thuê) gồm kiểm tra phòng, quyết toán, hoàn cọc, biên bản bàn giao; điều chỉnh hóa đơn từ sự cố; quản lý nhóm sự cố; mẫu thông báo; danh sách hợp đồng sắp hết hạn; yêu cầu thiết bị phía người thuê.

## 6. Cách kiểm tra lại

- Chạy toàn bộ test: `mvn test` (hiện 279 test, 0 lỗi).
- Sau khi kéo code của người khác về, luôn chạy thử app (`mvn spring-boot:run`) trước khi báo xong. Test đơn vị không bắt được lỗi trùng tên bean hay thiếu bảng, hai lỗi này đã xảy ra ba lần.
- Dữ liệu để thử nhiều: chạy `db/sample-data-day.sql` (3 khu trọ, 100 phòng, 100 khách thuê), xóa bằng `db/sample-data-day-xoa.sql`.
