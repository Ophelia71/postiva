# Phân tích website trong Admin

Trang **Admin → Phân tích web** hiển thị dữ liệu 7, 30 hoặc 90 ngày gần nhất từ migration `V12__first_party_web_analytics.sql`. Chỉ `ADMIN` đã xác thực MFA mới xem được API `/api/admin/analytics`. Dữ liệu bắt đầu từ lúc triển khai bản có tracking; các kỳ trước không thể khôi phục từ code. Trang hiển thị “chưa có dữ liệu” khi chưa có phiên thay vì gán 0%.

## Định nghĩa và nguồn

| Chỉ số | Cách tính / nguồn |
|---|---|
| Sessions | Số session ID khác nhau được ghi trong kỳ; một tab sau 30 phút không hoạt động tạo session mới |
| Unique visitors | Số browser ID khác nhau trong kỳ; thiết bị/trình duyệt khác nhau được tính riêng |
| New vs returning | Visitor mới nếu lần đầu thấy trong dữ liệu lưu là trong kỳ; visitor quay lại nếu đã có session trước kỳ. Dữ liệu chỉ lưu 180 ngày |
| Traffic sources | DIRECT, ORGANIC, SOCIAL, REFERRAL, PAID, EMAIL, OTHER; dựa trên UTM và domain referrer, không lưu referrer đầy đủ |
| Bounce rate | Session có tối đa một page view và không có tương tác hoặc thời gian hiện trang ≥ 10 giây / tổng session × 100% |
| Session duration | Trung bình `last_seen_at - started_at`; heartbeat 15 giây chỉ khi trang hiển thị và có hoạt động gần đây, nên đây là ước lượng từ sự kiện nhận được, không phải thời gian nhìn màn hình chính xác |
| Pages/session | Tổng page views / tổng sessions; React route change được tính là lượt xem mới |
| Scroll depth | Tỷ lệ page views có mốc cuộn cao nhất ≥ 25/50/75/100%; trang không thể cuộn không mặc định đạt 100% |
| Đăng ký | Số tài khoản USER tạo trong kỳ từ database. Sign-up rate dùng số visitor trong kỳ gắn với tài khoản mới, chia cho unique visitors |
| Thanh toán | Số đơn PAID, người mua, tổng tiền từ database; hoàn tiền SUCCEEDED hiển thị riêng. Purchase rate dùng visitor trong kỳ gắn với người mua trong kỳ / unique visitors. AOV = tiền đơn PAID / số đơn PAID |

Conversion theo visitor chỉ tính các tài khoản liên kết được với phiên truy cập, nên có thể thấp hơn tổng đăng ký/thanh toán trong database nếu người dùng đổi thiết bị hoặc chặn tracking. Không dùng chỉ số này thay sổ đối soát tài chính.

Tracking không lưu IP, user-agent, email, full URL hoặc query string trong bảng analytics; có thể gắn session với user ID sau đăng nhập. Dữ liệu bị xóa sau 180 ngày. Trình duyệt bật Do Not Track hoặc Global Privacy Control sẽ không gửi tracking. Chính sách quyền riêng tư đã bổ sung mô tả này. Endpoint public được giới hạn theo IP, page view có ID chống gửi lặp và một session tối đa 100 page views.

**Chưa nằm trong trang này:** uptime, tỷ lệ lỗi 4xx/5xx và Lighthouse score. Các chỉ số đó cần external monitor, access log/APM và các lần chạy Lighthouse riêng; không suy từ sự kiện hành vi của trình duyệt. Trước khi dùng số liệu cho báo cáo chính thức, kiểm tra production có chạy migration V12, endpoint `/api/analytics/events` trả 204, trang Admin trả dữ liệu theo kỳ và cấu hình Nginx/proxy đang dùng đúng bản phát hành.
