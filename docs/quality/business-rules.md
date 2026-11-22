# Confirmed Business Rules

Chỉ ghi các quy tắc có bằng chứng từ code, SQL, tài liệu nghiệp vụ hoặc hành vi đã kiểm chứng. Mục `Cần xác nhận` không được tự ý triển khai thành nghiệp vụ mới.

## Nhận khách thuê phòng

- Luồng nhận khách gồm 4 bước: Khách hàng → Phòng và hợp đồng → Bàn giao phòng → Thu cọc và tài khoản. Nguồn: `frontend/src/pages/checkin.ts`, `docs/KICH_BAN_KHACH_A.md`.
- Bước chọn khách chỉ hiển thị khách chưa có phòng đang thuê. Nguồn: `frontend/src/pages/checkin.ts:103-104`.
- Chỉ phòng có trạng thái `VACANT` được chọn để lập hợp đồng; service cũng kiểm tra trạng thái và hợp đồng còn hiệu lực. Nguồn: `ContractService.create()`.
- Hợp đồng phải có ngày kết thúc sau ngày bắt đầu; giá, cọc, chu kỳ và ngày đến hạn được kiểm tra ở service. Nguồn: `ContractService.create()` và `checkPricing()`.
- Biên bản bàn giao yêu cầu ghi tình trạng phòng/đồ đạc; khách không được mặc định là đã đồng ý. Nguồn: `frontend/src/pages/checkin.ts`, `CheckinInspectionService`.
- Cọc có thể thu nhiều lần; chưa thu đủ thì không thể hoàn tất nhận phòng. Nếu cọc bằng 0 thì không cần thu. Nguồn: `frontend/src/pages/checkin.ts`, `docs/KICH_BAN_KHACH_A.md`.
- Tài khoản người thuê đăng nhập bằng số điện thoại và mật khẩu tạm chỉ hiện một lần khi cấp/cấp lại. Nguồn: tenant account service/UI và `docs/KICH_BAN_KHACH_A.md`.
- Xử lý giữa chừng phải cho phép tiếp tục từ trạng thái đã lưu; tài liệu nghiệp vụ mô tả Tổng quan và cửa sổ hợp đồng dẫn lại bước còn thiếu. Cần browser verification để xác nhận toàn bộ đường dẫn thực tế.

## Trả phòng và tiền

- Trả phòng bình thường đi qua menu Trả phòng; chấm dứt sớm là luồng riêng với lý do tối thiểu, mã phòng xác nhận và điều kiện công nợ theo tài liệu nghiệp vụ. Nguồn: `docs/KICH_BAN_KHACH_A.md` và service tương ứng.
- Khi quyết toán, phần cọc đã thu là cơ sở hoàn cọc; công nợ và hư hỏng được trừ trước khi hoàn. Nguồn: `docs/KICH_BAN_KHACH_A.md`, `DepositLedger`/checkout services.
- Tiền phòng tháng không thu tại bước nhận phòng; phát hành hóa đơn theo luồng tính tiền hằng tháng. Nguồn: `docs/KICH_BAN_KHACH_A.md` và UI checkin.

## Quyền và dữ liệu

- Khi bật `app.security.enabled`, API yêu cầu JWT trừ các đường dẫn công khai được cấu hình; quyền endpoint cần kiểm tra riêng bằng test/security audit. Nguồn: `SecurityConfig`, README.
- Database dùng `ddl-auto=validate`; entity lệch schema phải làm app báo lỗi khi khởi động. Nguồn: README và cấu hình.
- `created_at` là cột bắt buộc ở nhiều bảng; không được phụ thuộc mù quáng vào database default khi entity còn tiếp tục bị dirty-check/update. Nguồn: SQL schema và regression lesson RL-002.

## Cần xác nhận trước khi thay đổi

- Quy tắc chính thức về số người ở cùng, phí dịch vụ và thời điểm bắt đầu tính trong mọi loại hợp đồng.
- Quyền cụ thể của từng vai trò cho các endpoint còn thiếu hoặc chưa có browser verification.
- Ngưỡng số lượng bản ghi để chuyển select nhỏ sang search/autocomplete/pagination.
- Có bắt buộc tìm kiếm tiếng Việt không dấu trên mọi màn hình hay chỉ các màn hình có danh sách lớn.
- Chính sách xử lý khi người dùng refresh sau một phần thao tác nhưng request trước đó đang pending.
