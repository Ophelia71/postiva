# Review code và mức sẵn sàng cho người dùng thật

Ngày đánh giá: 17/10/2026. Đối tượng: snapshot workspace Postiva hiện tại, gồm cả thay đổi chưa commit và file chưa được Git theo dõi.

## 1. Kết luận

**Ước lượng khoảng 65% mức sẵn sàng vận hành sản phẩm cho người dùng thật.** Dự án đã có phần lớn chức năng MVP và nhiều biện pháp bảo vệ đáng giữ lại. Tuy nhiên, còn lỗi có thể gây đăng trùng, xóa không hết bài trên nền tảng, vượt giới hạn lưu trữ và làm sai giới hạn IP. Chưa nên mở đăng ký/thu tiền đại trà ở snapshot này.

- **Pilot miễn phí bằng Page Access Token:** phù hợp sau khi xử lý các mục P1 bên dưới, kiểm chứng hành trình trên staging và có người hỗ trợ trực tiếp.
- **Mở bán Pro:** cần thêm giao dịch thật có kiểm soát, xác nhận quyền lợi/gia hạn/hoàn tiền, backup khôi phục được và cảnh báo có người nhận.
- **OAuth, Instagram, Threads, analytics:** đánh giá theo phạm vi công bố. Không có OAuth chưa phải lý do loại bỏ pilot dùng Page token; chức năng chưa kiểm chứng cần được giới hạn/ghi rõ.

65% là đánh giá kỹ thuật có xét mức độ nghiêm trọng và bằng chứng kiểm tra, không phải chỉ số đo chính xác hay tỷ lệ số dòng code đã viết. Một lỗi P1 vẫn có thể chặn phát hành dù nhiều chức năng khác đã hoàn thành.

| Nhóm đánh giá | Trọng số | Điểm sẵn sàng ước lượng | Cơ sở chính |
|---|---:|---:|---|
| Tài khoản, phân quyền, admin | 20% | 75/100 | MFA, khóa tài khoản, reset mật khẩu, authVersion; còn vấn đề IP qua proxy |
| Đăng bài và scheduler | 20% | 60/100 | Claim nguyên tử, lease, UNKNOWN; còn tranh chấp retry và thiếu chống lặp từ API |
| Quota AI và chi phí | 15% | 75/100 | Giữ quota, idempotency, ngân sách; cần đồng bộ cấu hình token và chính sách Free |
| Media | 10% | 50/100 | Có quyền sở hữu và URL ký; thiếu quota tổng, dọn file chưa đầy đủ, kiểm tra video còn yếu |
| Thanh toán và quyền lợi | 10% | 70/100 | Có kiểm tra server, chống callback lặp, đối soát; chưa kiểm chứng cổng thật |
| Database và CI | 10% | 70/100 | Có Flyway, validate, CI có DB; hiện còn ba test migration lỗi |
| Frontend và trải nghiệm | 5% | 70/100 | Build được, có trạng thái lỗi; thiếu kiểm thử trình duyệt và dữ liệu lớn |
| Vận hành và E2E | 10% | 40/100 | Có script/runbook; chưa xác nhận restore hoàn chỉnh, alert, tải và pilot thực tế |

Tổng trọng số cho ra khoảng 65/100. Đây là đánh giá cho phạm vi MVP đang hướng đến, không tính các tính năng ngoài phạm vi như quản lý agency/team.

## 2. Phạm vi và bằng chứng

Đã khảo sát cấu trúc 186 file Java production, các API, frontend React/TypeScript, migration V1–V11, kiểm thử, Docker/Nginx/Jenkins và script backup/restore. Dùng CodeGraph hỗ trợ lần theo luồng, sau đó đối chiếu source hiện tại và chạy kiểm chứng trọng điểm. Không khẳng định đã chứng minh mọi nhánh của từng file.

Không thay đổi logic ứng dụng trong lần review này. Các chương trình tái hiện nằm trong `backend/target/`; chỉ tạo database thử riêng, đã tắt tiến trình PostgreSQL thử nghiệm sau khi kiểm tra.

| Kiểm tra đã chạy | Kết quả |
|---|---|
| Backend `mvn -Dnet.bytebuddy.experimental=true verify`, không cấp DB test | 127 test được báo cáo: 93 đạt, 34 bị bỏ qua; build thành công |
| Backend `mvn -Dnet.bytebuddy.experimental=true test`, PostgreSQL thử riêng | **127 test: 124 đạt, 3 thất bại, 0 bỏ qua** |
| Frontend `npm test` | 4/4 đạt, phạm vi hiện có là phiên đăng nhập |
| Frontend `npm run lint` | 0 lỗi, 3 cảnh báo hiện có |
| Frontend `npm run build` | Thành công; bundle JavaScript khoảng 500,39 kB, có cảnh báo kích thước |
| Tái hiện IP qua ForwardedHeaderFilter | Header do client cung cấp làm thay đổi khóa giới hạn IP |
| Tái hiện retry bằng Hibernate + service claim thật trên PostgreSQL | Hai worker có thể nhận cùng lịch sau một lần ghi đè trạng thái cũ |
| Tái hiện xóa bài bằng controller thật, mock cổng Facebook | Hai đích đã đăng, chỉ một đích được gọi xóa nhưng bài local vẫn bị xóa |
| Tái hiện orphan bằng schema thật | Xóa bài để lại media `post_id = NULL`, `brief_id != NULL`; cleanup không chọn được |
| Tái hiện xác thực video bằng storage thật | File giả 24 byte được chấp nhận là `video/mp4` |

Môi trường local dùng Java 25.0.2 và PostgreSQL 18.6. CI khai báo Java 21/PostgreSQL 16, cần chạy lại CI trên đúng runtime phát hành. Lỗi build frontend ban đầu liên quan sandbox/`EPERM`; chạy ngoài sandbox đã build thành công, nên không kết luận frontend đang hỏng build.

Log kiểm tra: `backend/target/review-full-tests.log`, `review-backend-default.log`, `review-database-probe.log`, `review-media-deletion-probe.log`. Các file trong `target` có thể bị xóa khi chạy clean.

Chưa kiểm chứng: máy chủ production, SMTP thật, token/quyền Meta thật, giao dịch payOS thật, trình duyệt/điện thoại, tải kéo dài, backup/restore trên hạ tầng thật, kênh cảnh báo và rà soát CVE toàn bộ dependency. Các test tích hợp có database thật vẫn mock một số dịch vụ bên ngoài.

## 3. Các lỗi cần xử lý trước khi mở rộng pilot

P1: cần xử lý trước khi mở rộng tiếp nhận người dùng; P2: cần xử lý trong đợt hoàn thiện trước vận hành rộng hoặc có giới hạn phạm vi rõ ràng.

### R01 — P1: Có thể giả IP dùng cho giới hạn login/OTP

Vị trí: `frontend/nginx.conf:47`, `backend/src/main/resources/application-prod.yml:2`, `backend/src/main/java/com/postiva/auth/security/AuthRequestGuard.java:50`.

Nginx giữ phần `X-Forwarded-For` mà client gửi bằng `$proxy_add_x_forwarded_for`. Profile prod bật `forward-headers-strategy: framework`, nên Spring sửa `getRemoteAddr()` trước khi interceptor kiểm tra IP. Guard sau đó có thể coi IP do client chọn là địa chỉ thực.

**Đã tái hiện bằng thư viện Spring đang dùng:** client thực `203.0.113.50`, header có tiền tố `198.51.100.77`; guard tạo khóa `login-ip:198.51.100.77`.

Ảnh hưởng: kẻ gọi có thể thay IP để né lớp giới hạn theo IP trên login/register/OTP/reset. Giới hạn theo email vẫn còn tác dụng; đây không phải bằng chứng vượt được mọi lớp xác thực.

Hướng sửa: chuẩn hóa hoặc loại bỏ `Forwarded`/`X-Forwarded-*` không đáng tin tại proxy biên; xác định một luồng xử lý IP thống nhất với Spring, kiểm tra cả môi trường có CDN/LB. Thêm test qua chuỗi proxy/filter thật, không chỉ gọi guard với request thô.

### R02 — P1: Retry có thể phá claim của worker và dẫn tới đăng trùng

Vị trí: `backend/src/main/java/com/postiva/scheduler/service/PublishingService.java:158`, `scheduler/entity/ScheduledPost.java`, `scheduler/repository/ScheduledPostRepository.java:22`, `scheduler/service/ScheduledPostStateService.java:187`.

`retry()` đọc trạng thái FAILED rồi sửa entity và save. Không có khóa hàng hoặc `@Version` ở entity này. Claim SQL là nguyên tử, nhưng các thao tác ghi từ JPA chưa cùng tuân thủ quy tắc đó.

**Đã tái hiện bằng hai transaction Hibernate, entity hiện tại và `claimSpecific()` thật:**

1. Hai request cùng đọc lịch ở FAILED.
2. Request thứ nhất lưu SCHEDULED; worker A claim thành PUBLISHING.
3. Request thứ hai lưu entity cũ, đưa lịch về SCHEDULED và xóa claim của A.
4. Worker B claim được cùng lịch trong khi A có thể đang gửi tới Meta.

Kết quả probe: `first worker acquired claim=true`, sau retry cũ `status=SCHEDULED, claim_token=null, attempt_count=0`, rồi `second worker can acquire same schedule=true`.

Hướng sửa: dùng UPDATE có điều kiện theo trạng thái/version và kiểm tra số hàng cập nhật, hoặc khóa phù hợp cho toàn bộ chuyển trạng thái. Rà đồng thời retry, resolve, complete, recover lease, khóa/ngắt kết nối tài khoản. Thêm test đan xen request và worker, không chỉ test hai worker claim một hàng.

### R03 — P1: API đăng bài/lên lịch chưa chống lặp yêu cầu

Vị trí: `backend/src/main/java/com/postiva/scheduler/service/PublishingService.java:101`, `:128`; `social/service/SocialPostService.java:57`.

Mỗi lần gọi tạo một `ScheduledPost` mới. API chưa có khóa idempotency được lưu bền vững cho lệnh publish/schedule. Nếu server đã nhận yêu cầu nhưng client mất response, gửi lại có thể tạo một lịch khác và đăng thêm lần nữa. Claim chỉ chống xử lý đồng thời trên cùng một hàng, không gộp hai hàng do cùng ý định người dùng tạo ra.

Hướng sửa: client giữ ổn định request ID qua retry; backend lưu key, hash payload và kết quả, có unique constraint theo người dùng/key. Với nhiều đích đăng, lưu ý định và từng đích trước khi gọi provider; gửi lại trả trạng thái cũ. Vẫn cho phép đăng lại có chủ đích bằng request ID mới.

### R04 — P1: Xóa bài nhiều Page chỉ xóa một bài Facebook

Vị trí: `backend/src/main/java/com/postiva/appdata/controller/AppDataController.java:551`, `:557`, `:207`; `db/migration/V1__initial_schema.sql:150` và `:165`.

`deletePublishedFacebookPost()` dùng `findFirst()`. Sau khi xóa một external post, `deletePost()` xóa toàn bộ GeneratedPost. Foreign key kéo theo lịch và publish log của các lần đăng còn lại.

**Đã tái hiện:** controller nhận hai schedule Facebook PUBLISHED của cùng post, gọi `facebookGraphClient.deletePost()` đúng một lần rồi xóa post local. Bài ở Page còn lại có thể vẫn tồn tại, nhưng ứng dụng mất dấu ID để quản lý.

Code cũng chưa chặn xóa bài có schedule PUBLISHING/UNKNOWN. Đây là rủi ro thêm khi provider đang xử lý, chưa thử trên Meta thật.

Hướng sửa: phân biệt xóa một lần đăng và xóa toàn bộ; xử lý từng external post, giữ kết quả từng đích, chỉ xóa local khi đủ điều kiện. Dùng trạng thái xóa/đối soát cho thao tác mạng có kết quả chưa rõ; ngăn xóa mất dữ liệu đang được worker sử dụng.

### R05 — P1: Chưa có quota tổng dung lượng media

Vị trí: `backend/src/main/java/com/postiva/file/service/FileService.java:45`, `file/storage/LocalFileStorage.java:48`, `application.yml:51`.

Đã giới hạn mỗi file 10 MB và tối đa 20 file đính kèm một bài. Tuy nhiên, endpoint upload chưa kiểm soát tổng số byte/số file theo người dùng, số lần upload hoặc dung lượng đĩa còn lại. Quota 30 lượt AI không giới hạn upload. Một tài khoản có thể liên tục gửi các file hợp lệ dưới 10 MB và làm đầy volume dùng chung.

Hướng sửa: quota lưu trữ theo tài khoản/gói, giữ dung lượng nguyên tử khi upload đồng thời, trả lại dung lượng khi xóa, rate limit upload và ngưỡng dừng khi đĩa thấp. Thống nhất cách tính khi nhiều metadata trỏ tới cùng `publicId` để tránh tính hoặc xóa nhầm file dùng chung.

### R06 — P1 đối với phát hành: CI có DB hiện thất bại

Vị trí: `backend/src/test/java/com/postiva/database/DatabaseMigrationTest.java:143`, `:173`, `:211`; `Jenkinsfile:55`.

Ba test còn kỳ vọng số migration trước V11:

| Test | Kỳ vọng hiện tại | Thực tế |
|---|---:|---:|
| v5PreservesPendingRegistrationAndAddsEmptyUsageTables | 6 | 7 |
| v9AddsCacheWriteAccountingWithoutChangingExistingCostReservations | 2 | 3 |
| v6PreservesLegacyUtcScheduleTimeAndQuarantinesOldPublishingRows | 5 | 6 |

Đây là phần bị bỏ sót trong lần cập nhật quota trước. Bộ test không có DB đã bỏ qua nhóm này nên cho kết quả xanh. Lần review này đã chạy đủ và xác nhận ba failure trên. Bản thân migration V11 và test tăng quota giữ nguyên lượt dùng đã chạy đạt; không suy diễn ba failure thành lỗi mất dữ liệu migration.

Hướng sửa: cập nhật kỳ vọng hoặc kiểm tra tập phiên bản phù hợp mục đích từng test; giữ việc bắt buộc DB integration test trong CI. Chạy lại toàn bộ với runtime CI trước phát hành.

## 4. Các phần cần hoàn thiện tiếp

### R07 — P2: File của bài đã xóa không được dọn

Vị trí: `backend/src/main/java/com/postiva/file/service/MediaOrphanCleanupService.java:48`, `file/repository/UploadedFileRepository.java:17`, `db/migration/V1__initial_schema.sql:122`.

Cleanup chỉ chọn file có cả `brief_id` và `post_id` bằng NULL. Xóa GeneratedPost chỉ làm `post_id` thành NULL; ProductBrief còn lại giữ `brief_id`. File có metadata nên cũng không thuộc nhóm file không được theo dõi.

Đã thử với schema V1–V11 và một file cũ hơn tám ngày: sau xóa post, media còn `brief_id`; số file được truy vấn cleanup chọn là **0**.

Hướng sửa: định nghĩa vòng đời media và brief, xác định file không còn được nội dung có hiệu lực sử dụng; kiểm tra mọi tham chiếu dùng chung trước khi xóa vật lý. Thêm test xóa bài, gỡ file, đăng lại và cleanup đồng thời.

### R08 — P2: Xác thực video/WebP mới kiểm tra dấu hiệu header

Vị trí: `backend/src/main/java/com/postiva/file/storage/LocalFileStorage.java:129` và `:144`.

MP4/MOV/WebM/WebP chưa được kiểm tra đầy đủ cấu trúc hoặc khả năng giải mã. Một file 24 byte với chuỗi `ftypisom` tại vị trí phù hợp được storage chấp nhận là MP4 dù không có dữ liệu video. Không có bằng chứng thực thi mã từ file này; lỗi đã chứng minh là chấp nhận media không hợp lệ.

Hướng sửa: kiểm tra container/codec/duration/kích thước thật bằng công cụ phù hợp, có timeout và giới hạn tài nguyên; đưa file qua bước kiểm tra trước khi được đính vào bài. Đồng bộ thông báo định dạng/dung lượng giữa frontend, backend và nền tảng. Mức 10 MB cho cả video cũng cần kiểm nghiệm với video thực của người dùng.

### R09 — P2: Callback xóa dữ liệu Meta chưa khớp đầy đủ luồng token thủ công

Vị trí: `backend/src/main/java/com/postiva/social/service/SocialAccountService.java:146`, `:535`, `social/service/MetaSignedRequestService.java:68`.

Luồng lưu Facebook Page hiện lưu Page ID, chưa thiết lập đầy đủ quan hệ với danh tính người dùng của ứng dụng Meta. Bộ lọc callback so ID với Page/provider ID hoặc các khóa metadata không được luồng này ghi. Khi không tìm ra kết nối, service vẫn lưu receipt `completed`, `deletedAccounts=0`.

Giới hạn này đã được nêu trong `docs/META_PAGE_TOKEN_PILOT.md` và trang privacy. Vì vậy chưa thể coi callback công khai + chữ ký hợp lệ là bằng chứng đã xóa đúng mọi dữ liệu.

Hướng sửa: lưu/kiểm chứng mapping danh tính bằng luồng được nền tảng hỗ trợ; có trạng thái cần hỗ trợ/đối chiếu cho trường hợp không khớp. Trong pilot token thủ công, phải có quy trình xóa theo yêu cầu đã xác thực và kiểm tra cả token, lịch, metadata và media liên quan.

### R10 — P2: Scheduler có thể bị chậm bởi các tác vụ nền khác

Vị trí: `backend/src/main/java/com/postiva/scheduler/PublishingScheduler.java:15`, `scheduler/service/PublishingService.java:193`, `billing/BillingService.java:77`, `usage/OpenAiCostReconciliationService.java:36`.

Source chưa cấu hình TaskScheduler riêng hoặc pool cho scheduled tasks. Luồng mặc định dùng chung scheduler; publishing gọi provider đồng bộ, billing duyệt tới 30 đơn và mỗi request provider có timeout. Đối soát hoặc dọn media lâu có thể giữ luồng xử lý và làm trễ các lịch khác. Batch đăng mặc định là một, vòng lặp sau chờ thêm 10 giây.

Hướng sửa: tách việc polling/claim khỏi thực thi mạng bằng executor có giới hạn hoặc queue; tách lịch maintenance/đối soát. Theo dõi queue age và độ trễ đăng. Phải sửa R02 trước khi tăng worker để tránh khuếch đại tranh chấp.

### R11 — P2: Danh sách dữ liệu người dùng chưa phân trang phía server

Vị trí: `backend/src/main/java/com/postiva/appdata/controller/AppDataController.java:277` và `:297`; `scheduler/service/PublishingService.java:152`.

API lấy toàn bộ posts/schedules/logs của tài khoản, rồi lấy media theo từng post. Dữ liệu càng nhiều, payload, truy vấn và RAM càng tăng. Phân trang hoặc lọc sau khi đã tải toàn bộ ở frontend không giải quyết phần backend. Các hàm `safeAllSchedules()`/`safeLogs()` còn biến lỗi đọc thành danh sách rỗng; khi dùng trong thao tác thay đổi dữ liệu, trạng thái thiếu có thể bị hiểu thành không có dữ liệu.

Hướng sửa: phân trang/filter tại truy vấn, batch load media, endpoint aggregate riêng cho dashboard; thao tác xóa/sửa phải dừng rõ ràng khi không đọc được trạng thái phụ thuộc.

### R12 — P2: Restore drill chưa chứng minh phục hồi đầy đủ dữ liệu và media

Vị trí: `scripts/backup-production.sh:49`, `:53`; `scripts/restore-drill.sh:77`.

DB và media được chụp tuần tự khi ứng dụng có thể tiếp tục ghi/xóa. Nếu file bị dọn giữa thời điểm dump DB và tar media, snapshot DB có thể tham chiếu file thiếu. Restore hiện kiểm tra khôi phục SQL, đếm hàng và số file; chưa đối chiếu từng `publicId`/checksum hoặc khởi động ứng dụng trên dữ liệu restore.

Hướng sửa: có phương án snapshot nhất quán hoặc lưu media có version và manifest; ngăn cleanup phá các file thuộc lần backup đang thực hiện. Restore phải kiểm tra mọi tham chiếu media, giải mã token/MFA bằng khóa recovery, đăng nhập và thực hiện smoke test trong môi trường cách ly. Chưa có bằng chứng backup đã được đẩy sang nơi độc lập và alert đã gửi đến người trực.

### R13 — P2: Cấu hình giới hạn token AI chưa điều khiển request thực tế

Vị trí: `backend/src/main/java/com/postiva/ai/service/OpenAiService.java:98`, `usage/AiCostService.java:51`, `application.yml:92`.

Ngân sách giữ chỗ dùng `max-input-tokens`/`max-output-tokens`, nhưng request AI ghi cứng `max_completion_tokens=1800`; chưa có kiểm tra token đầu vào sử dụng cùng cấu hình. Nếu vận hành giảm max output để giảm chi phí, request vẫn gửi 1800 trong khi mức giữ chỗ tính theo giá trị nhỏ hơn. Mặc định hiện cùng là 1800 nên lỗi này lộ rõ khi thay cấu hình.

Hướng sửa: cùng một nguồn cấu hình cho request và dự toán, xác thực đầu vào thực tế hoặc cận trên bảo thủ, test thay cấu hình và xác nhận request/budget đổi cùng nhau. Giữ việc ghi nhận token thực tế và đối soát chi phí đã có.

### R14 — Cần chốt nghiệp vụ: 30 lượt dùng thử là theo kỳ hay một lần?

Vị trí: `backend/src/main/java/com/postiva/usage/AiUsageService.java:123`, `frontend/src/components/UsagePanel.tsx:29`, `application.yml:96`.

Code hiện **cấp lại 30 lượt Free mỗi kỳ 30 ngày**, không phải 30 lượt duy nhất trong suốt thời gian dùng thử. Nếu chủ sản phẩm muốn 30 lượt một lần cho mỗi tài khoản, cần thay chính sách và vòng đời trial. Đây là điểm cần chốt, không tự kết luận cơ chế theo kỳ là sai.

Các giới hạn hiện có khác: 20 lần yêu cầu AI/người/ngày UTC và 100 lần cho toàn hệ thống/ngày; số lần thử có cả yêu cầu lỗi. Một tài khoản còn 30 lượt vẫn có thể bị chặn sớm bởi trần ngày hoặc ngân sách. Cần công bố dễ hiểu và tính sức chứa pilot theo các giới hạn này.

Tài liệu vẫn còn số 5 tại `docs/USER_SECURITY_AND_AI_USAGE.md:75` và `NGHIEP_VU_MVP.md:18`; dòng hướng dẫn UsagePanel ghi cứng 30/50 nên có thể lệch cấu hình triển khai. Nên có một chính sách có phiên bản và UI lấy quyền lợi từ server.

## 5. Ưu điểm nên giữ lại

- **Phân quyền có nhiều lớp:** `/api/admin/**` yêu cầu ADMIN và MFA_VERIFIED; service kiểm tra tài khoản ACTIVE, quyền sở hữu dữ liệu và giới hạn admin quản lý USER. Có audit khóa/mở và bảo vệ nhật ký quản trị.
- **Bảo mật phiên tốt hơn JWT thuần:** JWT gắn `authVersion`, đối chiếu trạng thái hiện tại; đổi/reset mật khẩu và thu hồi phiên tăng phiên bản. Có TOTP, mã recovery, chặn reuse mã và giới hạn thử admin.
- **Đăng ký/khôi phục đã có nền tảng đúng:** OTP/hash, giới hạn dùng lại, khóa khi cập nhật đồng thời, response chống dò email; reset token ngẫu nhiên lưu hash, hết hạn và dùng một lần. Email OTP/reset gửi sau transaction.
- **Quota AI có tính nhất quán:** giữ quota bằng khóa user, lưu idempotency key/hash request; lưu bài và trừ lượt thành công trong cùng transaction. Provider I/O của tạo nội dung nằm ngoài transaction database.
- **Kiểm soát chi phí đã thành module:** giữ chỗ ngân sách ngày/tháng UTC, ghi usage provider, giữ khoản chưa rõ khi timeout, công tắc admin, ngưỡng warning và đối soát chi phí tùy cấu hình.
- **Scheduler có hướng thiết kế hợp lý:** claim bằng `FOR UPDATE SKIP LOCKED`, lease, backoff, giới hạn retry; tình huống kết quả không rõ chuyển UNKNOWN để đối chiếu. Cần hoàn thiện toàn bộ state transitions theo R02.
- **Thanh toán có nền móng cho tiền thật:** server chọn giá/quota, kiểm tra HMAC và dữ liệu đơn, lưu giao dịch/chống lặp, grant có thể retry, gia hạn nối kỳ và xử lý refund có audit. UI không cấp Pro chỉ vì quay về return URL. Hoàn tiền hiện có bước xử lý bên ngoài và admin ghi nhận, chưa phải tự động hoàn về ngân hàng.
- **Media có kiểm soát truy cập:** kiểm tra chủ sở hữu, đường dẫn lưu giới hạn, chữ ký URL có hạn, Meta token dùng AES-GCM. Không thấy `.env` thật trong danh sách file được Git theo dõi ở snapshot kiểm tra; chưa kiểm toán toàn bộ lịch sử Git/secret production.
- **Database/deploy tiến bộ rõ:** Flyway, Hibernate validate, profile staging/prod, kiểm tra cấu hình, health check, image tag theo release, CI chạy với database riêng, script backup mã hóa và rollback.
- **Phạm vi chưa hỗ trợ được thể hiện:** ReportsPage ghi rõ chưa đồng bộ lượt xem/tương tác/bình luận; endpoint comments trả NOT_IMPLEMENTED. Calendar/scoring hiện là quy tắc/template, cần mô tả đúng như vậy khi giới thiệu sản phẩm.

Đã đối chiếu hình dạng API tạo/tra cứu/hủy và chữ ký trong [tài liệu API payOS](https://payos.vn/docs/api/). Việc phù hợp cấu trúc API chưa chứng minh merchant, webhook public hoặc luồng tiền thật đã vận hành.

## 6. Nhược điểm kiến trúc và chất lượng dài hạn

- `AppDataController` đang chứa nhiều nghiệp vụ ghép dữ liệu, sửa/xóa và gọi API ngoài; logic nên chuyển vào service có DTO rõ ràng và ranh giới transaction cụ thể.
- Có nhiều đường tạo/sửa/đăng bài: PostService, SocialPostService, FacebookBusinessPostService và AppDataController. Quy tắc media/trạng thái bị chia nhỏ, dễ sửa một đường nhưng bỏ sót đường khác. Một số luồng vẫn gọi provider trong transaction, một số ghi nhiều bước thiếu transaction chung.
- Nhiều DTO dữ liệu ứng dụng là `Map<String,Object>`, làm hợp đồng frontend/backend yếu hơn DTO có kiểu và kiểm tra runtime.
- Cần chuẩn hóa quy tắc chuyển trạng thái và lưu lịch sử theo từng lần đăng; không để trạng thái chung của post che lỗi của một đích trong bài nhiều nền tảng.
- Frontend có kiểm tra session nhưng chỉ bốn test tự động, chưa phủ upload/publish/retry/payment. Nên tách các component lớn theo nghiệp vụ và lazy-load khu vực admin/billing để giảm tải ban đầu.
- JWT đang lưu localStorage, frontend chưa có chính sách CSP trong Nginx. Đây là điểm cần gia cố cùng phòng XSS và quản lý phiên; review này chưa tìm thấy đường XSS có thể khai thác để kết luận lộ token.
- Docker base image còn là tag có thể thay đổi; release app có tag riêng nhưng chưa có bằng chứng pin digest, SBOM/dependency scan hoặc image scan trong CI. Runtime backend chưa thấy USER không đặc quyền.
- Nhiều thay đổi production đang chưa commit/untracked. Jenkins checkout từ Git sẽ không tự có các thay đổi chỉ nằm ở workspace. Cần chốt snapshot release, kiểm tra secrets và chạy CI trên chính commit sẽ deploy.

## 7. Thứ tự xử lý đề xuất

1. Sửa ba test migration để CI phản ánh đúng tình trạng code; giữ DB integration gate bắt buộc.
2. Sửa R01 về proxy/IP, bổ sung test đi qua filter và cấu hình triển khai.
3. Sửa R02/R03: chuyển trạng thái nguyên tử và idempotency từ API tới từng đích đăng. Sau đó chạy cạnh tranh retry/worker/restart/timeout.
4. Sửa R04: xóa theo từng đích, xử lý PUBLISHING/UNKNOWN và giữ dữ liệu để đối soát khi provider lỗi.
5. Sửa R05/R07/R08: quota media, vòng đời file, xác thực nội dung; kiểm thử upload đồng thời và xóa khi còn tham chiếu.
6. Chốt chính sách Free 30 lượt, giới hạn ngày, cấu hình token và quyền lợi Pro; cập nhật UI/tài liệu từ cùng dữ liệu chính sách.
7. Kiểm thử E2E trên staging: đăng ký → OTP → tạo AI → upload → Page token → đăng ngay/lên lịch → xem kết quả → sửa/xóa → xử lý lỗi. Có ca multi-Page, mất response, token hết hạn, hai tab và worker restart.
8. Diễn tập restore đầy đủ, cấu hình alert thực và chạy pilot có người hỗ trợ. Tăng tải theo số liệu độ trễ, lỗi, chi phí và dung lượng đo được.
9. Sau khi pilot ổn định mới bật mở bán; kiểm chứng thanh toán, grant, webhook lặp/sai thứ tự, gia hạn, refund và đối soát với giao dịch thật có kiểm soát. Tiếp tục App Review/OAuth và analytics theo phạm vi phát hành.

## 8. Điều kiện chấp nhận trước phát hành

| Điều kiện | Bằng chứng cần có |
|---|---|
| CI xanh trên commit phát hành | Backend đầy đủ DB test, frontend test/lint/build; không bỏ qua nhóm integration bắt buộc |
| Không đăng trùng trong các ca mục tiêu | Test cạnh tranh request/worker; timeout sau Meta nhận; restart; thử lại cùng request ID |
| Media không làm đầy đĩa và không mất sai file | Test quota đồng thời, giả định dạng, xóa/repost, last reference và cleanup |
| Quản lý bài nhiều đích nhất quán | Kết quả từng Page rõ; xóa một phần thất bại vẫn giữ dữ liệu đối soát |
| Khôi phục được | Restore DB + media + khóa trên staging, kiểm tra tham chiếu và chạy ứng dụng |
| Có người biết khi lỗi | Alert thử tới người trực cho downtime, publish UNKNOWN/FAILED, grant lỗi, chi phí và dung lượng |
| Phạm vi sử dụng minh bạch | Chính sách Free/Pro, giới hạn media, nền tảng/token và chức năng chưa hỗ trợ khớp UI/code |
| Thu tiền an toàn | Giao dịch thật có kiểm soát xác minh cấp đúng kỳ, không cấp từ return URL, quy trình refund/đối soát hoạt động |

Ưu tiên hiện tại là đóng các lỗi đã chứng minh và chạy pilot có bằng chứng. Không cần viết lại toàn bộ dự án; các module bảo mật, quota, billing và migration hiện có đủ cơ sở để tiếp tục hoàn thiện.
