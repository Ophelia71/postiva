# Tình năng đã làm và mức độ sẵn sàng của Postiva

Ngày rà soát: 17/10/2026  
Phạm vi: mã nguồn và tài liệu triển khai trong repository, cùng kết quả kiểm tra cục bộ đã ghi nhận. Tài liệu này không xác nhận trạng thái máy chủ production, quyền ứng dụng Meta hoặc tài khoản thanh toán bên ngoài.

**Cập nhật riêng phần thanh toán 17/10/2026:** đã có luồng payOS trong mã nguồn nhưng tắt mặc định; giá/policy/merchant và giao dịch thật chưa được xác nhận. Các tỷ lệ tổng thể bên dưới là ảnh chụp lần rà soát trước và chưa được tính lại sau những thay đổi bảo mật, AI và thanh toán tiếp theo. Xem [hướng dẫn payOS](BILLING_PAYOS.md).

## Kết luận cho người dùng và chủ dự án

Postiva đã có nền tảng để tạo nội dung, quản lý bài, kết nối mạng xã hội bằng token và lên lịch đăng; gần đây đã bổ sung bảo vệ đăng nhập/OTP và giới hạn sử dụng AI. Tuy nhiên, các phần cốt lõi để vận hành dịch vụ thương mại vẫn còn thiếu hoặc chưa được kiểm chứng: lịch đăng chống xử lý trùng, OAuth tự phục vụ, thanh toán/cấp gói Pro, CI và vận hành production, và kiểm thử toàn hành trình trên trình duyệt.

**Ước lượng tổng thể:** khoảng **60% hoàn thiện chức năng MVP trong mã nguồn**; khoảng **45–50% sẵn sàng cho pilot người dùng bên ngoài có giám sát**; khoảng **30–35% sẵn sàng mở công khai và thu phí**. Đây là ước lượng theo các luồng nghiệp vụ quan trọng và rủi ro phát hành, không phải tỷ lệ dòng code. Tích hợp dịch vụ ngoài và production chưa được xác minh nên không được tính là đã sẵn sàng.

**Khuyến nghị hiện tại:** chưa mở đăng ký công khai hoặc nhận tiền. Cơ chế scheduler đã được bổ sung trong mã nguồn nhưng cần chạy kiểm thử PostgreSQL đồng thời, áp dụng migration có kiểm soát và diễn tập với Meta trên staging. OAuth một nền tảng, bảo mật/vận hành production và luồng người dùng đầu cuối vẫn cần hoàn thiện; chỉ triển khai thanh toán thật sau khi đã chốt chính sách gói và cơ chế đối soát.

## Chức năng đã làm

| Chức năng | Nội dung hiện có | Mức hoàn thiện ước tính |
|---|---|---:|
| Tài khoản và xác thực người dùng | Đăng ký bằng email và OTP; đăng nhập JWT; kiểm tra trạng thái ACTIVE/LOCKED; giới hạn yêu cầu login, đăng ký và xác thực OTP theo tài khoản/IP; OTP được lưu dạng hash, có thời hạn và khóa sau nhiều lần nhập sai. | 80% |
| Quản trị người dùng | Admin có thể tìm kiếm và phân trang danh sách, lọc trạng thái, xem chi tiết, khóa/mở tài khoản kèm lý do và xem nhật ký thao tác. Có cấp admin bằng cấu hình nội bộ an toàn; không tự nâng quyền người dùng thường. | 90% |
| Bảo mật tài khoản admin | MFA TOTP, mã khôi phục, đổi mật khẩu admin, ghi nhận sự kiện bảo mật và yêu cầu MFA đã xác minh cho API admin. | 85% |
| Tạo và quản lý nội dung | Tạo nội dung bằng OpenAI; xem và chỉnh sửa bài; quản lý trạng thái bài, hashtag/nội dung và media liên quan. Có giới hạn chi phí theo số yêu cầu AI. | 70% |
| Free/Pro và quota AI | Có kỳ sử dụng và quota tách biệt vai trò USER/ADMIN; mặc định Free dùng thử 30 và Pro 50 lượt/kỳ 30 ngày; giữ chỗ quota nguyên tử, giới hạn theo ngày, chống lặp bằng Idempotency-Key, lịch sử trạng thái yêu cầu và hoàn quota khi lỗi rõ ràng. Quota này là chính sách dùng thử, không phải giá bán. | 70% |
| Lịch đăng và đăng ngay | Có tạo lịch, danh sách lịch, đăng ngay, thử lại thủ công, lưu trạng thái và log; adapter đăng Facebook, Instagram và Threads; khi khóa user thì lịch chờ bị hủy. Scheduler đã có claim nguyên tử, lease, retry giới hạn, trạng thái UNKNOWN và đối soát thủ công; API lưu UTC và trình duyệt hiển thị giờ địa phương. | 75% |
| Kết nối mạng xã hội | Có API nhập token thủ công, lưu token đã mã hóa, kiểm tra/ngắt kết nối; có thao tác thử đăng và đồng bộ bài Facebook. Luồng này yêu cầu người dùng tự lấy token. Giao diện và hướng dẫn hiện ghi rõ phạm vi pilot/Page token và giới hạn OAuth. | 45% |
| Xóa dữ liệu/deauthorization Meta | Có signed callback; status GET public không JWT, URL HTTPS lấy từ cấu hình. Deauthorization hủy lịch/giữ UNKNOWN nếu đang gửi; data deletion dọn lịch/log và metadata Meta. Chưa thể bảo đảm xử lý đúng token thủ công vì chưa lưu app-scoped user ID; chưa xác minh với Meta/production. | 55% |
| Giao diện ứng dụng | Có trang đăng nhập, tạo bài, lịch đăng, kết nối, báo cáo, chính sách riêng tư; giao diện admin cho người dùng và bảo mật; có hiển thị quota/lịch sử sử dụng AI. | 65% |
| Database và triển khai cơ bản | Có PostgreSQL, Flyway V1–V11 trong mã nguồn, Hibernate validate, Docker Compose, Nginx TLS và Jenkins pipeline. V10 đã migrate trên database thử riêng; chưa áp dụng migration mới lên database đang phục vụ người dùng. | 60% |

## Chức năng còn thiếu hoặc mới làm một phần

| Chức năng / điểm cần xử lý | Tình trạng và việc cần làm | Mức hiện tại |
|---|---|---:|
| Kiểm chứng lịch đăng trên PostgreSQL/Meta | Mã nguồn đã tách claim và gọi nhà cung cấp khỏi transaction, dùng `SKIP LOCKED`, lease 30 phút, tối đa 4 lần retry có backoff, không tự gửi lại khi kết quả sau publish không rõ, và hỗ trợ đối soát `UNKNOWN`. Còn cần chạy test PostgreSQL cho hai worker/khởi động lại, kiểm tra migration V6 với múi giờ dữ liệu cũ và diễn tập timeout/token hết hạn trên staging. | 65% |
| OAuth tự phục vụ | Chưa có authorize/callback OAuth trong luồng hiện dùng; pilot có thể kết nối Facebook Page bằng token thủ công nếu người tham gia có quyền và app/token được Meta cho phép. Cần chọn một nền tảng phát hành đầu tiên, triển khai state một lần gắn với phiên/người dùng, callback/redirect chính xác, lưu và làm mới token an toàn, xử lý thu hồi/kết nối lại. | 15% |
| Duyệt quyền Meta thực tế | Chưa xác minh Meta App Dashboard, quyền nâng cao/App Review, xác minh doanh nghiệp hoặc đăng bài bằng tài khoản production được cho phép. Đây là công việc cấu hình/xét duyệt bên ngoài, repository không đủ bằng chứng để kết luận đã được duyệt. | Chưa xác minh |
| Xóa dữ liệu/deauthorization Meta | Chưa có liên kết app-scoped user ID cho tài khoản Page Access Token thủ công; callback có thể xử lý 0 kết nối dù trong Postiva còn token/dữ liệu. Cần bổ sung cách liên kết danh tính được Meta hỗ trợ (OAuth hoặc tương đương), xác nhận xử lý không khớp an toàn và thử callback/URL HTTPS thật trên staging trước khi công bố đã hỗ trợ. | 35% |
| Thanh toán và cấp gói Pro | Đã có đơn payOS tạo phía backend, chữ ký webhook/tra cứu, giá và quota chụp theo đơn, chống lặp, cấp Pro/gia hạn, hủy link, yêu cầu hoàn và xác nhận hoàn thủ công có audit, đối soát trạng thái nền, màn khách/admin. Tắt mặc định; cần chủ dự án chốt merchant, giá, chính sách/thuế/chứng từ, kiểm chứng payOS giao dịch thật và đối soát sao kê trước khi nhận tiền khách. | Khoảng 70% mã nguồn; chưa sẵn sàng live |
| Quên/đặt lại/đổi mật khẩu người dùng | OTP đăng ký và bảo mật admin đã có; luồng quên/đặt lại mật khẩu cho USER chưa hoàn chỉnh. Cần token một lần có hạn, giới hạn gửi, thu hồi phiên phù hợp và email thật. | 20% |
| Thống kê và bình luận | Đã bỏ trường số liệu giả khỏi API/giao diện; chi tiết bài và báo cáo ghi rõ lượt xem/tương tác/bình luận chưa đồng bộ; endpoint bình luận trả 501 thay vì danh sách rỗng. Chức năng đồng bộ và quản lý bình luận vẫn chưa được hỗ trợ. | 25% |
| Trạng thái đăng và thông báo | Lịch/báo cáo tự làm mới theo chu kỳ; lịch hiển thị lỗi gần nhất, số lần thử và thời điểm retry; có thông báo trạng thái khi worker cập nhật, hỗ trợ thử lại FAILED và xác nhận UNKNOWN. Cần kiểm chứng toàn luồng trên Meta/staging và gửi thông báo ngoài ứng dụng nếu sản phẩm yêu cầu. | 65% |
| An toàn lưu trữ media | Upload hiện dùng lưu trữ local; kiểm tra loại file còn dựa vào MIME từ client; endpoint GET/HEAD file public nên ai có URL có thể tải media. Cần kiểm tra nội dung file, quyền truy cập/URL ký có hạn, giới hạn dung lượng, dọn file mồ côi và backup media. | 40% |
| CI và quy trình phát hành | Jenkins đang build/push/deploy nhưng chưa có gate test/lint và smoke test sau deploy; Compose dùng `latest`. Cần chạy kiểm thử trong CI, gắn image theo tag/digest bất biến, health check và rollback. | 35% |
| Backup, giám sát và khôi phục production | Mới có bằng chứng backup/restore migration ở local; chưa xác minh lịch backup production, restore định kỳ, giám sát lỗi đăng bài/chi phí/dung lượng, cảnh báo và mục tiêu khôi phục. Cần xác minh môi trường thật, gồm cả database, media và khóa mã hóa. | 30% |
| Kiểm thử toàn hành trình | Có unit/integration test backend và kiểm tra phiên frontend; chưa có kiểm thử E2E trình duyệt, thiết bị di động, đồng thời nhiều worker, SMTP/OpenAI/Meta thật hoặc giao dịch thanh toán thật. | 35% |
| Chính sách và hỗ trợ khách hàng | Cần chốt phạm vi dịch vụ, chính sách dữ liệu/xóa tài khoản, giá/gói, quy trình hỗ trợ tài khoản/MFA và nội dung công bố đúng với tính năng thực tế. | 25% |

## Ưu tiên trước khi đưa cho người dùng thật

1. **Pilot Meta giới hạn bằng Page Access Token:** dùng Page thuộc người tham gia pilot; làm theo [hướng dẫn token và phạm vi Meta](META_PAGE_TOKEN_PILOT.md), bấm kiểm tra trước khi lên lịch, theo dõi token lỗi và không quảng bá OAuth là đã có.
2. **Xác minh lịch đăng:** chạy test PostgreSQL đồng thời và khởi động lại worker, áp dụng V6 trên staging sau khi chốt múi giờ cũ, diễn tập timeout/token hết hạn; chỉ sau đó mới bật lịch tự động cho pilot giới hạn.
3. **Hoàn thiện OAuth cho một nền tảng song song:** chọn nền tảng và quyền cần dùng; làm authorize/callback, kết nối lại, rồi kiểm chứng bằng ứng dụng/tài khoản thực được cấp quyền. Sửa callback tình trạng xóa dữ liệu.
4. **Đóng các lỗ hổng vận hành và bảo mật:** đặt lại mật khẩu USER, kiểm soát truy cập media, kiểm tra file upload, giới hạn dung lượng và cấu hình proxy/secret production.
5. **Thiết lập CI và pilot có giới hạn:** test/lint gate, image cố định, backup/restore database và media, giám sát/cảnh báo; kiểm thử toàn hành trình trên trình duyệt với email và tích hợp sandbox/test.
6. **Chốt thương mại và kiểm chứng thanh toán thật:** duyệt giá/quota/quyền lợi, merchant/pháp nhân, chính sách hoàn tiền, hóa đơn; kiểm chứng webhook, đối soát và hoàn tiền trên payOS với giao dịch thử có kiểm soát; sau đó mới bật công tắc nhận tiền khách.
7. **Hoàn thiện analytics/bình luận hoặc loại khỏi phạm vi MVP:** không quảng bá chỉ số chưa đồng bộ như dữ liệu thật.

## Bằng chứng và giới hạn đánh giá

- Backend: full suite ngày 17/10/2026 đã chạy với PostgreSQL test chuyên dụng và **151 test pass, 0 lỗi, 0 bỏ qua**. Có test migration V1–V11, trạng thái thanh toán/gia hạn/hoàn tiền, webhook lặp/sai số tiền và backoff đối soát. Scheduler unit test có kiểm tra timeout/mất kết nối, che access token, 429 backoff và token hết hạn; kiểm thử tải nhiều worker trên production vẫn cần thực hiện.
- Frontend: `tsc -b`, lint và Vite production build đã qua; lint còn hai cảnh báo Fast Refresh, JavaScript bundle khoảng 531 kB vượt cảnh báo 500 kB. Kiểm tra phiên đăng nhập frontend **4/4 pass** từ lần rà soát trước.
- Database local: backup, restore thử và đối chiếu giữ nguyên dữ liệu 18 bảng cũ ở lần rà soát trước; migration V10 đã chạy trên PostgreSQL test riêng. Cần kiểm chứng migration V10 trên bản sao dữ liệu staging và backup/restore trước khi áp dụng production.
- Test email/OpenAI dùng mock; chưa xác minh gửi email, OpenAI, Meta hay payment bằng tài khoản production.
- Chưa chạy E2E trình duyệt, kiểm thử tải/concurrency production hoặc xác minh Meta và payOS bằng tài khoản thật. Tài liệu này mô tả repository và bằng chứng cục bộ; không khẳng định phiên bản trên máy chủ đang đồng bộ.

Tài liệu liên quan: [Hướng dẫn pilot kết nối Meta bằng access token](META_PAGE_TOKEN_PILOT.md), [Hướng dẫn vận hành lịch đăng](SCHEDULER_RELIABILITY.md), [Đánh giá khả năng đưa Postiva vào sử dụng thực tế](PRODUCTION_READINESS_REVIEW.md), [Bảo vệ người dùng và quota AI](USER_SECURITY_AND_AI_USAGE.md), [Hướng dẫn database](../database/README.md).
