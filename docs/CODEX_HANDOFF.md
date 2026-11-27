# Bàn giao MiniBoardingHouse cho agent tiếp theo

Cập nhật ngày 26/11/2026. Tài liệu này ghi trạng thái khi người dùng yêu cầu tạm dừng công việc và bàn giao. Goal hiện là `paused`. Chỉ tiếp tục triển khai khi người dùng yêu cầu tiếp tục.

## 1. Mục tiêu người dùng đã chốt

Hoàn thiện và tích hợp end-to-end toàn bộ UC1–UC123, bao gồm frontend, backend và database. Nguồn yêu cầu là sheet **Phân Công** trong `C:\VoiVoi\projava1\group-7.xlsx`. Bảng theo dõi chính là `docs/NGHIEM_THU_123_USE_CASE.xlsx`.

Ưu tiên P0/P1 cho khách thuê, phòng, hợp đồng, nhận/trả phòng, hóa đơn, thanh toán và phân quyền. Thiếu API thì bổ sung, thiếu màn hình thì xây dựng, chưa nối thì tích hợp, sai nghiệp vụ thì sửa. Mỗi lỗi cần được sửa tận gốc, kiểm thử lại và có regression test phù hợp.

Điều kiện PASS là frontend → API → SQL Server QA → kết quả hiển thị đúng, kèm kiểm tra quyền và tình huống lỗi quan trọng. API QA và JUnit là bằng chứng thành phần. Tái sử dụng bằng chứng cũ khi đúng phiên bản và đủ phạm vi. Tích hợp bên ngoài thiếu điều kiện phải ghi BLOCKED.

Tiêu chí kết thúc là 123/123 UC E2E PASS và hết lỗi P0/P1. Hiện chưa đạt. Chưa có tỷ lệ hoàn thành đáng tin cậy. Con số 75% từng báo trong chat là ước lượng và đã được đính chính.

Người dùng muốn IMPLEMENT → TEST → FIX liên tục, tự chuyển sang UC tiếp theo. Chỉ cập nhật sau một nhóm UC hoặc khi có blocker nghiêm trọng. Người dùng đã phản ánh việc agent chỉ chạy nhiều lượt API QA rồi báo cáo từng phần.

## 2. Bắt đầu ngay từ công việc đang dở

Đang chuyển trọng tâm sang frontend E2E cho màn **Chỉ số điện nước** sau khi backend được bổ sung chỉ số chốt chuyển phòng.

Đã đọc `frontend/src/pages/meters.ts`, API client, context tháng, Vite config và tìm công cụ browser. Chưa sửa mã frontend trong lượt này. Chưa tạo regression E2E mới. Chưa chụp baseline hay chạy browser cho luồng này.

Đã tạo `target/vite.e2e.config.mjs` để chạy frontend local cổng 5178, proxy `/api` tới QA `http://127.0.0.1:18091`. Lệnh khởi động Vite bị người dùng ngắt giữa chừng. Kiểm tra ngay trước khi bàn giao cho thấy Vite có phản hồi ở 5178 nhưng `/api/auth/me` trả 500 vì backend 18091 đã dừng.

### Lỗi tích hợp cần xử lý tiếp

Backend đã hỗ trợ nhiều chỉ số trong một phòng/kỳ và trả `closingContractId`. Frontend `meters.ts` vẫn có các điểm cần sửa và chứng minh bằng browser.

1. `Reading` chưa khai báo `closingContractId`. Bảng chưa phân biệt chỉ số chốt của hợp đồng cũ với chỉ số định kỳ của khách mới.
2. `olds` chỉ lấy chỉ số kỳ trước. Khi khách mới ghi trong cùng kỳ đã có chỉ số chốt, gợi ý số cũ và kiểm tra frontend có thể dùng mốc sai. Cần ưu tiên chỉ số chốt mới nhất của phòng trong kỳ hiện tại. Backend đã lấy mốc chốt khi ghi bản mới.
3. Cần kiểm tra nút điều chỉnh chỉ số chốt đã dùng làm mốc cho bản tiếp theo. Backend trả conflict cho trường hợp này. UI cần phản ánh quyền thao tác và hiển thị lỗi đúng.
4. Các bản ghi cùng phòng phải được nhận diện bằng `readingId`. Kiểm tra các thao tác xác nhận, điều chỉnh, tải ảnh có gửi đúng ID.
5. Sau sửa, chạy E2E browser với API thật và SQL Server QA. Tạo regression test trong khu vực E2E hiện có. Kiểm tra cả tài khoản METER_READER và vai trò bị cấm, dữ liệu nhập thấp hơn mốc, lỗi API và trạng thái kỳ đóng.

Phạm vi đầu tiên nên hoàn tất một luồng chuyển phòng → ghi chỉ số cho khách mới → tính tiền → hóa đơn trên giao diện. Tránh quay lại chỉ làm thêm vài API assert mà bỏ qua frontend.

## 3. Git và cách bảo toàn công việc

- Repo `C:\VoiVoi\projava1\MiniBoardingHouse`, workspace ghi được `C:\VoiVoi\projava1`.
- Shell PowerShell, Java 17, Spring Boot 3.3.5, frontend Vite + TypeScript.
- Branch `main`, HEAD `a7a4b73` với tiêu đề “Cập nhật nghiệm thu mẫu hợp đồng và điện nước”. Git ghi ahead 12 so với remote tracking hiện có. Chưa xác minh remote mới nhất.
- Có nhiều thay đổi chưa commit và nhiều file untracked. Tại thời điểm bàn giao, diff các file tracked gồm 68 file, khoảng 1.245 dòng thêm và 216 dòng xóa. Số này chưa tính file mới untracked.
- Đọc `git status --short --branch` và `git diff` trước khi sửa. Giữ công việc của người dùng và các lượt trước. Không tự reset, clean, revert, xóa dữ liệu hoặc tác động production.
- Chưa commit/push các thay đổi này. Trước đó có quy ước chỉ push khi người dùng nói “sync”.
- GitHub từng không kết nối được cổng 443, `gh` chưa có trong PATH. Người dùng từng dán PAT trong chat. Không chép PAT vào tài liệu, file hay lệnh. Xác thực lại bằng cơ chế thông thường nếu cần.

## 4. Bảng nghiệm thu và phần chưa ghi vào bản chính

File chính đang được mở/khóa ghi. Có file khóa `docs/.~lock.NGHIEM_THU_123_USE_CASE.xlsx#`. Không xóa file khóa để ép ghi.

`target/acceptance-pending-service-fees.xlsx` là bản tạm tích lũy bằng chứng mới nhất của các lượt bị khóa. Cần hòa các ghi chú mới theo mã UC vào bản chính khi người dùng đóng Excel, bảo toàn chỉnh sửa người dùng thực hiện sau đó. Không chép đè toàn bộ bản chính bằng một snapshot cũ.

Bằng chứng chỉ có ở bản tạm tại lần kiểm tra cuối:

- UC34, UC70, UC73, UC74 về phí theo người/xe/cố định, kỳ tính 9 và hóa đơn 8.
- UC68 về tính tiền thuê tháng thường và năm nhuận.

File chính có các sheet `Tổng hợp`, `UC1–UC123`, `QA smoke`, `Backend handoff`, `QA remaining`. Sheet UC gồm 124 dòng, 5 cột là mã UC, tên yêu cầu, mức xác minh, bằng chứng/điều kiện còn thiếu và file đối chiếu.

Trạng thái cột C và một số câu “cần QA” còn cũ dù cột D đã bổ sung bằng chứng. Chưa chuẩn hóa thành 123 kết quả E2E theo tiêu chí mới. Không suy số PASS từ nhãn hiện tại. Các tài liệu SRS trong `docs/` cũng có mô tả trạng thái cũ, cần đối chiếu source khi dùng.

## 5. Backend đã sửa, cần giữ và tích hợp

Đây là các nhóm thay đổi chính trong worktree, không phải danh sách chứng nhận E2E hoàn tất.

| Nhóm | Mã nguồn và hành vi |
| --- | --- |
| Phòng và khách thuê | `chi/service/RoomService`, `TenantService`, controller/DTO liên quan. Trạng thái CLEANING chặn giao phòng; ngày cư trú; bảo vệ ảnh CCCD |
| Hợp đồng | `phi/service/ContractService`, `ContractEndService`, DTO/entity. Điều khoản điện/nước/dịch vụ, thu cọc, chốt chỉ số và chuyển phòng |
| Tính tiền sau chuyển phòng | `BillingService`, `MeterReadingService`, `MeterReadingRepository`, entity `MeterReading`. Liên kết chỉ số chốt với hợp đồng cũ; cộng điện nước cả hai phòng; tách chỉ số của khách mới trong cùng kỳ |
| API chỉ số | `MeterDtos.ReadingResponse` có `closingContractId` nullable. Cả danh sách và chi tiết trả trường này; thứ tự danh sách theo mã phòng rồi readingId |
| Tiền và biên nhận | Khóa và đọc lại dữ liệu khi thu tiền, cấn trừ, hủy cấn trừ, đảo giao dịch và lập phiếu thu. API biên nhận người thuê, bản in ẩn ghi chú nội bộ |
| Lịch và thông báo | `BillingJobs`, `ContractExpiryService`, `DebtService`, `NotificationSender`. Lịch chuẩn bị nháp/phát hành sau duyệt; thông báo trong ứng dụng và chống trùng |
| Tích hợp ngoài | File mới SMTP, Twilio, payOS. Cần đọc cấu hình và kiểm thử thực tế. Chưa đủ điều kiện kết luận đã tích hợp E2E |
| Sao lưu | `BackupService` đọc lịch sử `msdb`, xác minh header đúng database/full backup và checksum trước restore, xử lý trả MULTI_USER khi restore lỗi |
| Sự cố và thiết bị | Ảnh được bảo vệ, danh mục active, phân công/xử lý/chi phí/đánh giá, điều chỉnh hóa đơn và phí thiết bị |

Migration mới đều chưa commit:

- `db/patch_11_room_cleaning_status.sql`
- `db/patch_12_tenant_residence_dates.sql`
- `db/patch_13_contract_service_terms.sql`
- `db/patch_14_transfer_closing_reading.sql`
- `db/patch_15_segmented_meter_readings.sql`

Hai schema tạo mới `db/group-7.sql` và `db/minihouse-full.sql` đã được cập nhật. Patch 14 thêm `meter_readings.closing_contract_id`; patch 15 đổi unique key từ phòng/kỳ sang phòng/kỳ/hợp đồng chốt. Một bản định kỳ chưa gắn hợp đồng vẫn có `closing_contract_id=NULL`.

Patch 14 không tự suy hợp đồng cho chỉ số chuyển phòng đã được ghi trước patch. Có thể cần migration dữ liệu cũ có kiểm chứng. Đã ghi giới hạn này trong nghiệm thu.

## 6. Môi trường QA, tiến trình và cách chạy lại

Database QA dùng ở các lượt gần đây là **MiniBoardingHouseQA_20261122**. Đây là database dùng cho dữ liệu tổng hợp của agent. Luôn xác minh `SELECT DB_NAME()` trước mọi thay đổi. Không dùng database mặc định từ cấu hình local nếu chưa xác nhận.

Tài khoản SQL nằm trong `src/main/resources/application-local.properties`. Đọc cục bộ khi cần, không in/chuyển giá trị bí mật vào log hay commit. URL QA được các helper cũ lấy từ `%TEMP%\StaylyQaNotification.java` rồi thay `databaseName` thành tên QA ở trên.

### Quan sát trực tiếp khi bàn giao

| Cổng | Kết quả GET /api/auth/me |
| --- | --- |
| 18087–18093 | Không kết nối được |
| 18094, 18095, 18097, 18098 | HTTP 401, có tiến trình phục vụ API |
| 18096 | Không kết nối được |
| 5178 | HTTP 500 cho API qua proxy, vì target 18091 đã dừng |

Các cổng còn chạy xuất phát từ thử lịch hợp đồng, nhắc nợ và hóa đơn. Có thể còn cron QA bật. Lệnh của các lượt trước từng bị ngắt nên có tiến trình con còn sống dù shell đã thoát. Không giả định có thể dùng instance đó làm baseline source mới nhất. Xác minh command line, phiên bản và target DB trước khi dừng hoặc tái sử dụng. Không stop mọi Java process trên máy.

Khi chạy instance QA cho E2E, tắt tác vụ tự động để dữ liệu kiểm thử không bị biến đổi ngoài thao tác giao diện. `app.jobs.enabled` và `minihouse.scheduler.enabled` điều khiển hai cơ chế lịch khác nhau, cần kiểm tra cả hai. Chỉ bật cron riêng trong test yêu cầu lịch.

### Backend

Build chuẩn qua Maven khi dependencies truy cập được. Repo không có Maven wrapper ở root tại lúc kiểm tra. Các lượt cũ bị hạn chế truy cập Maven Central và đã dùng classpath cục bộ để compile và chạy QA.

`target/backup-check/` chứa bản classes, dependency JAR, Java helper và boot log được dùng gần đây. Có thể chạy classpath QA từ repo với dạng:

```powershell
java -cp 'target/backup-check/classes;target/backup-check/*;src/main/resources' com.group7.minihouse.MiniBoardingHouseApplication --spring.profiles.active=local --server.address=127.0.0.1 --server.port=18091 --spring.jpa.hibernate.ddl-auto=validate --app.security.enabled=true --app.jobs.enabled=false --minihouse.scheduler.enabled=false
```

Lệnh trên cần `SPRING_DATASOURCE_URL` được đặt rõ tới QA trước khi chạy. Dùng biến môi trường cho thông tin nhạy cảm. Classes trong target có thể cũ hơn source sau các lượt mới, nên compile lại các thay đổi hoặc build đầy đủ trước khi kiểm thử kết quả sửa.

Lịch sử có 450 JUnit thành công với bộ dependencies/classes chọn lọc. Ba class phụ thuộc SMTP từng được loại khỏi bộ compile thủ công do thiếu dependencies. Kết quả 450 không chứng minh full Maven build hiện tại hoặc toàn bộ E2E PASS. Kiểm tra runner/bộ class cụ thể trước khi tái sử dụng số liệu.

Các helper trong target gồm `TransferQaMigration.java`, `SegmentQaMigration.java`, `BackupQa.java`, `BackupQaCleanup.java`, `BillingJobQa.java`, `ExpiryCountQa.java`, `DebtCronQa.java`, `InvoiceReminderQa.java`, `PrepaymentSeedQa.java`. Một số helper cũ có lệnh tạo fixture hoặc restore. Đọc trước khi chạy, tránh tạo lặp hay chạy restore ngoài test được phép.

### Frontend và browser

`frontend/package.json` có `dev`, `build`, `preview`. Build mặc định ghi vào `src/main/resources/static` và `emptyOutDir=true`; đó là artifact dùng bởi ứng dụng. Khi thử QA có thể build vào target riêng để tránh thay artifact đang dùng.

Vite config chuẩn proxy tới localhost:8080. Config QA đã tạo trong `target/vite.e2e.config.mjs` proxy tới 18091. Khởi động từ thư mục frontend:

```powershell
node node_modules/vite/bin/vite.js --config ../target/vite.e2e.config.mjs
```

Playwright đã có ở `audit/e2e/node_modules`, Chromium cache ở `%LOCALAPPDATA%\ms-playwright`. Tạo/chạy test từ `audit/e2e/` để resolve `import { chromium } from 'playwright'`. Node ở repo root hoặc Temp không resolve được package này.

`audit/e2e/checkin-api-failure.mjs` hiện có interception trả lỗi API giả lập. Có thể tái sử dụng cách tổ chức để kiểm tra lỗi, nhưng không dùng script đó làm bằng chứng E2E SQL Server thật. `audit/e2e/audit-ui.mjs` và `screens/` có sẵn, cần đọc để xác định phiên bản/bằng chứng còn phù hợp.

Frontend lưu JWT trong sessionStorage key `stayly.session`, khu trọ trong localStorage key `stayly.property`. Mặc định property 1. Tháng làm việc do `frontend/src/lib/ctx.ts` quản lý. API client xử lý cả JSON thô và envelope `{success,message,data}`. Một số POST/DELETE thành công trả body rỗng; test phải chấp nhận đúng contract.

Khi viết test login, dùng credential fixture có sẵn hoặc tạo tài khoản QA. `%TEMP%\stayly-round2-browser.mjs` có credential fixture cũ; không in file nguyên khối hoặc đưa credential vào report. `functions.store/load` của phiên trước không phải artifact cho agent mới, đừng phụ thuộc vào chúng.

Đã đọc skill `impeccable`, chạy context loader và đọc craft floor; cũng đã đọc skill `design-taste-frontend`. Cần bảo toàn UI hiện có và ưu tiên sửa tính đúng của thao tác. `PRODUCT.md` và `.impeccable/surfaces/frontend-index-html.md` có vài mô tả lịch sử đã cũ, ví dụ Chi chưa có API và frontend chỉ một khu. Code hiện tại đã có API Chi và chọn property.

## 7. Bằng chứng thành phần đã có để tái sử dụng

Hầu hết danh sách dưới là API → SQL Server QA. Muốn đánh dấu E2E theo goal mới cần nối thêm bằng chứng frontend tương ứng.

| Nhóm | Bằng chứng đã thực hiện |
| --- | --- |
| Tài khoản, quyền | 5 vai trò × 5 route đại diện đúng 200/403; anonymous 401; khóa tài khoản vô hiệu JWT. Tài khoản manager mới đổi mật khẩu tạm, từ chối mật khẩu cũ/yếu, cập nhật tên/SĐT, ghi nhật ký LOGIN/LOGOUT/CHANGE_PASSWORD/UPDATE_PROFILE |
| Thu cọc/trả phòng | Checkout 2 cọc 500.000, hư hỏng 25.000, hoàn 475.000; checkout 3 cọc vừa đủ; checkout 4 thiếu 5.000 được lưu lịch sử. Checkout 5 cọc 2 triệu, trừ nợ 1,13 triệu trước rồi hư hỏng, còn phải thu 30.000 |
| Chuyển phòng | Hợp đồng 13, phòng 103/104. Chỉ số cũ được liên kết và tính cùng phòng mới. Hóa đơn 4 điện 87.500/nước 180.000. Khách mới ở phòng cũ có chỉ số 548 lấy mốc 35/16, tính riêng 7 điện/3 nước, hóa đơn 5 là 24.500/45.000 |
| Lịch | Tính nháp tự động, chỉ phát hành sau duyệt, không tạo hóa đơn trùng. Mốc ân hạn chốt kỳ 7 ngày. Hợp đồng 16 cảnh báo hết hạn đúng một lần. Nhắc nợ 1/7/15 ngày đúng mức; ngày hẹn trả tương lai được hoãn nhắc |
| Nhắc hóa đơn | Hóa đơn 6 nhắc trước 3 ngày và quá hạn; hóa đơn 7 nhắc trước 1 ngày. Giao diện chưa được chứng minh qua browser cho các bước này |
| Tiền và biên nhận | Hai refund 40.000 trên số dư 50.000 chỉ một thành công. Undo cấn trừ đồng thời chỉ cộng lại một lần. Hai phiếu thu 40.000 trên giao dịch 50.000 chỉ một được tạo; phần 10.000 còn lại thành công, vượt thêm 1 bị từ chối |
| Thanh toán nhiều lần | Hóa đơn 7 có CASH 50.000; BANK_TRANSFER 30.000; hai installments sửa tổng thành 55.000. Đảo transfer đồng thời một 204/một 409, paidAmount còn 105.000. Mã giao dịch trùng bị từ chối |
| Quyền dữ liệu thuê | Chủ hóa đơn thấy biên nhận 6/7 và CASH history. Tài khoản khác nhận 404 cho invoice detail, receipt print và history. Bản in no-store, ẩn ghi chú nội bộ |
| Công nợ | Hóa đơn 6/7 tổng còn nợ 1.015.000; recalc không nhân đôi; phạt ngày được ghi riêng, không cộng vào tổng nợ hóa đơn. Excel export khớp search và summary |
| Sự cố | Request 3 theo danh mục điện/nước từ tenant; OPEN → ASSIGNED → IN_PROGRESS → RESOLVED → CLOSED, chi phí 35.000. Cấm thao tác sai quyền/trạng thái/chi phí âm. Rating 4 thành công, trước hoàn tất, ngoài 1–5 và lần hai bị từ chối |
| Dashboard/báo cáo | Property 4 doanh thu 105.000, chi phí 35.000, nợ 1.015.000, 2/2 phòng rented tại lúc test. Expense CRUD làm thay đổi report đúng rồi khôi phục khi xóa fixture. XLSX báo cáo khớp 12 tháng + tổng năm |
| Người ở/xe | Canonical API CRUD resident và vehicle round-trip đúng. Fixtures đã ngừng sau test. Kỳ 9/hóa đơn 8 xác minh phí người/xe/cố định; bằng chứng mới ở workbook tạm |
| Tiền thuê theo ngày | Hợp đồng từ 15/2 đến 15/3, giá 500.000. Tháng 2/2027 14 ngày là 250.000; 2/2028 15 ngày là 258.621; tháng 3 15 ngày là 241.935. Không tính trước tháng bắt đầu/sau tháng kết thúc. Bằng chứng ở workbook tạm |

QA đã có nhiều dữ liệu tổng hợp, kỳ tương lai tới năm 2028 và tài khoản synthetic được khóa sau kiểm thử. Không mặc định database sạch. Các số dashboard ở bảng này là snapshot; tạo fixture mới có thể thay đổi chúng.

## 8. Blocker và việc cần xác minh tiếp

- SMTP/email, SMS/Twilio và payOS chưa được kiểm chứng giao nhận end-to-end với môi trường test được cấu hình. Đọc `QA remaining`, cấu hình và source, ghi BLOCKED đúng phần thiếu. Không gửi tin nhắn/email ra người nhận bên ngoài khi chưa có ủy quyền cụ thể.
- UC122 backup tạo/list thành công. Restore đã vượt lỗi file 404, nhưng SQL Server từ chối quyền CREATE DATABASE trong master. Đã kiểm tra QA trở lại MULTI_USER và dọn marker thử nghiệm. Cần tài khoản QA đủ quyền và test rollback dữ liệu thật trên database được phép. Không thực hiện restore production.
- Excel chính đang khóa ghi, cần hòa workbook tạm sau khi đóng file.
- Backend QA 18091 đang dừng, proxy frontend chưa sử dụng được. Các QA server khác có thể còn cron. Đây là trạng thái cần xử lý trước browser E2E.
- Chưa có full clean build với toàn bộ SMTP dependencies. Cần thử lại truy cập Maven/resolve cache ở môi trường hiện tại thay vì mặc định blocker cũ còn nguyên.
- Cần kiểm tra regression đầy đủ cho segmentation meter, dữ liệu chỉ số cũ chưa có closingContractId và UI theo vai trò.
- Một số công thức dùng occupancy hiện tại cho phí người/xe khi tính kỳ lịch sử. Các test hiện có chỉ chứng minh thay đổi số lượng rồi tính lại; chưa chứng minh phân bổ theo lịch sử ngày cư trú/phương tiện. Đối chiếu yêu cầu trước khi kết luận đạt.
- Chưa kiểm thử đồng thời việc đánh giá sự cố; source từng chỉ check rating rồi save. Nếu phạm vi nghiệp vụ cần đảm bảo một đánh giá trước concurrent request, tái hiện và sửa bằng khóa/constraint phù hợp rồi thêm regression.

## 9. Trình tự tiếp tục đề nghị

1. Xác nhận người dùng đã yêu cầu resume. Đọc status/diff và các file đang dở ở mục 2.
2. Xác minh các tiến trình local, dựng một instance QA đúng source và tắt jobs, kiểm tra database và `ddl-auto=validate`.
3. Dùng Playwright thật chụp baseline màn meters và tái hiện lỗi mốc chỉ số sau chuyển phòng. Giữ UI hiện có, sửa tích hợp, thêm regression E2E và kiểm tra lại đường đi frontend/API/SQL/UI.
4. Hòa bằng chứng workbook đang chờ, ghi kết quả rõ ở mức E2E hay thành phần, đánh dấu BLOCKED cho phụ thuộc ngoài.
5. Tiếp tục các luồng P0/P1 theo Excel từ frontend thật, sửa thiếu sót và tích hợp còn hở. Tận dụng source và bằng chứng cũ để tránh lặp kiểm tra.
6. Chỉ kết thúc goal khi đủ điều kiện 123/123, hoặc báo rõ blocker thực sự theo quy tắc goal. Không thu nhỏ mục tiêu thành một bộ API smoke pass.

## 10. Đoạn giao việc có thể gửi cho agent mới

> Đọc `docs/CODEX_HANDOFF.md`, kiểm tra Git diff và hai workbook nghiệm thu chính/tạm, rồi tiếp tục công việc đang dở ở mục 2 khi tôi yêu cầu resume. Giữ các thay đổi hiện có. Hoàn thiện frontend và tích hợp E2E thật cho UC1–UC123 theo Excel; ưu tiên P0/P1 và chuỗi IMPLEMENT → TEST → FIX. Backend QA 18091 đang dừng, Vite 5178 proxy về đó; xác minh và dựng đúng QA trước khi dùng browser. Dùng Playwright trong `audit/e2e/node_modules`. Không suy API QA thành E2E PASS. Không tác động production, xóa dữ liệu hoặc tự revert. Đọc blocker và ghi kết quả chính xác; không báo phần trăm ước lượng.
