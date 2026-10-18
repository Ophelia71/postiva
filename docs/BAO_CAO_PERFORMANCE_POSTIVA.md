# Báo cáo performance — Dự án Postiva

**Mục đích:** Phần I, mục 2 — Báo cáo performance dự án công nghệ (15%).  
**Thời điểm lập:** 17/10/2026.  
**Kỳ đo kinh doanh:** [Điền ngày bắt đầu]–[Điền ngày kết thúc].  
**Kỳ đối chiếu Outcome 2:** [Điền kỳ đo và dẫn nguồn báo cáo Outcome 2].  
**Trạng thái:** Bản báo cáo có cấu trúc và kết quả kiểm tra kỹ thuật; cần bổ sung số liệu vận hành thực tế trước khi nộp.
**Phạm vi đo website:** Lighthouse ở chế độ **Desktop** trên URL website đang chạy; không đưa kết quả Mobile vào phần này.

### Kết quả rà soát nguồn số liệu tại workspace (17/10/2026)

| Hạng mục người báo cáo cần | Số liệu có thể xác minh hiện tại | Lý do / nguồn cần bổ sung |
|---|---|---|
| Đăng ký, người dùng hoạt động, tài khoản xã hội kết nối, bài đăng thành công | **Chưa lấy được số thực tế** | Có bảng dữ liệu tương ứng nhưng database ứng dụng không chạy tại máy kiểm tra; “người dùng hoạt động” cần chốt kỳ và hành động hoạt động |
| Người mua Pro, giao dịch, doanh thu | **Chưa lấy được số thực tế** | Có mô hình đơn hàng, nhưng không có database production hay bản đối soát payOS; file cấu hình mẫu để bán gói mặc định `false` không chứng minh trạng thái production |
| Lighthouse Desktop: Performance, Accessibility, Best Practices, FCP, LCP, TBT, CLS | **Đã đo:** 99/100, 95/100, 100/100; 0,75 giây, 0,79 giây, 52,5 ms, 0 | Một lần đo trang chủ `https://postiva.store/` lúc 23:10; xem [báo cáo kết quả](KET_QUA_DO_WEBSITE_POSTIVA_2026-10-17.md) và JSON gốc |
| Uptime, tỷ lệ lỗi API | **Chưa đo** | Có health endpoint và hướng dẫn monitor; chưa có lịch sử external monitoring hay log tổng hợp |
| Bounce rate, session duration, pages/session, scroll depth | **Chưa đo trên website đang chạy** | Đã bổ sung tracking nội bộ trong code; chỉ có dữ liệu từ lúc triển khai bản mới |
| Sessions/users, nguồn truy cập, new/returning | **Chưa đo trên website đang chạy** | Trang Admin → Phân tích web sẽ hiển thị dữ liệu từ migration V12 sau khi phát hành |
| Tỷ lệ đăng ký, tỷ lệ mua Pro, AOV | **Chưa tính được** | Thiếu mẫu số traffic và số liệu người mua/doanh thu đã xác minh |

Các số liệu kỹ thuật có thể kiểm chứng tại workspace là **147/147 backend tests đạt với PostgreSQL thử riêng** và **4/4 frontend tests đạt**. Chúng không thay thế các KPI website hoặc kết quả kinh doanh trong bảng trên.

## 1. Tổng quan sản phẩm và phạm vi đo

Postiva là ứng dụng hỗ trợ tạo nội dung bằng AI, quản lý media, kết nối tài khoản mạng xã hội và đăng bài ngay hoặc theo lịch. Sản phẩm có tài khoản Free/Pro và luồng thanh toán được triển khai trong code. Kỳ báo cáo này cần phân biệt **chức năng đã lập trình** với **kết quả sử dụng thực tế**: một luồng API hay màn hình có sẵn không đồng nghĩa đã có lượt truy cập, khách hàng trả tiền hoặc doanh thu.

Phễu phù hợp với sản phẩm: **truy cập website → đăng ký/xác thực → kết nối tài khoản và tạo bài đầu tiên → đăng bài thành công → sử dụng lặp lại → mua Pro**. Bước “đăng bài thành công” nên tách theo Facebook Page, Instagram Professional và tài khoản Threads vì điều kiện kết nối và lỗi của mỗi nền tảng khác nhau.

## 2. Chỉ số kinh doanh và tăng trưởng (AARRR)

| Giai đoạn | KPI cần báo cáo | Kết quả kỳ này | Outcome 2 | Cách tính / nguồn dữ liệu |
|---|---|---:|---:|---|
| Acquisition | Lượt truy cập website (`sessions`) | Chưa đo | Chưa cung cấp | GA4 hoặc công cụ analytics, lọc bot và traffic nội bộ |
| Acquisition | Người truy cập duy nhất (`users`) | Chưa đo | Chưa cung cấp | GA4; không đồng nhất với số tài khoản đăng ký |
| Activation | Tài khoản đăng ký mới và xác thực thành công | Chưa trích xuất | Chưa cung cấp | Bảng người dùng, phân theo thời điểm tạo/xác thực; loại tài khoản admin/test |
| Activation | Người dùng kết nối ít nhất một tài khoản xã hội | Chưa trích xuất | Chưa cung cấp | Số user duy nhất có kết nối hợp lệ trong kỳ |
| Activation | Người dùng đăng bài đầu tiên thành công | Chưa trích xuất | Chưa cung cấp | Số user duy nhất có trạng thái đăng thành công; tách nền tảng |
| Retention | Người dùng quay lại hoạt động D7/D30 | Chưa đo | Chưa cung cấp | Cohort đăng ký; “hoạt động” cần chốt là tạo bài, đăng bài hoặc dùng AI |
| Referral | Người dùng mới đến từ chia sẻ/giới thiệu | Chưa đo | Chưa cung cấp | UTM/referral code hoặc GA4 source/medium; chưa được chứng minh có tracking |
| Revenue | Tài khoản Pro trả phí thực | Chưa xác minh | Chưa cung cấp | Số user duy nhất được cấp Pro từ giao dịch đã đối soát, loại cấp thử/admin |
| Revenue | Số giao dịch thanh toán thành công | Chưa xác minh | Chưa cung cấp | Đơn hàng đã xác nhận phía máy chủ và đối soát với cổng thanh toán |
| Revenue | Doanh thu thuần (VND) | Chưa xác minh | Chưa cung cấp | Tổng tiền thực thu trừ hoàn tiền; không tính đơn tạo nhưng chưa thanh toán |
| Revenue | AOV (giá trị đơn hàng bình quân) | Chưa tính | Chưa cung cấp | Doanh thu thuần / số giao dịch thanh toán thành công |

**Tỷ lệ chuyển đổi cần trình bày:**

- Đăng ký = tài khoản đăng ký mới / người truy cập duy nhất × 100%.
- Kích hoạt = người dùng đăng bài đầu tiên thành công / tài khoản đã xác thực × 100%.
- Trả phí = người dùng Pro trả phí / người dùng đã kích hoạt × 100%.
- Đăng bài thành công = số yêu cầu đăng có kết quả `PUBLISHED` / tổng số yêu cầu đăng đã kết thúc × 100%; thống kê riêng `FAILED` và `UNKNOWN`.
- D7 retention = số người trong cohort đăng ký quay lại hoạt động vào ngày thứ 7 / tổng người trong cohort đủ 7 ngày × 100%.

**Lưu ý khi tính:** Chốt cùng một kỳ thời gian và múi giờ cho mọi nguồn; phân biệt người dùng với phiên truy cập, giao dịch với khách hàng và bài đăng với yêu cầu đăng. Không coi 30 lượt AI miễn phí là doanh thu. Nếu chưa thu tiền thật, ghi doanh thu thực tế là **“chưa có dữ liệu xác minh”** thay vì lấy giá niêm yết nhân với số tài khoản Pro.

## 3. Website / software performance

| Nhóm | KPI | Kết quả kỳ này | Mục tiêu đề xuất cho pilot | Nguồn đo cần thiết |
|---|---|---|---|---|
| Kỹ thuật | Uptime | Chưa đo | ≥ 99,5% | Sửa `/health` và `/_ops/ready` đang trả HTML, rồi dùng external HTTPS monitor liên tục |
| Kỹ thuật | Lighthouse Desktop: Performance Score | **99/100** | ≥ 90/100 | Một lần đo trang chủ ngày 17/10/2026 |
| Kỹ thuật | Lighthouse Desktop: Accessibility Score | **95/100** | ≥ 90/100 | Có lỗi tương phản màu chữ/nền cần sửa |
| Kỹ thuật | Lighthouse Desktop: Best Practices Score | **100/100** | ≥ 90/100 | Một lần đo trang chủ ngày 17/10/2026 |
| Kỹ thuật | FCP / LCP / TBT / CLS (Desktop) | **0,75 giây / 0,79 giây / 52,5 ms / 0** | Đối chiếu ngưỡng trong Lighthouse | [JSON Lighthouse](performance/postiva-desktop-lighthouse-2026-10-17.json) |
| Kỹ thuật | LCP p75 người dùng thực trên desktop | Chưa đo | ≤ 2,5 giây | RUM/GA4 Web Vitals nếu có; không lấy một lần Lighthouse để suy ra p75 thực tế |
| Kỹ thuật | Tỷ lệ lỗi API 5xx | Chưa đo | < 1% | Reverse proxy/APM; loại request kiểm tra sức khỏe |
| Kỹ thuật | Tỷ lệ đăng bài lỗi/không rõ kết quả | Chưa trích xuất | Theo dõi riêng từng nền tảng; giảm liên tục | Publish logs, đối soát với Meta |
| User behaviour | Bounce/engagement rate | Chưa đo | Thiết lập baseline sau pilot | GA4 với event và định nghĩa trang phù hợp |
| User behaviour | Session duration | Chưa đo | Thiết lập baseline sau pilot | GA4; dùng cùng phương pháp đo giữa các kỳ |
| User behaviour | Pages/session và scroll depth | Chưa đo | Thiết lập baseline sau pilot | GA4 page_view và scroll event |
| Traffic | Nguồn truy cập | Chưa đo | Phân nhóm direct, organic, social, referral | GA4 source/medium, UTM campaign |
| Traffic | New vs returning users | Chưa đo | Theo dõi xu hướng | GA4; đối chiếu thêm user đăng nhập thực tế |
| Conversion | Sign-up rate | Chưa tính | Baseline sau pilot | Số đăng ký từ backend / unique visitors từ analytics |
| Conversion | Purchase rate | Chưa tính | Chỉ tính khi mở thanh toán thật | Người mua Pro thực / user đã kích hoạt hoặc unique visitors; ghi rõ mẫu số |
| Conversion | AOV | Chưa tính | Theo chính sách giá Pro | Doanh thu thuần / giao dịch thành công |

Các ngưỡng pilot là **mục tiêu đề xuất**, không phải kết quả hiện có hay cam kết SLA. Cần thu thập ít nhất một kỳ đo đủ dài và so sánh cùng định nghĩa trước khi kết luận hiệu năng tăng/giảm.

### Bằng chứng kỹ thuật hiện có

- Lần kiểm tra cuối ngày 17/10/2026: backend `mvn verify` với PostgreSQL thử riêng báo **147 test, 0 failure/error/skipped**; frontend **4/4 test đạt**, lint không có lỗi (3 cảnh báo) và build thành công. Đây là bằng chứng kiểm thử code, **không phải uptime hay tỷ lệ lỗi của người dùng thật**.
- Ba assertion migration cũ được cập nhật cùng migration V12; bộ test database cục bộ hiện đạt. CI dùng Java 21/PostgreSQL 16 vẫn cần chạy trên runner phát hành trước khi deploy.
- Repository có health endpoint và hướng dẫn monitoring, nhưng chưa có bằng chứng external monitor, số liệu traffic, Web Vitals, tỷ lệ chuyển đổi hoặc doanh thu thực thu trong workspace.

## 4. So sánh với Outcome 2 và kết luận từ chỉ số

Điền bảng sau **sau khi nhận số liệu Outcome 2 và kỳ đo hiện tại**. Dùng công thức `mức thay đổi = (kỳ này − Outcome 2) / Outcome 2 × 100%`; nếu Outcome 2 bằng 0 thì ghi chênh lệch tuyệt đối, không tính phần trăm.

| Chỉ số | Outcome 2 | Kỳ này | Thay đổi | Vì sao thay đổi? | Hành động tiếp theo |
|---|---:|---:|---:|---|---|
| Unique visitors | [ ] | [ ] | [ ] | [Nêu kênh marketing, thời gian và bằng chứng] | [ ] |
| Đăng ký đã xác thực | [ ] | [ ] | [ ] | [Nêu thay đổi onboarding và bằng chứng] | [ ] |
| Người dùng đăng bài đầu tiên | [ ] | [ ] | [ ] | [Nêu lỗi/điểm nghẽn theo nền tảng] | [ ] |
| Người dùng Pro trả phí | [ ] | [ ] | [ ] | [Nêu trạng thái mở bán, đối soát] | [ ] |
| Doanh thu thuần | [ ] | [ ] | [ ] | [Nêu giao dịch thực thu/hoàn tiền] | [ ] |
| Uptime | [ ] | [ ] | [ ] | [Nêu downtime đã ghi nhận] | [ ] |
| LCP p75 | [ ] | [ ] | [ ] | [Nêu thay đổi frontend/hạ tầng] | [ ] |
| API 5xx rate | [ ] | [ ] | [ ] | [Nêu nguyên nhân từ log/APM] | [ ] |

**Kết luận hiện tại:** Postiva đã có luồng sản phẩm, phép đo Lighthouse trang chủ và code analytics nội bộ cho Admin, nhưng chưa đủ dữ liệu vận hành để chứng minh tăng trưởng hoặc tỷ lệ chuyển đổi thực tế. Cần phát hành bản có tracking, thiết lập external monitor, trích xuất dữ liệu đăng ký/đăng bài và đối soát thanh toán; sau đó điền số liệu cùng kỳ với Outcome 2 và giải thích biến động dựa trên bằng chứng.

## 5. Dữ liệu cần cung cấp để hoàn thiện báo cáo

1. Kỳ báo cáo hiện tại và bản/số liệu Outcome 2.
2. GA4 hoặc analytics: sessions, unique users, source/medium, new/returning, engagement, session duration, page views, scroll, Web Vitals.
3. Backend: số đăng ký/xác thực, kết nối tài khoản, user đăng bài thành công, publish success/failed/unknown theo nền tảng, user quay lại D7/D30. Trích xuất dữ liệu tổng hợp, không đưa email/token vào báo cáo.
4. Thanh toán: số đơn đã thanh toán và đối soát, số người mua Pro, số tiền thực thu và hoàn tiền; nếu chưa mở bán, ghi rõ chưa phát sinh.
5. Monitoring: uptime, latency p75/p95, API 5xx, thời gian và nguyên nhân từng sự cố.

**Nguồn nội bộ để đối chiếu tình trạng kỹ thuật:** [REVIEW_SAN_SANG_PRODUCTION_2026-10-17.md](REVIEW_SAN_SANG_PRODUCTION_2026-10-17.md), [PRODUCTION_OPERATIONS.md](PRODUCTION_OPERATIONS.md), [E2E_PILOT_RUNBOOK.md](E2E_PILOT_RUNBOOK.md).
