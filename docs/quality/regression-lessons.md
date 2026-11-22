# Regression Lessons

Tài liệu này ghi lại các lỗi đã xảy ra hoặc đã được xác minh, kèm tình huống tái hiện và biện pháp phòng tránh. Không xem tài liệu này là bằng chứng rằng toàn bộ phần mềm không còn lỗi.

## RL-001 — Customer Creation: Step 2 Deadlock

- Mức độ: P1 — workflow tạo hồ sơ khách bị kẹt.
- Nguồn phát hiện: báo cáo hồi quy của người dùng trong đợt audit ngày 22/11/2026.
- Phạm vi: luồng tạo khách hàng nhiều bước, đặc biệt bước 2; cần kiểm tra cả màn Khách hàng và các workflow nhiều bước có cùng cơ chế chuyển bước.
- Tình huống tái hiện được yêu cầu: mở luồng thêm khách hàng, hoàn thành bước 1, chuyển sang bước 2, sau đó thao tác theo điều kiện làm bước 2 không thể tiếp tục hoặc quay lại đúng cách. Exact payload, API response và commit gây lỗi chưa được lưu trong repository hiện tại.
- Root cause: chưa được kết luận từ code hiện tại. Không được ghi nhận là đã xác định chỉ dựa trên tên hàm hoặc giả định về state.
- Vì sao review/test bỏ sót: chưa có bằng chứng lịch sử đủ chi tiết; giả thuyết cần kiểm tra là test tập trung vào API/unit hoặc happy path, chưa kiểm tra state sau back/refresh/API failure bằng giao diện thật.
- Phòng tránh bắt buộc: browser check cho bước 1→2, back, refresh, double-click, API failure và retry; test phải kiểm tra hành vi rendered của người dùng, không chỉ mock callback.
- Trạng thái: cần tái hiện và truy root cause trong Task 2–3 của kế hoạch audit.

## RL-002 — Tạo hợp đồng: `created_at` bị NULL sau dirty checking

- Mức độ: P1 — bước Lập hợp đồng trong Nhận khách thất bại trên DB thật.
- Tình huống tái hiện: từ màn Nhận khách, chọn khách/phòng hợp lệ và bấm Lập hợp đồng. App trả thông báo chung về dữ liệu trùng/ràng buộc.
- Bằng chứng: log Spring Boot ghi `Cannot insert the value NULL into column 'created_at', table ... rental_contracts`; DB thật có cột `created_at NOT NULL DEFAULT getdate()` và không có bản ghi null.
- Root cause: `ContractService.create()` tạo `RentalContract` với `createdAt == null`, lưu entity rồi cập nhật `pdfContractUrl`. Hibernate dirty checking phát sinh UPDATE với `created_at = NULL`; SQL Server từ chối. Database default chỉ giúp INSERT bỏ cột, không sửa được entity state khi UPDATE.
- Đã sửa local: gán `c.setCreatedAt(LocalDateTime.now())` trước `repo.save(c)` tại `ContractService`; thêm assertion regression trong `ContractServiceTest`.
- Bằng chứng sau sửa: focused test PASS; full Maven suite PASS 426 tests, 0 failures, 0 errors; app boot và HTTP 200 đã được kiểm tra. Chưa commit/push.
- Vì sao review/test bỏ sót: test cũ mock repository, không chạy Hibernate dirty checking và SQL Server default/NOT NULL; review không có integration test cho create-then-update trên schema thật.
- Phòng tránh: entity có cột bắt buộc phải được khởi tạo ở application layer khi service tạo entity; integration/browser smoke cần bao phủ create-then-update; không coi unit test mock là kiểm chứng DB boundary.

## RL-004 — Nuốt lỗi khi tải tiến độ nhận phòng

- Mức độ: P2 — người vận hành có thể hiểu nhầm trạng thái đã lưu.
- Tái hiện: mở `#/checkin?contract=<id>`; giả lập `GET /api/contracts/{id}/deposit` hoặc `GET /api/chi/checkin-inspections` trả 503. Bản cũ dùng `.catch(() => null/[])`, tiếp tục hiển thị checklist như thể dữ liệu chỉ đang thiếu.
- Root cause: `loadProgress()` dùng `Promise.all` với fallback im lặng, không phân biệt “chưa có dữ liệu” và “không tải được dữ liệu”.
- Khắc phục: dùng `Promise.allSettled()`, đặt `progressLoadFailed`, hiển thị cảnh báo rõ trong summary khi bất kỳ API tiến độ thất bại.
- Verification: browser route-interception test PASS; calls tới deposit/inspection bị giả lập 503 và UI hiển thị `Không thể kiểm tra tiến độ nhận phòng`.
- Phòng tránh: API failure test phải là một phần của workflow browser regression; không fallback lỗi mạng thành dữ liệu nghiệp vụ mặc định.


- Mức độ: P2 — hiệu năng/UX, tăng mạnh số request khi danh sách khách lớn.
- Tái hiện: đăng nhập chủ trọ, mở `#/tenants` với dữ liệu hiện có 114 khách; bản cũ gửi 114 request `GET /api/tenants/{id}/account` song song trước khi render danh sách. Browser audit bắt lỗi với `Tenant list made too many account requests: 114`.
- Root cause: `tenantsPage()` dùng `Promise.all(tenants.map(...))` để tải trạng thái account cho toàn bộ dòng, dù trạng thái chỉ cần khi mở drawer chi tiết.
- Khắc phục: bỏ preload account khỏi list; khi mở hồ sơ mới gọi đúng một API, có trạng thái đang tải và thông báo lỗi riêng.
- Verification: frontend build PASS; browser audit PASS trên 114 khách, search render được, 0 request account lúc mở list, không có console error/HTTP >= 400 trong run.
- Phòng tránh: không gọi endpoint chi tiết theo từng dòng trong list; browser test phải đếm request và kiểm tra rendered list với dữ liệu nhiều dòng.


- Finding chỉ được gọi là confirmed bug khi có tái hiện, log/data evidence hoặc test chứng minh.
- Risk/design issue/business decision phải ghi nhãn riêng, không trộn với confirmed bug.
- Sau mỗi nhóm audit phải tự-review: workflow nào chưa chạy thật, test nào chỉ kiểm tra mock, và pattern tương tự đã được tìm ở đâu.
