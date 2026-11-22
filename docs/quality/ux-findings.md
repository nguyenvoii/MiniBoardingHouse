# UX Findings

Audit ngày 22/11/2026. Đây là inventory bằng chứng hiện có; finding chưa có browser/data-scale evidence được ghi là candidate, không gọi là confirmed bug.

| ID | Màn hình | Vị trí | Vấn đề | Bằng chứng hiện tại | Ảnh hưởng | Đề xuất | Priority | Trạng thái |
|---|---|---|---|---|---|---|---|---|
| UX-001 | Nhận khách → bước 1 | `frontend/src/pages/checkin.ts:103-104` | Chọn khách dùng native `<select>` với toàn bộ khách chưa thuê | Code map toàn bộ `live.tenants.filter((t) => !t.roomCode)` vào options; màn Khách hàng riêng đã có search/filter ở `tenants.ts:20-27` | Khi số khách tăng, khó tìm, danh sách dài trên mobile; có thể làm chậm render | Nếu browser test với dữ liệu lớn xác nhận, dùng search/autocomplete theo tên/SĐT và giữ lựa chọn đã chọn; cân nhắc endpoint search/pagination nếu API cần | P2 | Candidate, chờ seed + browser evidence |
| UX-002 | Nhận khách → tiếp tục hợp đồng dở | `frontend/src/pages/checkin.ts:60-78` (đã sửa) | Nuốt lỗi tải tiến độ | Browser route interception: deposit/inspection 503; bản cũ không cảnh báo | Người vận hành có thể hiểu nhầm trạng thái | `Promise.allSettled` + cảnh báo rõ | P2 | Fixed; API-failure browser test PASS |
| UX-003 | Các màn chọn hợp đồng/phòng | `frontend/src/pages/checkout.ts:79`, `maintenance.ts:59`, `settings.ts:129` | Native select tải danh sách từ API; chưa có bằng chứng pagination/search tại UI | Code tạo options trực tiếp từ mảng contracts/rooms; backend contracts có pagination nhưng các component có thể đọc tập lớn | Khó thao tác khi dữ liệu tăng; chi phí render và cognitive load | Inventory theo dữ liệu 500–1000 bản ghi; chỉ đổi các danh sách vượt ngưỡng đã kiểm chứng | P2 | Candidate, chờ seed + browser evidence |

| UX-004 | Khách hàng | `frontend/src/pages/tenants.ts:16-18` (đã sửa) | Danh sách tạo N+1 request account | Browser với 114 khách: bản cũ tạo 114 request account trước khi render | Chậm tải, dễ nghẽn khi quy mô tăng | Lazy-load khi mở hồ sơ | P2 | Fixed; browser regression PASS, list tạo 0 request account |
## Trạng thái sau browser audit

- UX-002 đã fixed và xác minh bằng route-interception browser test: khi deposit/inspection trả 503, UI hiển thị cảnh báo rõ thay vì suy diễn trạng thái thiếu.
- UX-001 và UX-003 vẫn là candidate: với dữ liệu QA hiện tại, list 100 khách render được và search hoạt động; chưa chứng minh ngưỡng 500–1000 gây hại.
- Concurrent deposit và role regression trên môi trường QA chưa chạy; cần test riêng nếu tiếp tục mở rộng audit.
