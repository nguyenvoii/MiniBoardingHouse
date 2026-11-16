HƯỚNG DẪN ĐẨY CODE LÊN GIT (NHÓM 7)

File này dành cho cả nhóm. Làm đúng thứ tự, đừng bỏ bước. Repo: https://github.com/nguyenvoii/MiniBoardingHouse


NGUYÊN TẮC CHUNG

- KHÔNG upload file zip hay "Add files via upload" trên trang web GitHub. Luôn đẩy code bằng lệnh git.
- KHÔNG commit thẳng vào nhánh main. Mỗi chức năng làm trên một nhánh riêng, xong thì mở Pull Request để Phi xem rồi mới gộp vào main.
- KHÔNG dùng git push --force.
- Chỉ sửa trong package của mình (phi, chi, toan, viet). Muốn sửa package shared thì báo Phi trước.
- KHÔNG commit mật khẩu, token, file application-local.properties, thư mục target/ hay file nén.


LẦN ĐẦU TIÊN (chỉ làm một lần)

1. Cài Git for Windows: https://git-scm.com/download/win
2. Khai báo tên và email (dùng email đăng ký GitHub):
   git config --global user.name "Tên của bạn"
   git config --global user.email "email-github-của-bạn"
3. Lấy code về máy:
   git clone https://github.com/nguyenvoii/MiniBoardingHouse.git
   cd MiniBoardingHouse
4. Khi push lần đầu GitHub sẽ hỏi đăng nhập. Đăng nhập bằng tài khoản GitHub của bạn trong cửa sổ hiện ra. Nếu cần mật khẩu thì dùng Personal Access Token, đừng dùng mật khẩu tài khoản, và đừng gửi token cho ai.


MỖI LẦN BẮT ĐẦU LÀM MỘT CHỨC NĂNG

1. Về nhánh main và lấy code mới nhất:
   git checkout main
   git pull
2. Tạo nhánh riêng, đặt tên theo kiểu tên-bạn/chức-năng:
   git checkout -b toan/meter-reading
   (ví dụ: chi/room-crud, viet/debt, phi/contract)


TRONG LÚC LÀM

- Commit thường xuyên, mỗi commit là một việc nhỏ chạy được.
- Trước khi commit, xem mình sắp đưa gì lên:
   git status
   git diff
- Chỉ thêm đúng file cần thiết, không dùng git add . khi chưa kiểm tra git status:
   git add src/main/java/com/group7/minihouse/toan
   git commit -m "Thêm API ghi chỉ số điện nước (UC53)"
- Lời nhắn commit viết bằng tiếng Việt CÓ DẤU, ngắn gọn, nói rõ làm gì. Ví dụ: "Sửa lỗi trùng mã phiếu thu", "Thêm test cho tính tiền điện".


TRƯỚC KHI ĐẨY LÊN

1. Chạy thử: build được và test xanh (mvn test hoặc nút Run test trong IDE).
2. Cập nhật code mới của người khác về nhánh của mình:
   git fetch origin
   git rebase origin/main
   Nếu báo xung đột (CONFLICT): mở file bị báo, giữ lại phần đúng, xóa các dòng <<<<<<< ======= >>>>>>>, rồi:
   git add tên-file-đã-sửa
   git rebase --continue
   Bối rối quá thì chạy git rebase --abort để quay lại như cũ rồi hỏi Phi.
3. Đẩy nhánh lên GitHub:
   git push -u origin toan/meter-reading
   (những lần sau chỉ cần git push)


MỞ PULL REQUEST

1. Vào trang repo trên GitHub, bấm nút "Compare & pull request" hiện ra ở đầu trang.
2. Ghi tiêu đề và mô tả ngắn: làm UC nào, đã test thế nào.
3. Chọn Phi làm reviewer, bấm "Create pull request".
4. Nếu Phi nhờ sửa: sửa tiếp trên cùng nhánh, commit và git push, PR tự cập nhật.
5. Phi duyệt xong thì bấm "Merge". Sau đó dọn nhánh ở máy mình:
   git checkout main
   git pull
   git branch -d toan/meter-reading


LỖI THƯỜNG GẶP

1. "rejected ... fetch first" hoặc "non-fast-forward" khi push:
   Trên GitHub có commit mới mà máy bạn chưa có. Chạy git pull --rebase rồi push lại. KHÔNG dùng --force.
2. Lỡ add nhầm file (ví dụ file zip, target/, file chứa mật khẩu), chưa commit:
   git restore --staged tên-file
3. Lỡ commit nhầm file nhưng chưa push:
   git rm --cached tên-file
   git commit --amend
4. Lỡ push token hoặc mật khẩu lên GitHub:
   Báo Phi NGAY và đổi/thu hồi token đó trên GitHub. Xóa file khỏi repo là chưa đủ, vì nó còn nằm trong lịch sử.
5. Không nhớ mình đang ở nhánh nào, đã sửa gì:
   git status
   git branch
   git log --oneline -5
6. Muốn bỏ hết thay đổi chưa commit của một file:
   git restore tên-file
   (cẩn thận: lệnh này xóa thay đổi, không lấy lại được)


TÓM TẮT MỘT VÒNG LÀM VIỆC

   git checkout main
   git pull
   git checkout -b ten-ban/chuc-nang
   ... sửa code, chạy thử ...
   git status
   git add <các file cần thiết>
   git commit -m "Lời nhắn tiếng Việt có dấu"
   git fetch origin
   git rebase origin/main
   git push -u origin ten-ban/chuc-nang
   ... mở Pull Request trên GitHub, chờ Phi duyệt ...
