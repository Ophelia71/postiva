# Vận hành lịch đăng an toàn

Scheduler dùng PostgreSQL làm nơi claim công việc. Worker mở transaction ngắn, lấy lịch đến hạn bằng `FOR UPDATE SKIP LOCKED`, chuyển trạng thái sang `PUBLISHING`, tăng số lần thử và cấp claim token cùng lease. Worker gọi Facebook/Instagram/Threads sau khi transaction claim đã commit; kết quả và publish log được ghi trong transaction riêng. Vì vậy nhiều instance không claim cùng một lịch, và một cuộc gọi mạng không giữ khóa database.

## Trạng thái và cách xử lý lỗi

- `SCHEDULED`: chờ tới giờ đăng hoặc tới thời điểm retry.
- `PUBLISHING`: một worker đang giữ claim có lease.
- `PUBLISHED`: nền tảng trả mã bài đăng và database đã ghi nhận kết quả.
- `FAILED`: nền tảng từ chối rõ ràng, token/quyền không hợp lệ, hoặc đã hết số lần retry. Người dùng có thể kiểm tra nguyên nhân rồi thử lại thủ công.
- `UNKNOWN`: timeout, mất kết nối, lỗi máy chủ sau khi gửi, thiếu mã bài đăng, worker dừng giữa chừng hoặc không lưu được kết quả cuối. Ứng dụng không tự gửi lại vì nền tảng có thể đã nhận bài.
- `CANCELLED`: lịch bị hủy hoặc chủ tài khoản bị khóa.

HTTP 429 và lỗi kết nối khi chuẩn bị dữ liệu chỉ đọc có thể retry với exponential backoff: mặc định 60 giây, 120 giây, 240 giây… tối đa một giờ và tối đa bốn lần thử. HTTP 4xx khác được xem là từ chối rõ ràng và dừng. Khi gọi thao tác đăng, timeout/mất mạng/lỗi 5xx được chuyển sang `UNKNOWN`; HTTP không cung cấp cơ chế idempotency chung cho các adapter hiện có nên không thể khẳng định exactly-once giữa Meta và database.

Sau khi lease 30 phút hết hạn, worker kế tiếp chuyển lịch còn `PUBLISHING` sang `UNKNOWN`, ghi log và không tự đăng lại. Mặc định một vòng lấy một lịch; có thể điều chỉnh batch nhưng cần bảo đảm thời gian xử lý nằm trong lease.

### Đối soát `UNKNOWN`

Mở trang Lịch đăng, tìm bài có trạng thái “Cần xác minh kết quả”, rồi kiểm tra đúng Page/tài khoản và nội dung trên nền tảng:

1. Nếu bài đã xuất hiện, chọn **Đã đăng** để lưu xác nhận và hoàn tất lịch.
2. Nếu đã kiểm tra và bài chưa xuất hiện, chọn **Chưa đăng · thử lại**. Ứng dụng chuyển lịch thành thất bại đã xác nhận; sau đó người dùng chọn **Thử lại** để tạo lần gửi mới.

Không chọn “Chưa đăng” chỉ vì ứng dụng báo timeout. Nếu xác nhận sai, lần thử sau có thể tạo bài trùng. Những lịch đang `PUBLISHING` khi nâng cấp cũng được đưa sang `UNKNOWN` để kiểm tra trước khi gửi lại.

## Thời gian và cấu hình

Database và API lưu thời điểm dưới dạng instant UTC (`TIMESTAMPTZ`, ISO-8601 có `Z` hoặc offset). Trình duyệt hiển thị theo múi giờ thiết bị của người dùng, gồm cả nhãn viết tắt múi giờ. Khi nhập lịch, frontend chuyển giờ địa phương thành instant trước khi gửi API.

Các cấu hình môi trường có trong `backend/.env.example`:

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `POSTIVA_SCHEDULER_BATCH_SIZE` | `6` | Số lịch claim mỗi vòng; các lịch trong một vòng đăng song song (pool `publishingImmediateExecutor` 6 luồng) |
| `POSTIVA_SCHEDULER_MAX_ATTEMPTS` | `4` | Tổng số lần gửi tối đa cho retry tự động |
| `POSTIVA_SCHEDULER_LEASE_SECONDS` | `1800` | Thời gian worker giữ claim |
| `POSTIVA_SCHEDULER_BACKOFF_BASE_SECONDS` | `60` | Khoảng retry đầu tiên |
| `POSTIVA_SCHEDULER_BACKOFF_MAX_SECONDS` | `3600` | Giới hạn backoff |
| `POSTIVA_LEGACY_SCHEDULE_TIME_ZONE` | `UTC` | Múi giờ diễn giải lịch cũ; giao diện trước đây gửi ISO UTC |
| `POSTIVA_LEGACY_TIMESTAMP_TIME_ZONE` | `UTC` | Múi giờ diễn giải `created_at`/`updated_at`/`published_at` cũ theo JVM |

Giao diện cũ gửi `scheduled_time` dưới dạng ISO UTC trước khi backend bỏ offset, nên `POSTIVA_LEGACY_SCHEDULE_TIME_ZONE=UTC` giữ nguyên thời điểm lịch. Các cột `created_at`, `updated_at` và `published_at` là thời gian ghi nhận của JVM; dùng `UTC` nếu ứng dụng cũ chạy trong Docker mặc định hoặc `Asia/Ho_Chi_Minh` nếu chạy trên host dùng giờ Việt Nam. Xác định đúng cách chạy backend cũ, sao lưu và kiểm tra trên bản restore trước khi migrate. V6 đổi cột lịch sang `TIMESTAMPTZ`, thêm lease/backoff và đưa các dòng `PUBLISHING` cũ vào `UNKNOWN`; migration không tự chạy trên database vận hành từ thao tác sửa mã nguồn.

## Kiểm thử

Unit test bao phủ lỗi sau khi gửi, timeout không retry mù, che access token khỏi thông báo lưu trữ, HTTP 429 backoff và token hết hạn trước khi publish. `ScheduledPostClaimIntegrationTest` dùng PostgreSQL thật để kiểm tra hai worker cạnh tranh và phục hồi lease khi worker khởi động lại. Test này chỉ chạy khi `POSTIVA_TEST_DB_URL` trỏ tới database local chuyên dụng `postiva_migration_test`; không cấu hình biến này trên database người dùng hoặc production.

Các test mock không xác minh Meta đã nhận bài, quyền ứng dụng, thời gian phản hồi production, hay thứ tự event khi dừng tiến trình thật. Trước pilot, cần chạy migration trên staging, bật test PostgreSQL integration, rồi diễn tập timeout và khởi động lại bằng tài khoản/Page thử nghiệm có thể kiểm tra bài đã đăng.
