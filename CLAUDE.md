# MiniBoardingHouse — Local Engineering Rules

## Quality gate

- Đọc các tài liệu trong `docs/quality/` trước khi sửa workflow hoặc nghiệp vụ.
- Mọi thay đổi chạm workflow phải chạy browser test của workflow đó và ghi kết quả thật trước khi báo hoàn thành.
- Không gọi một issue là bug nếu chưa có reproduction, log/data evidence hoặc test chứng minh.
- Với bug fix: Reproduce → test đỏ → root cause → fix tối thiểu → test xanh → regression → documentation.
- Test xanh chỉ chứng minh các tình huống đã chạy; không coi đó là bằng chứng phần mềm không còn lỗi.
- Sau mỗi nhóm audit phải tự-review workflow bỏ sót, pattern tương tự và mock-only tests.

## Safety

- Không sửa hoặc stage các file demo/SRS untracked nếu không được yêu cầu.
- Không migration destructive, xóa dữ liệu hoặc ghi uncontrolled data vào DB thật.
- Dùng DB tạm/transaction rollback cho integration hoặc E2E có ghi dữ liệu.
- Không commit/push nếu chưa có yêu cầu rõ ràng.

## Verification commands

- Backend: dùng Maven wrapper/path đã ghi trong memory/project instructions, chạy `mvn test` và ghi đúng số test.
- Frontend: `cd frontend && npm run build` (lệnh này gồm `tsc --noEmit` và `vite build`).
- Browser verification: chỉ báo PASS khi đã chạy test trên app thật và có kết quả rendered; không thay bằng compile.
