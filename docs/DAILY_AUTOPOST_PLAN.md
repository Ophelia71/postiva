# Daily: đăng bài tự động hằng ngày

> Trạng thái: **đã triển khai bản đầu trên nhánh `feature/onboarding-daily-autopost`**, phía sau cờ `POSTIVA_AUTOMATION_ENABLED` (mặc định tắt). Tên trong giao diện là **Daily**. Bản này chỉ hỗ trợ Facebook Page, mỗi rule một Page, chế độ duyệt trước; chế độ tự động đăng (`AUTO`) có cờ riêng `POSTIVA_AUTOMATION_AUTO_ENABLED` (mặc định tắt) và chưa nên bật cho tới khi đã diễn tập trên staging. Tài liệu bên dưới là plan gốc đã rà với mã nguồn; mục "Đã làm và khác plan" ở cuối ghi những chỗ bản triển khai khác.
## Mục tiêu

Người dùng đặt một lần: "mỗi ngày lúc 19:00 đăng một bài về chủ đề X lên Facebook Page Y". Postiva tự soạn nội dung, đưa vào hàng đợi và đăng đúng giờ.

Không nằm trong đợt đầu: lặp lại cùng một nội dung, đăng nhiều nền tảng trong một rule, chọn giờ tối ưu theo dữ liệu tương tác (hiện chưa đồng bộ lượt xem/tương tác), Instagram và Threads.

## Nguyên tắc thiết kế

1. **Automation chỉ tạo lịch, không bao giờ gọi Meta.** Bài tự động là một dòng `scheduled_posts` bình thường; `PublishingScheduler` và cơ chế claim/lease hiện có lo việc đăng, retry, `UNKNOWN`. Không sửa máy trạng thái này.
2. **Khởi đầu bằng chế độ duyệt (`REVIEW`).** `docs/META_PAGE_TOKEN_PILOT.md` khuyến cáo chưa dùng lịch tự động cho nội dung quan trọng cho tới khi đã diễn tập timeout, restart và đối soát `UNKNOWN` trên staging. `AUTO` chỉ mở sau, theo từng người dùng.
3. **Mỗi rule một Page** (một tài khoản mạng xã hội). Khóa duy nhất `(rule_id, run_date)` khi đó đúng bằng "tối đa một bài mỗi kênh mỗi ngày". Thay cho danh sách `targets` ở bản nháp trước.

## Điểm sửa so với bản nháp trước (đã đối chiếu code)

| # | Bản nháp sai/thiếu | Thực tế trong code | Cách xử lý |
|---|---|---|---|
| 1 | Một cột `brief` là đủ để soạn bài | `PostService.generate` (`post/service/PostService.java:68-94`) dựa vào người dùng đang đăng nhập (`requireCurrentUserId`) và cần đủ trường của `GeneratePostRequest`: tên sản phẩm, mô tả, khách hàng mục tiêu, điểm nổi bật, mục tiêu, giọng điệu, nền tảng. | Tách `generateForUser(userId, request, key)`. `PublishingService.create` và `SocialAccountService.requireCurrentUserAccount` cần bản nhận `userId` tương tự; repository đã có `findByIdAndUserIdAndDisconnectedAtIsNull`. `brief` lưu dạng JSONB khớp các trường này. |
| 2 | Lưu `draft_content` riêng | `generate` đã lưu một dòng `generated_posts` ở trạng thái `DRAFT`; sửa nháp đã có qua `PUT /api/posts/{id}` (`PostController.java:262`). | Chỉ lưu `post_id` trong `automation_runs`. |
| 3 | Chỉ nói "hết quota thì dừng" | `AiUsageService.reserve` ném cùng một `RateLimitException` (429) cho 4 trường hợp: đang có yêu cầu chạy, hết quota kỳ, hết hạn mức ngày của người dùng, hết hạn mức ngày toàn hệ thống (`:61, 62, 69, 77`). | Thêm mã lý do vào `RateLimitException`, hoặc kiểm tra trước `AiUsageService.summary(userId).remaining()` (public, `:33`). |
| 4 | "Free ≈ 30 lượt/kỳ là đủ" | Hạn mức ngày: 20 lượt/người và 100 lượt toàn hệ thống (`application.yml:222`). Automation cạnh tranh với việc dùng tay. Free 30 lượt/kỳ khớp V11, Pro 50. | Giữ một phần quota cho dùng tay; cảnh báo khi tạo rule (xem quyết định 4). |
| 5 | Phát hiện token/quyền lỗi dễ dàng | Chỉ có hàm private `containsTokenOrPermissionFailure` khớp chữ tiếng Việt (`scheduler/service/ScheduledPostProcessingService.java:268-273`). | Thêm cột `scheduled_posts.failure_code` (nullable, chỉ thêm) và ghi tại đó. |
| 6 | "Có giới hạn tần suất như API AI" | Không có; `PostService` chỉ dùng `sha256` của limiter. Chốt chặn thực tế là các hạn mức usage. | Bỏ ý đó khỏi plan. |
| 7 | Chống duyệt hai lần bằng ràng buộc ở `scheduled_posts` | `scheduled_posts` không có ràng buộc duy nhất theo (bài, tài khoản). | Chống trùng bằng cập nhật có điều kiện trên `automation_runs` (mục "Duyệt"). |
| 8 | Không nhắc | `@EnableScheduling` bật toàn cục (`PostivaApplication.java:8`) và không job nào có khóa liên-instance ngoài SQL claim; phục hồi lease `PUBLISHING→UNKNOWN` chỉ chạy trong `claimDue` (`ScheduledPostClaimService.java:47`). | Planner phải tự an toàn trên DB, và đọc trạng thái bằng polling. |
| 9 | Không nhắc | `DatabaseMigrationTest.java:84` khẳng định phiên bản Flyway là `"12"`. | Cập nhật lên 13. |

## Mô hình dữ liệu: `V13__daily_autoposting.sql`

```sql
CREATE TABLE automation_rules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  social_account_id UUID REFERENCES social_accounts(id) ON DELETE SET NULL,
  platform VARCHAR(20) NOT NULL CHECK (platform IN ('FACEBOOK','INSTAGRAM','THREADS')),
  name VARCHAR(120) NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PAUSED')),
  paused_reason VARCHAR(40), paused_at TIMESTAMPTZ,
  timezone VARCHAR(64) NOT NULL,
  time_of_day TIME NOT NULL,
  days_of_week SMALLINT NOT NULL DEFAULT 127 CHECK (days_of_week BETWEEN 1 AND 127), -- bitmask
  approval_mode VARCHAR(10) NOT NULL DEFAULT 'REVIEW' CHECK (approval_mode IN ('REVIEW','AUTO')),
  lead_minutes INTEGER NOT NULL DEFAULT 720 CHECK (lead_minutes BETWEEN 60 AND 1440),
  brief JSONB NOT NULL,                       -- các trường của GeneratePostRequest, trừ platforms
  default_media_ids JSONB NOT NULL DEFAULT '[]',
  version INTEGER NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX idx_automation_rules_user ON automation_rules(user_id);
CREATE INDEX idx_automation_rules_active ON automation_rules(status) WHERE status = 'ACTIVE';

CREATE TABLE automation_runs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rule_id UUID NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
  run_date DATE NOT NULL,                     -- ngày địa phương theo múi giờ của rule
  planned_for TIMESTAMPTZ NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN
    ('GENERATING','PENDING_REVIEW','SCHEDULED','SKIPPED','FAILED')),
  claim_token UUID, lease_until TIMESTAMPTZ,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  idempotency_key UUID NOT NULL,
  post_id UUID REFERENCES generated_posts(id) ON DELETE SET NULL,
  scheduled_post_id UUID REFERENCES scheduled_posts(id) ON DELETE SET NULL,
  error VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (rule_id, run_date));
CREATE UNIQUE INDEX uq_automation_runs_sched ON automation_runs(scheduled_post_id) WHERE scheduled_post_id IS NOT NULL;
CREATE INDEX idx_automation_runs_review ON automation_runs(status, planned_for);

ALTER TABLE scheduled_posts ADD COLUMN failure_code VARCHAR(40);
```

Đây là bản phác thảo cho người triển khai; cần đối chiếu tên bảng/cột thật (`social_accounts`, `generated_posts`) trước khi chạy.

## Planner (`AutomationPlanner`, `@Scheduled` mỗi 5 phút, sau cờ `postiva.automation.enabled`, mặc định tắt)

1. **Chèn run.** Với mỗi rule ACTIVE (chủ tài khoản `ACTIVE`, kênh còn kết nối và có token), tính khung giờ kế tiếp và `run_date`. Nếu nằm trong `lead_minutes` và khớp thứ trong tuần:
   `INSERT ... ON CONFLICT (rule_id, run_date) DO NOTHING RETURNING id`. Chỉ instance thắng mới đi tiếp. `idempotency_key = UUID.nameUUIDFromBytes(ruleId|runDate|ruleVersion)`.
2. **Claim và phục hồi.** Instance thắng ghi `claim_token` và `lease_until`, giống `claimDue`. Run `GENERATING` quá hạn lease (quét bằng `FOR UPDATE SKIP LOCKED`) chỉ được thử lại bằng đúng key đó:
   - nếu usage đã `SUCCEEDED` thì `reserve` trả bài đã có (`AiUsageService.java:51-54`);
   - nếu `RESERVED` hoặc `UNKNOWN` thì trả 409 → run `FAILED`, bỏ qua ngày đó, **không** gọi nhà cung cấp lần hai.
   Nội dung yêu cầu phải xác định (deterministic) để hash ổn định; đưa `run_date` và 3 tiêu đề gần nhất vào `additionalInfo` để tránh lặp.
3. **Soạn nội dung** bằng `generateForUser`, ngoài transaction (như `generate` hiện làm). Ghi `post_id`. `REVIEW` → `PENDING_REVIEW`; `AUTO` → bước 4.
4. **Duyệt hoặc tự tạo lịch**, trong một transaction:
   `UPDATE automation_runs SET status='SCHEDULED' WHERE id=? AND status IN ('PENDING_REVIEW','GENERATING') AND planned_for > now() RETURNING ...`
   Nếu trả về một dòng thì gọi `PublishingService.createForUser(userId, postId, accountId, platform, plannedFor)` và ghi `scheduled_post_id`. Cập nhật có điều kiện này là chốt chặn "đúng một lần" cho việc duyệt hai lần và cho tranh chấp giữa planner với người dùng. Dùng lại các kiểm tra sẵn có của lịch đăng: thời điểm phải ở tương lai; tài khoản, nền tảng và publisher phải tồn tại.
5. **Hết hạn.** Một sweeper đánh `SKIPPED` cho run `PENDING_REVIEW` có `planned_for <= now`.

## Bảo vệ AI

- `generateForUser` giữ nguyên reserve/succeed/fail: quota, ngân sách toàn hệ thống, `ensureEnabled` (công tắc admin) và kiểm tra yêu cầu đang chạy.
- Kiểm tra trước `summary().remaining()`:
  - còn 0 → tạm dừng rule với `AI_QUOTA_EXHAUSTED`;
  - đang bận hoặc chạm hạn mức ngày → hoãn sang lần quét sau trong cửa sổ `lead_minutes`; hết cửa sổ thì `SKIPPED` với `AI_BUSY`;
  - 503 (AI tắt/ngân sách) → hoãn rồi bỏ qua; chỉ tạm dừng rule sau 3 ngày liên tiếp lỗi.
- Giới hạn số rule mỗi người dùng (đề xuất 3).

## Điều kiện tạm dừng

| Điều kiện | Nơi phát hiện |
|---|---|
| Token hết hạn/thiếu | Planner kiểm tra trước, mô phỏng `ensureTokenUsable` (`SocialAccountService.java:427`). Sau sự cố: `failure_code=TOKEN` ghi tại `ScheduledPostProcessingService.java:199-203` và `:235`. |
| Bài gần nhất ở `UNKNOWN` | Trước khi tạo run, kiểm tra bài của run trước. `PUBLISHING` hoặc `UNKNOWN` chặn run mới; `UNKNOWN` tạm dừng rule. Từ chối bật lại khi còn bài chưa đối soát (`resolveUnknown`, `PublishingService.java:178`). |
| Chủ tài khoản bị khóa | Truy vấn planner lọc `status='ACTIVE'`; đồng thời gọi `pauseAllForUser` từ `UserAccountStatusService.changeStatus` (nhánh `lockUser`, gần `:60`). |
| Kênh bị ngắt | Planner kiểm tra `disconnected_at IS NULL`; thêm móc ở `SocialAccountService.disconnect` (`:243`) và `removeProviderUserData` (`:253`). `ON DELETE SET NULL` để lại tài khoản null, planner coi là tạm dừng. |

Chỉ người dùng mới bật lại được; mở khóa tài khoản không tự bật lại rule.

## Múi giờ và DST

Lưu múi giờ IANA và `LocalTime`, kiểm tra với `ZoneId.getAvailableZoneIds()`. Tính `ZonedDateTime.of(localDate, time, zone).toInstant()`: khoảng trống DST dịch tiến, khoảng chồng lấn lấy thời điểm sớm hơn. `run_date` là ngày địa phương. Sửa rule tăng `version`, chỉ ảnh hưởng các run sau. Kiểm thử `Asia/Ho_Chi_Minh` lúc 00:30 và 23:30, và `America/New_York` vào hai ngày chuyển giờ.

## API (`/api/automation`)

Xác thực theo `SecurityConfig` (`anyRequest().hasRole("USER")`); người dùng lấy từ `CurrentUserService`; quyền sở hữu kiểm bằng `findByIdAndUserId` trả 404, như `PublishingService`.

- Rule: `GET/POST /rules`, `PUT/DELETE /rules/{id}`, `POST /rules/{id}/pause` và `/resume`, `GET /rules/{id}/preview` (giờ đăng 7 ngày tới, không tốn quota).
- Run: `GET /runs?status=`, `PUT /runs/{id}` (chuyển tiếp sang cập nhật bài hiện có), `POST /runs/{id}/approve` và `/skip`.
- Kiểm tra khi tạo/sửa: tài khoản thuộc người dùng và đang kết nối; Instagram cần media (chưa hỗ trợ ở pha đầu); cờ `AUTO` phải được phép cho người dùng đó.
- Phản hồi dùng `ApiResponse` và lỗi tiếng Việt qua `ResponseStatusException`.

## Frontend

- `AutomationPage.tsx` tại `/app/automation`; thêm route ở `App.tsx` và mục điều hướng trong `navItems`.
- Wizard 4 bước dùng lại mẫu stepper của `CreatePostPage` (`goToStep`, `stepIssue`): kênh, giờ và ngày, chủ đề và giọng điệu, duyệt hay tự động kèm xem trước 7 ngày.
- `EmptyState` cho trạng thái chưa có rule; `HelpTip` cho giải thích quota và cảnh báo `AUTO`.
- Hàng đợi chờ duyệt hiện ở `SchedulePage` và dẫn từ `StartPage`; trạng thái rule (Đang chạy / Tạm dừng kèm lý do) luôn nhìn thấy, không chỉ dựa vào màu.
- Hộp xác nhận trước khi chọn `AUTO`: bài sẽ đăng công khai mà không qua bước duyệt.
- Mở rộng onboarding: bước 4 tùy chọn "Bật đăng hằng ngày" sau bài đầu tiên đã đăng.

## Kiểm thử

- **Unit:** tính khung giờ (kể cả DST); phân loại điều kiện tạm dừng; lỗi AI giả lập 429, 503, 403.
- **Tích hợp PostgreSQL** (nhân bản `ScheduledPostClaimIntegrationTest`, dùng `POSTIVA_TEST_DB_URL`):
  1. Hai planner chạy song song chỉ tạo một run.
  2. Duyệt hai lần chỉ tạo một dòng `scheduled_posts`.
  3. Phục hồi sau crash dùng cùng key, không gọi nhà cung cấp lần hai.
  4. Hết quota thì tạm dừng rule.
  5. `UNKNOWN` chặn run kế tiếp.
  6. Người dùng bị khóa bị bỏ qua.
  7. Truy cập chéo người dùng trả 404.
- Cập nhật `DatabaseMigrationTest` lên phiên bản 13. Frontend hiện chỉ có `tests/auth-session.test.mjs`, cần thêm khung test (xem `ONBOARDING_PLAN.md`).

## Giai đoạn

| Giai đoạn | Nội dung | Mức | Hoàn thành khi |
|---|---|---|---|
| A | `V13`, entity, repository, cờ tính năng, sửa `DatabaseMigrationTest` | S | Migrate được trên DB trống và bản sao staging |
| B | `generateForUser`, `createForUser`, `failure_code`, lý do trong `RateLimitException` | M | Endpoint hiện có hành xử y như cũ; test xanh |
| C | Planner, claim, phục hồi, duyệt/bỏ qua, hết hạn | L | Test race và idempotency chạy qua |
| D | Móc tạm dừng (`UserAccountStatusService`, `SocialAccountService`), nhật ký kiểm toán | M | Mỗi điều kiện dừng có một test |
| E | Controller, DTO, quyền sở hữu | M | Test truy cập chéo chạy qua |
| F | Wizard, hàng đợi duyệt, trạng thái rule | L | Rule → duyệt → thấy ở Lịch đăng trên 375px và 1440px |
| G | Pilot chỉ `REVIEW`, vài tài khoản, Page thử nghiệm, 2 tuần | S | Không bài trùng, không bài ngoài ý muốn |
| H | `AUTO` theo từng người dùng | S | Đã diễn tập timeout/restart và đối soát `UNKNOWN` trên staging |

## Quyết định cần chốt

1. **Chỉ `REVIEW` khi ra mắt** (khuyến nghị, theo tài liệu pilot).
2. **Mỗi rule một Facebook Page**; Instagram/Threads để sau.
3. **Pro riêng hay dùng chung quota AI?** Khuyến nghị dùng chung, kèm cảnh báo khi tạo rule vì rule hằng ngày dùng khoảng 30 lượt/kỳ, bằng toàn bộ quota Free.
4. **Giữ phần quota cho dùng tay** (ví dụ tạm dừng khi `remaining <= 3`). Khuyến nghị có.
5. **Tự bật lại khi sang kỳ quota mới?** Khuyến nghị không; người dùng bật lại để an toàn.
6. **Thông báo chờ duyệt:** chỉ trong ứng dụng; email cần `MAIL_*` hoạt động và chính sách gửi mail.
7. **Cột `failure_code` hay phân tích chuỗi lỗi?** Khuyến nghị cột.

## Chưa kiểm chứng

- Hành vi thật của Meta với token thật và lịch tự động.
- Việc `MediaOrphanCleanupService` có xóa nhầm media dùng làm `default_media_ids` hay không (`FileService.java:96-100`); cần đọc kỹ trước khi cho rule dùng ảnh mặc định.
- API chi tiết của các component frontend ngoài việc chúng tồn tại.
- Các số dòng trích dẫn ở trên lấy từ lúc rà soát; có thể lệch nếu code đổi.

## Đã làm và khác plan

- **Backend** (`com.postiva.automation`): `V13__daily_autoposting.sql`, `AutomationService` (rule, planner, duyệt/bỏ qua), `AutomationPlanner` (`@Scheduled`, chỉ tạo bean khi cờ bật), `AutomationStore` (JDBC, các câu lệnh có điều kiện), `AutomationPauser` (chỉ JDBC để tránh phụ thuộc vòng), `AutomationController` (`/api/automation`).
- **Tách theo `userId`:** `PostService.generateForUser`, `PublishingService.scheduleForUser`, `SocialAccountService.requireUserAccount`. `scheduleForUser` gọi `flush()` vì dòng `scheduled_posts` do JPA tạo phải có trong DB trước khi `automation_runs` trỏ tới nó bằng JDBC (lỗi này do test tích hợp phát hiện).
- **`failure_code`:** cột mới trên `scheduled_posts`, ghi `TOKEN` khi đăng thất bại vì token/quyền. Chỉ lỗi mới hơn lần bật lại gần nhất mới làm rule tạm dừng.
- **`RateLimitException`** có `limit()` (không dùng tên `getReason` vì trùng với `ResponseStatusException`) để phân biệt hết quota kỳ với các giới hạn tạm thời.
- **Móc tạm dừng:** khóa người dùng (`UserAccountStatusService`) và ngắt kênh (`SocialAccountService`). Hai service giữ constructor cũ (truyền `null`) nên test hiện có không đổi.
- **Không có `default_media_ids`:** bỏ khỏi bản đầu vì chưa kiểm chứng tương tác với `MediaOrphanCleanupService`. Bài chỉ có chữ.
- **API thêm** `GET /api/automation/status` (cờ bật/tắt và giới hạn) để giao diện biết hiển thị gì.
- **Khi người dùng tự tạm dừng**, bản nháp chờ duyệt được giữ lại (đã tốn lượt AI); tạm dừng tự động thì bỏ qua cả bản nháp.
- **Giao diện:** `/app/daily` (mục "Daily" trong thanh bên và menu tài khoản trên mobile), wizard 4 bước dùng component `Stepper` chung, hàng đợi duyệt có sửa bản nháp trước khi duyệt.
- **Chưa làm:** sửa rule bằng giao diện (API `PUT` đã có), thông báo email, Instagram/Threads, ảnh mặc định, chạy `AUTO` thật trên staging.
