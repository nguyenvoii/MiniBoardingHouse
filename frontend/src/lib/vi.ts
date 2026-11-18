// Backend ghi nhật ký và một số thông báo lỗi bằng tiếng Việt không dấu. Bảng này khôi phục dấu cho các câu đã biết.
// Cụm dài đặt trước để khớp trước. Câu lạ thì giữ nguyên.
const PHRASES: Array<[string, string]> = [
  ['Dat lai mat khau bang ma OTP', 'Đặt lại mật khẩu bằng mã OTP'], ['Xac thuc email, kich hoat tai khoan', 'Xác thực email, kích hoạt tài khoản'],
  ['Mo khoa tai khoan nguoi thue cua khach thue', 'Mở khóa tài khoản người thuê của khách thuê'], ['Khoa tai khoan nguoi thue cua khach thue', 'Khóa tài khoản người thuê của khách thuê'],
  ['Cap tai khoan nguoi thue cho khach thue', 'Cấp tài khoản người thuê cho khách thuê'], ['Sua dieu khoan thanh toan hop dong', 'Sửa điều khoản thanh toán hợp đồng'],
  ['Sua dieu khoan dien nuoc hop dong', 'Sửa điều khoản điện nước hợp đồng'], ['Sua gia/coc/chu ky hop dong', 'Sửa giá, cọc, chu kỳ hợp đồng'],
  ['Dat tai khoan ngan hang mac dinh', 'Đặt tài khoản ngân hàng mặc định'], ['Ngung dung tai khoan ngan hang', 'Ngừng dùng tài khoản ngân hàng'],
  ['Cap nhat ma QR cua tai khoan', 'Cập nhật mã QR của tài khoản'], ['Sao luu co so du lieu', 'Sao lưu cơ sở dữ liệu'], ['Sua tai khoan ngan hang', 'Sửa tài khoản ngân hàng'],
  ['Dang nhap thanh cong', 'Đăng nhập thành công'], ['Cap nhat ho so ca nhan', 'Cập nhật hồ sơ cá nhân'], ['Cap nhat anh dai dien', 'Cập nhật ảnh đại diện'],
  ['Go anh dai dien', 'Gỡ ảnh đại diện'], ['Cap nhat nhan vien', 'Cập nhật nhân viên'], ['Them nhan vien', 'Thêm nhân viên'], ['Dieu chinh hop dong', 'Điều chỉnh hợp đồng'],
  ['Cham dut hop dong', 'Chấm dứt hợp đồng'], ['Chuyen hop dong', 'Chuyển hợp đồng'], ['Gia han hop dong', 'Gia hạn hợp đồng'], ['Duyet ky tinh tien', 'Duyệt kỳ tính tiền'],
  ['Tao ky tinh tien', 'Tạo kỳ tính tiền'], ['Tinh tien ky', 'Tính tiền kỳ'], ['Xac nhan giao dich', 'Xác nhận giao dịch'], ['Tao giao dich', 'Tạo giao dịch'],
  ['Ghi nhan thanh toan', 'Ghi nhận thanh toán'], ['Them nguoi o cung', 'Thêm người ở cùng'], ['Sua nguoi o cung', 'Sửa người ở cùng'], ['Nguoi o cung', 'Người ở cùng'],
  ['Ngung dung xe', 'Ngừng dùng xe'], ['Dang ky xe', 'Đăng ký xe'], ['Lap hop dong', 'Lập hợp đồng'], ['Gui hop dong', 'Gửi hợp đồng'], ['Ky hop dong', 'Ký hợp đồng'],
  ['Doi mat khau', 'Đổi mật khẩu'], ['Dang xuat', 'Đăng xuất'], ['Them khoan', 'Thêm khoản'], ['Xoa dong', 'Xóa dòng'], ['Sua dong', 'Sửa dòng'], ['Sua xe', 'Sửa xe'],
  [' cua bang tinh ', ' của bảng tính '], [' vao bang tinh ', ' vào bảng tính '], [' cho hoa don ', ' cho hóa đơn '], [' cho khach thue', ' cho khách thuê'],
  [' tu phong ', ' từ phòng '], [' vao phong ', ' vào phòng '], [' roi phong', ' rời phòng'], [' khu tro ', ' khu trọ '], [' den ', ' đến '], [' phong)', ' phòng)'],
  ['Sai tai khoan hoac mat khau', 'Sai tài khoản hoặc mật khẩu'], ['Chua dang nhap hoac token het han', 'Chưa đăng nhập hoặc phiên đã hết hạn'],
  ['Tai khoan da bi khoa', 'Tài khoản đã bị khóa'],
  ['Hop dong dang co yeu cau tra phong chua xu ly: hoan tat thanh ly o muc Tra phong hoac huy yeu cau do truoc', 'Hợp đồng đang có yêu cầu trả phòng chưa xử lý: hoàn tất thanh lý ở mục Trả phòng hoặc hủy yêu cầu đó trước'],
  ['Hop dong khong con hieu luc, khong the yeu cau tra phong', 'Hợp đồng không còn hiệu lực, không thể yêu cầu trả phòng'],
  ['khong con hieu luc de thao tac', 'không còn hiệu lực để thao tác'], ['Khach thue chua co tai khoan, hay cap tai khoan', 'Khách thuê chưa có tài khoản, hãy cấp tài khoản'],
  ['Khong co quyen thuc hien', 'Bạn không có quyền thực hiện thao tác này'], ['Loi he thong', 'Lỗi hệ thống, thử lại sau hoặc báo quản trị'],
  ['Ky tinh tien chua duoc duyet (dang DRAFT)', 'Kỳ tính tiền chưa được duyệt (đang Nháp)'], ['Phong doi khach giua thang', 'Phòng đổi khách giữa tháng'],
  ['Thieu chi so dien nuoc', 'Thiếu chỉ số điện nước'], ['Chi so chua xac nhan', 'Chỉ số chưa xác nhận'], ['Chi chap nhan chuyen khoan', 'Chỉ chấp nhận chuyển khoản'],
  ['da co phieu hoan coc', 'đã có phiếu hoàn cọc'], ['Giao dich da duoc xac nhan', 'Giao dịch đã được xác nhận'],
];

export function restoreVi(text: string): string {
  let out = text;
  for (const [from, to] of PHRASES) out = out.split(from).join(to);
  return out;
}
