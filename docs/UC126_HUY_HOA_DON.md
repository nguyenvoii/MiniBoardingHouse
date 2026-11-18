# UC126 – Hủy hóa đơn phát hành nhầm

## API

`PATCH /api/invoices/{id}/cancel`

Quyền: `LANDLORD`, `MANAGER`, `CASHIER` (JWT).

Request:

```json
{
  "reason": "Phát hành nhầm hóa đơn của phòng"
}
```

Response theo `ApiResponse<CancelInvoiceResponse>`; trạng thái hóa đơn sau khi hủy là `CANCELLED`.

## Quy tắc nghiệp vụ

- Bắt buộc có lý do hủy, tối đa 500 ký tự.
- Khóa dòng hóa đơn bằng `PESSIMISTIC_WRITE` trong transaction.
- Không hủy hóa đơn đã `CANCELLED`.
- Không hủy nếu `paid_amount > 0`, có giao dịch `SUCCESS`, hoặc có đợt thanh toán một phần với số tiền dương.
- Không xóa hóa đơn, không sửa khoản thu, không tự đảo giao dịch.
- Ghi `CANCEL_INVOICE` vào `user_activity_logs` trong cùng transaction, lấy người thao tác từ JWT và IP từ request.

## Kiểm thử

`InvoiceServiceTest` bao phủ hủy thành công, đã thu một phần, có giao dịch thành công, có đợt thanh toán một phần, hóa đơn đã hủy, không tìm thấy hóa đơn và lý do trống.

## Lưu ý tích hợp đồng thời

UC126 khóa dòng hóa đơn trong lúc hủy. Để chống tuyệt đối trường hợp thanh toán bắt đầu đồng thời từ luồng `phi`, cần thành viên phụ trách `phi` phối hợp khóa cùng dòng hóa đơn và kiểm tra lại trạng thái ngay trước khi ghi giao dịch thành công. Package `toan` không tự ý sửa package `phi` theo quy ước nhóm.
