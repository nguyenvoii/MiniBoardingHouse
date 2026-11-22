# QA Checklist

Dùng checklist này trước khi báo hoàn thành một thay đổi hoặc một nhóm audit. Test xanh chỉ chứng minh các tình huống đã chạy, không chứng minh toàn bộ phần mềm không còn lỗi.

## 1. Triage và bằng chứng

- [ ] Ghi severity P0/P1/P2/P3 và ảnh hưởng thực tế.
- [ ] Có bước tái hiện cụ thể, dữ liệu đầu vào và kết quả quan sát.
- [ ] Phân biệt confirmed bug, risk, UX/design issue và business decision cần xác nhận.
- [ ] Đã truy từ UI → API → service → repository/entity → database khi lỗi đi qua nhiều tầng.
- [ ] Không gọi là lỗi chỉ dựa trên tên file, tên biến hoặc suy đoán.

## 2. TDD và sửa lỗi

- [ ] Viết test hồi quy tối thiểu trước khi sửa production code.
- [ ] Chạy test và chứng kiến failure đúng nguyên nhân.
- [ ] Sửa root cause, không chỉ đổi thông báo lỗi hoặc che triệu chứng.
- [ ] Chạy focused test sau sửa.
- [ ] Chạy test liên quan và full backend suite.
- [ ] Không sửa test chỉ để làm test xanh.

## 3. API, validation và database

- [ ] Empty/null/duplicate/invalid input.
- [ ] Boundary values: số tiền, ngày, thời hạn, số lượng, độ dài chuỗi.
- [ ] HTTP status và error message nói rõ cách sửa.
- [ ] Kiểm tra transaction khi có nhiều ghi dữ liệu.
- [ ] Kiểm tra database constraint, default, NOT NULL, FK, unique và dirty checking.
- [ ] Kiểm tra retry/double-submit và hai request đồng thời nếu nghiệp vụ có thể gặp.
- [ ] Không ghi uncontrolled data vào DB thật; dùng DB tạm hoặc transaction rollback.

## 4. Workflow nhiều bước

Với mỗi workflow, kiểm tra:

- [ ] Happy path từ đầu đến cuối.
- [ ] Back và cancel ở từng bước.
- [ ] Refresh/đóng trình duyệt giữa các bước.
- [ ] API failure, timeout, retry.
- [ ] Double-click/repeated submit không tạo bản ghi trùng.
- [ ] Validation chặn dữ liệu sai nhưng không chặn dữ liệu hợp lệ.
- [ ] Loading/error/success state kết thúc rõ ràng.
- [ ] Có đường quay lại khi trạng thái đã lưu một phần.
- [ ] Quyền người dùng đúng ở từng bước.

## 5. UX/UI thực tế

- [ ] Danh sách dài có search/filter/sort/pagination hoặc lazy loading phù hợp.
- [ ] Search tiếng Việt không dấu/khác dấu được đánh giá khi có yêu cầu.
- [ ] Select không tải toàn bộ dữ liệu khi quy mô có thể tăng lớn.
- [ ] Empty state, loading state và error state dễ hiểu.
- [ ] Nút disabled giải thích điều kiện còn thiếu.
- [ ] Confirmation cho thao tác nguy hiểm.
- [ ] Mobile/narrow viewport, keyboard, focus, label, contrast và tap target.
- [ ] Screenshot/browser check kiểm tra rendered behavior, không chỉ TypeScript/build.

## 6. Regression và tự-review audit

- [ ] Chạy backend full suite và ghi đúng số lượng/kết quả.
- [x] Chạy frontend typecheck/build: `npm run build` là script chính của repo.
- [ ] Chạy Playwright/browser suite khi thay đổi workflow/UI; hiện repo chưa có Playwright suite, phải ghi rõ blocked/chưa xác minh.
- [ ] Kiểm tra module liên quan nhưng không bị sửa trực tiếp.
- [ ] Liệt kê workflow đã kiểm tra và workflow chưa kiểm tra.
- [ ] Liệt kê test mock-only chưa chứng minh hành vi người dùng.
- [ ] Tìm pattern cùng bản chất ở module khác.
- [ ] Cập nhật regression lesson/business rule nếu phát hiện mới.
- [ ] `git diff --check`, kiểm tra file thay đổi và untracked.
- [ ] Chưa commit/push nếu chưa được yêu cầu.
