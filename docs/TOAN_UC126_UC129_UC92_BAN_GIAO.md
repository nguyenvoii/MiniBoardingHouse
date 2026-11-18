# Bàn giao UC126–UC129 và bổ sung UC92

## Phạm vi
- UC126: `PATCH /api/invoices/{id}/cancel` – hủy hóa đơn phát hành nhầm; yêu cầu lý do; khóa hóa đơn khi xử lý; từ chối hóa đơn đã hủy hoặc đã có khoản thu; ghi nhật ký thao tác.
- UC127: `GET /api/payment-reports/daily?from=YYYY-MM-DD&to=YYYY-MM-DD` – báo cáo giao dịch thành công/đảo/thất bại, nhóm theo ngày/người thu/phương thức; `GET /api/payment-reports/daily/export` – xuất Excel.
- UC128: `GET /api/invoice-history?from=...&to=...&actionType=...&keyword=...&page=0&size=20` – tra cứu lịch sử hóa đơn/giao dịch. Danh sách action hỗ trợ có `REFUND_PREPAYMENT`.
- UC129: `GET /api/monthly-statements?month=YYYY-MM`; `GET /api/monthly-statements/export?month=YYYY-MM&format=xlsx|pdf`.
- UC92: `GET /api/tenants/{tenantId}/prepayment`; `GET /api/tenants/{tenantId}/prepayment/deductions`; `POST /api/invoices/{invoiceId}/overpayment/to-balance`; `POST /api/prepayments/deduct`; `DELETE /api/prepayments/deductions/{id}`; `POST /api/prepayments/refund`; `POST /api/payment-transactions/{id}/reverse`.

## Bổ sung an toàn UC92 trong bản này
- Dùng pessimistic write lock cho số dư tiền dư khi cấn trừ/hoàn tiền, tránh hai yêu cầu đồng thời sử dụng cùng số dư.
- Khóa hóa đơn khi chuyển khoản trả thừa vào số dư khách thuê và khi cấn trừ vào hóa đơn.
- Hoàn tiền yêu cầu vai trò LANDLORD/MANAGER; ghi `REFUND_PREPAYMENT` vào `user_activity_logs` trong cùng transaction.
- Việc hoàn tiền ở phần mềm chỉ cập nhật số dư và nhật ký. Đơn vị vận hành vẫn phải thực hiện chi tiền thực tế theo quy trình kế toán/ngân hàng; không được hiểu API này tự chuyển tiền qua ngân hàng.

## Quy tắc kiểm thử thủ công
1. Hủy hóa đơn chưa thu: nhập lý do hợp lệ, kiểm tra trạng thái CANCELLED và nhật ký.
2. Hủy hóa đơn đã có khoản thu/đã hủy: phải nhận lỗi 409, dữ liệu không đổi.
3. Báo cáo ngày: so sánh tổng giao dịch SUCCESS, REVERSED, FAILED với dữ liệu giao dịch; kiểm tra khoảng ngày bao gồm ngày kết thúc.
4. Lịch sử: lọc theo ngày, actionType và mã hóa đơn/giao dịch; xác nhận người thực hiện/thời gian/IP.
5. Bảng kê: xuất cùng tháng ở xlsx và pdf; mở được tệp, kiểm tra tổng hợp và danh sách hóa đơn/phiếu thu.
6. UC92: chuyển tiền trả thừa một lần; thử gọi lại phải bị từ chối do không còn khoản thừa; cấn trừ không vượt số dư và không vượt dư nợ; hoàn tiền không vượt số dư; hai yêu cầu đồng thời không được làm số dư âm; kiểm tra log REFUND_PREPAYMENT.
7. Giao dịch: đảo SUCCESS một lần; gọi lại phải bị từ chối; chỉ LANDLORD/MANAGER được đảo giao dịch/hoàn tiền.

## Lưu ý triển khai
- Không commit `src/main/resources/application.properties` nếu chứa thông tin xác thực. Dùng biến môi trường cho mật khẩu DB.
- UC127 có export Excel; UC129 có export Excel/PDF.
- Chạy `mvn test` ở máy phát triển sau khi cấu hình JDK 21 và SQL Server theo cấu hình môi trường.
