# Kiểm thử đầu cuối và chạy pilot

## Trạng thái hiện tại

Đây là runbook để chạy thử trên staging. Chưa thể đánh dấu luồng đầu cuối hoặc pilot là đạt chỉ bằng test hiện có.

- Frontend có 4 bài test phiên đăng nhập; đã đưa lệnh này vào Jenkins CI.
- Backend hiện có test đơn vị và test HTTP/tích hợp cần PostgreSQL riêng. Jenkins đã được cấu hình tạo PostgreSQL ephemeral cho `mvn verify`; cấu hình này chưa được xác nhận bằng một lượt chạy Jenkins. Chạy local không cấu hình `POSTIVA_TEST_DB_URL` sẽ tự bỏ qua nhóm test database.
- Chưa có bằng chứng kiểm thử trên trình duyệt thật, điện thoại, SMTP thật, Meta Page token trên Page thử nghiệm, hay alert từ monitoring.
- Pilot cần Page do nhóm/người tham gia kiểm soát. Không dùng token hoặc dữ liệu Page production để chạy kịch bản lỗi.

## Điều kiện trước khi chạy

- Deploy một bản staging có tag cố định, domain HTTPS và database/media tách biệt production.
- Đã chạy migration và backup staging; xác nhận restore gần nhất có thể dùng được.
- SMTP staging có thể gửi OTP và email khôi phục đến hộp thư người kiểm thử.
- Chuẩn bị một Facebook Page thử nghiệm do người kiểm thử có quyền quản lý. Kết nối bằng Page Access Token như tính năng hiện tại; không giả định OAuth đã có.
- Token được nhập qua giao diện bảo mật, không đưa vào ticket, ảnh chụp, script, shell history hoặc log.
- Ghi lại phiên bản ứng dụng, ngày chạy, người kiểm thử, trình duyệt/thiết bị và ID Page thử nghiệm; không ghi token hay mật khẩu.
- Nếu kiểm tra thanh toán, chỉ dùng sandbox; tắt thanh toán live trong pilot.

## Kịch bản đầu cuối

| Mã | Kịch bản | Kết quả cần thấy |
| --- | --- | --- |
| E2E-01 | Đăng ký bằng email thử nghiệm, nhận và nhập OTP đúng | Tài khoản được xác minh một lần; có thể đăng nhập |
| E2E-02 | Nhập OTP sai/hết hạn hoặc đăng ký trùng email | Bị từ chối an toàn, có thông báo hữu ích, không tạo trạng thái tài khoản sai |
| E2E-03 | Đăng xuất, đăng nhập lại, quên/đặt lại/đổi mật khẩu | Email đến đúng người; link/token dùng đúng hạn và chỉ dùng một lần; phiên bị thu hồi theo chính sách |
| E2E-04 | Tải ảnh hợp lệ, ảnh sai định dạng thật, file quá giới hạn và thử truy cập file của tài khoản khác | File hợp lệ dùng được; file giả/định dạng sai/quá cỡ bị từ chối; người khác không đọc được media riêng tư |
| E2E-05 | Kết nối Page bằng token hợp lệ, token sai hoặc token bị thu hồi | Page đúng được lưu; trường hợp lỗi yêu cầu kết nối lại; token không xuất hiện trong API response, log hay giao diện sau khi lưu |
| E2E-06 | Tạo bài có media và đăng ngay lên Page thử nghiệm | Bài chỉ xuất hiện một lần trên Page thử nghiệm; Postiva lưu trạng thái, thời gian và mã bài ngoài nếu Meta trả về |
| E2E-07 | Lên lịch ở múi giờ đã chọn, chờ đến hạn | Thời gian hiển thị đúng múi giờ; backend xử lý đúng thời điểm UTC; kết quả cuối cùng khớp Page |
| E2E-08 | Hai worker cùng lấy một lịch; khởi động lại worker khi lịch đang được xử lý | Chỉ một worker nhận lịch; tình huống kết quả không rõ được hiển thị `UNKNOWN`/cần đối soát, không tự đăng lại mù quáng |
| E2E-09 | Token hết hạn, Meta từ chối, timeout trước phản hồi và timeout sau khi Meta có thể đã nhận bài | Người dùng thấy trạng thái/lý do phù hợp; retry tuân theo giới hạn/backoff; trạng thái mơ hồ không tạo bài trùng |
| E2E-10 | Refresh trang, đăng nhập lại và mở lịch sử bài đăng | Trạng thái bài đăng, lỗi, lần thử và hành động kế tiếp vẫn nhất quán sau khi tải lại |

Với E2E-06, chỉ đăng nội dung thử nghiệm đã được người quản lý Page chấp thuận trên Page thử nghiệm. Với E2E-09, dùng lỗi giả lập hoặc Page/token thử nghiệm; không chủ động gây lỗi trên tài khoản khách hàng.

## Trình duyệt, thiết bị và tải

Chạy toàn bộ luồng E2E-01 đến E2E-10 trên trình duyệt desktop mục tiêu (ít nhất Chrome và Edge nếu cả hai được hỗ trợ). Chạy luồng đăng ký, tạo bài, tải media, kết nối Page, xem lịch và xử lý lỗi trên điện thoại iOS/Safari và Android/Chrome. Ghi lại kích thước màn hình, phiên bản trình duyệt và lỗi giao diện.

Trên staging, tạo nhiều lịch cùng thời điểm và chạy hai worker trở lên; xác nhận mỗi lịch chỉ được claim một lần. Khởi động lại worker/backend trong lúc có lịch đến hạn. Mô phỏng lỗi mạng/Meta tại client giả lập hoặc môi trường test và xác nhận không retry khi kết quả đăng có thể chưa rõ. Không dùng bài đăng công khai thật để tạo tải.

## Tiêu chí đạt để mở pilot giới hạn

- Không có bài đăng trùng trong các kịch bản đồng thời, retry và restart.
- Không truy cập chéo tài khoản đối với bài viết, media, Page hoặc lịch đăng.
- Không có token, mật khẩu, OTP hoặc link đặt lại mật khẩu trong log.
- Đăng nhập, khôi phục tài khoản, upload, kết nối Page, đăng ngay/lên lịch và xem trạng thái đều hoàn tất trên browser/device mục tiêu.
- Mọi lỗi Meta có trạng thái dễ hiểu; `UNKNOWN` được giữ để đối soát và không bị gửi lại tự động.
- Worker restart không làm mất lịch; retry dừng ở giới hạn cấu hình; UTC và múi giờ hiển thị khớp.
- Alert test đến đúng người trực; có cách tạm dừng scheduler, ngắt kết nối Page và hỗ trợ người dùng.
- Không còn lỗi mức nghiêm trọng/cao chưa có biện pháp giảm thiểu được chấp nhận.

## Theo dõi pilot

Khởi đầu với nhóm nhỏ và Page mà nhóm pilot kiểm soát. Theo dõi hàng ngày: số bài đăng thành công/thất bại/`UNKNOWN`, bài trùng, retry, lỗi token, thời gian xử lý, OTP/email lỗi, upload lỗi, chi phí AI, downtime, dung lượng và ticket hỗ trợ. Chỉ mở rộng khi các lỗi nghiêm trọng đã được xử lý và số liệu thực tế ổn định qua kỳ theo dõi đã thống nhất.

Dừng nhận lịch mới và xử lý thủ công nếu có bài đăng trùng, lộ token, truy cập chéo dữ liệu, hoặc trạng thái `UNKNOWN` tăng bất thường. Giữ nguyên log/audit cần thiết nhưng loại bỏ bí mật; liên hệ người bị ảnh hưởng theo quy trình hỗ trợ.

## Biên bản mỗi lượt chạy

Ghi `release tag`, môi trường, thời gian, người chạy, browser/device, các mã kịch bản đã thử, số lượng lịch/bài, kết quả, lỗi/issue ID, backup gần nhất, alert được thử và quyết định tiếp tục/dừng. Chỉ lưu ID Page và mã bài ngoài nếu cần đối soát; không lưu access token.

## Việc cần có để đóng bước này

1. Xác nhận Jenkins chạy các test PostgreSQL với 0 test bị skip; nếu chạy local, chỉ đặt `POSTIVA_TEST_DB_URL` trỏ đến database `postiva_migration_test` riêng, tuyệt đối không trỏ vào database ứng dụng.
2. Cấp staging, SMTP thật cho staging, Page thử nghiệm và kênh nhận alert.
3. Bổ sung/đưa vào CI một bộ test browser E2E chạy trên staging hoặc môi trường tích hợp cô lập; hiện repo chưa có Playwright/Cypress.
4. Chạy ma trận browser/điện thoại, kiểm tra tải/khởi động lại và lưu biên bản pilot theo runbook này.
