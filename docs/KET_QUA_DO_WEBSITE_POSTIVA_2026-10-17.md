# Kết quả đo website Postiva — 17/10/2026

**Website:** https://postiva.store/  
**Phạm vi:** Trang chủ, trình duyệt Chrome Desktop.  
**Thời điểm Lighthouse:** 23:10 giờ Việt Nam (16:10 UTC).  
**Bằng chứng:** [Báo cáo Lighthouse JSON](performance/postiva-desktop-lighthouse-2026-10-17.json).

## 1. Technical Metrics

| Chỉ số | Kết quả đo | Kết luận với dự án |
|---|---:|---|
| Lighthouse Performance | **99/100** | Trang chủ tải nhanh trong phép đo desktop này. Không suy rộng sang mọi trang hoặc mạng của người dùng. |
| Accessibility | **95/100** | Tốt nhưng audit còn lỗi tương phản màu chữ/nền, cần sửa để người dùng dễ đọc. |
| Best Practices | **100/100** | Không có lỗi trong nhóm audit này ở lần đo. |
| FCP | **0,75 giây** | Nội dung đầu tiên xuất hiện nhanh. |
| LCP | **0,79 giây** | Thành phần nội dung lớn nhất xuất hiện nhanh trong phép đo. |
| Total Blocking Time | **52,5 ms** | JavaScript chặn tương tác ít trong phép đo. |
| CLS | **0** | Không ghi nhận dịch chuyển bố cục ở lần đo. |
| Speed Index | **1,13 giây** | Trang hoàn thiện hình ảnh nhanh trong điều kiện lab. |
| HTML và tài nguyên chính | HTML **457 byte**; JS **443.947 byte**; CSS **68.092 byte** sau khi giải mã | Bundle JavaScript là phần lớn dữ liệu cần tải; nên theo dõi khi thêm tính năng. |
| Uptime kỳ báo cáo | **Chưa đo được** | Một thời điểm truy cập thành công không thể chứng minh mục tiêu 99,9%; cần monitor liên tục trong kỳ xác định. |
| Error rate toàn website | **Chưa đo được** | Cần tổng request và số 4xx/5xx từ access log/APM; không suy từ vài request thử nghiệm. |

Một phép đo Node riêng trên trang chủ ghi thời gian lấy HTML khoảng **42–188 ms** trong 5 lần thử; tải HTML và hai tài nguyên JS/CSS chính song song mất **269–1.617 ms** trong 5 lần. Đây là thời gian mạng từ máy đo, **không phải** chỉ số hiển thị màn hình của Lighthouse.

**Phát hiện cần xử lý:** `https://postiva.store/health` và `/_ops/ready` đều trả **HTTP 200 với nội dung HTML trang ứng dụng**, không phải kết quả health/readiness chuyên dụng. Do đó không dùng hai URL đang chạy này làm bằng chứng backend khỏe hoặc để tính uptime đúng nghĩa. `/api/billing/offer` trả 401 khi gọi không có JWT, chứng tỏ request có tới lớp API nhưng không xác nhận thanh toán hoạt động.

## 2. User Behaviour

| Chỉ số | Kết quả | Ý nghĩa đối với Postiva |
|---|---|---|
| Bounce rate | **Chưa có số liệu** | Cho biết khách vào trang giới thiệu rồi rời đi; cần đọc cùng tỷ lệ đăng ký. |
| Session duration | **Chưa có số liệu** | Thời gian ngắn có thể là bỏ cuộc hoặc hoàn thành thao tác nhanh; cần đối chiếu sự kiện tạo/đăng bài. |
| Pages/session | **Chưa có số liệu** | Với ứng dụng React SPA, phải ghi page view khi đổi route mới đo đúng. |
| Scroll depth | **Chưa có số liệu** | Hữu ích cho trang giới thiệu, hướng dẫn và bảng giá; ít ý nghĩa hơn với dashboard. |

Sau phép đo này, repository đã được bổ sung tracking nội bộ và trang Admin → Phân tích web. Bản website đang chạy chưa được triển khai lại trong phiên làm việc này, nên vẫn không có dữ liệu lịch sử để tính bốn chỉ số hành vi từ một lần mở trang.

## 3. Traffic

| Chỉ số | Kết quả | Ý nghĩa đối với Postiva |
|---|---|---|
| Unique users | **Chưa có số liệu** | Đo số người tiếp cận website, khác số tài khoản đăng ký. |
| Sessions | **Chưa có số liệu** | Đo số phiên truy cập; một người có thể tạo nhiều phiên. |
| Traffic sources | **Chưa có số liệu** | Cần biết kênh organic/social/direct/referral nào đưa người dùng có khả năng đăng ký. |
| New vs Returning | **Chưa có số liệu** | Giúp theo dõi thu hút người mới và người quay lại; không thay thế retention của tài khoản đã đăng ký. |

## 4. Conversion

| Chỉ số | Kết quả | Công thức và ý nghĩa |
|---|---|---|
| Sign-up rate | **Chưa tính được** | Số người đăng ký và xác thực thành công / unique visitors × 100%. Đo hiệu quả biến khách truy cập thành người dùng. |
| Purchase rate | **Chưa tính được** | Số người có giao dịch Pro đã xác minh / unique visitors hoặc người dùng đã kích hoạt × 100%; phải ghi rõ mẫu số. Đo hiệu quả thương mại khi mở bán thật. |

Hiện không có dữ liệu phiên truy cập, database production hoặc đối soát thanh toán trong môi trường kiểm tra. **“Chưa có số liệu” không có nghĩa là 0%.**

## Kết luận cho báo cáo

**Trang chủ Postiva đạt hiệu năng desktop tốt trong phép đo lab ngày 17/10/2026** (Performance 99/100, LCP 0,79 giây, CLS 0). Cần sửa lỗi tương phản màu và cấu hình health endpoint đang trả HTML. **Chưa thể kết luận website đạt uptime 99,9%, tỷ lệ lỗi thấp, người dùng tương tác tốt hoặc chuyển đổi cao**, vì các kết luận đó cần dữ liệu monitoring, analytics và thanh toán trong một kỳ đo xác định. Tracking nội bộ vừa được bổ sung vào code, nhưng sẽ chỉ tích lũy dữ liệu sau khi phát hành bản đó. Cần thiết lập monitor ngoài hệ thống và xuất số liệu cùng kỳ để so sánh với Outcome 2.
