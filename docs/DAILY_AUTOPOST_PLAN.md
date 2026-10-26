# Plan đăng bài tự động hằng ngày

> Trạng thái: **chỉ là plan, chưa triển khai.** Tính năng này cần thay đổi backend (migration, job, API), khác với đợt sửa onboarding chỉ chạm frontend. Cần chủ dự án duyệt các quyết định ở mục "Quyết định cần chốt" trước khi bắt đầu.

## Mục tiêu

Người dùng đặt một lần: "mỗi ngày lúc 19:00, đăng một bài về chủ đề X lên Facebook Page Y". Postiva tự soạn nội dung, đưa vào hàng đợi và đăng đúng giờ, không cần mở ứng dụng mỗi ngày.

Không phải mục tiêu của đợt đầu: đăng lặp lại cùng một nội dung, đăng chéo nhiều nền tảng phức tạp, chọn giờ đăng tối ưu bằng dữ liệu tương tác (hiện chưa đồng bộ lượt xem/tương tác).

## Điều kiện nền (đã có sẵn trong backend)

| Thành phần | Dùng lại như thế nào |
|---|---|
| `scheduled_posts` với claim token, lease 30 phút, retry backoff, trạng thái `SCHEDULED/PUBLISHING/PUBLISHED/FAILED/UNKNOWN/CANCELLED` | Bài tự động chỉ là một lịch đăng bình thường. **Không sửa máy trạng thái này.** |
| `PublishingScheduler` + `ScheduledPostClaimService` (`FOR UPDATE SKIP LOCKED`) | Giữ nguyên làm nơi đăng. Automation chỉ *tạo* lịch, không tự gọi Meta. |
| Module `ai` + quota Free/Pro + ngân sách ngày/tháng + công tắc AI | Mỗi bài tự soạn trừ đúng quota và bị chặn khi hết ngân sách. |
| Quy tắc `UNKNOWN` không tự gửi lại | Giữ nguyên; automation phải **tạm dừng** khi gặp `UNKNOWN`/token lỗi (xem "Bảo vệ"). |

Lưu ý từ `docs/META_PAGE_TOKEN_PILOT.md`: pilot hiện khuyến cáo *không dùng lịch tự động cho nội dung quan trọng* cho tới khi đã diễn tập timeout, restart và đối soát `UNKNOWN` trên staging. Vì vậy phát hành bắt đầu ở chế độ **duyệt trước khi đăng**, không phải tự động hoàn toàn.

## Thiết kế đề xuất

### Hai chế độ duyệt

| Chế độ | Hành vi | Khi nào dùng |
|---|---|---|
| `REVIEW` (mặc định) | Bài được soạn trước giờ đăng, nằm trong hàng đợi chờ duyệt. Chỉ khi người dùng bấm Duyệt mới tạo `scheduled_post`. Quá giờ mà chưa duyệt thì bỏ qua ngày đó. | Đợt đầu và với người dùng mới. |
| `AUTO` | Soạn xong là tạo `scheduled_post` ngay. | Mở sau khi đã chạy ổn định ở `REVIEW` và đã đối soát an toàn. Chỉ bật theo từng người dùng. |

Giữ bài ở bảng automation cho tới khi duyệt, thay vì thêm trạng thái "chờ duyệt" vào `scheduled_posts`, để máy trạng thái của scheduler không đổi.

### Mô hình dữ liệu (migration mới, V13 trở đi)

`automation_rules`

| Cột | Ghi chú |
|---|---|
| `id`, `user_id` | |
| `name`, `active` | |
| `timezone` | IANA (ví dụ `Asia/Ho_Chi_Minh`), không lưu offset cố định |
| `time_of_day`, `days_of_week` | Giờ địa phương; mặc định cả tuần |
| `targets` | Danh sách kênh (platform + account id) đã kết nối thuộc người dùng |
| `source_type` | `BRIEF` (soạn từ mô tả/chủ đề, đợt đầu), sau này `TEMPLATE`, `EVERGREEN_LIST` |
| `brief` | Mô tả sản phẩm/chủ đề, giọng điệu, hashtag; kèm ảnh mặc định nếu có |
| `approval_mode` | `REVIEW` hoặc `AUTO` |
| `lead_minutes` | Soạn trước giờ đăng bao lâu (mặc định 12 giờ) |
| `paused_reason`, `paused_at` | Lý do tự động dừng |
| `created_at`, `updated_at` | |

`automation_runs`

| Cột | Ghi chú |
|---|---|
| `id`, `rule_id`, `run_date` (ngày địa phương) | **Khóa duy nhất `(rule_id, run_date)`** để mỗi ngày chỉ có một lần chạy, kể cả khi hai worker cùng quét |
| `status` | `GENERATING`, `PENDING_REVIEW`, `SCHEDULED`, `SKIPPED`, `FAILED` |
| `draft_content` | Nội dung đã soạn, người dùng sửa được trước khi duyệt |
| `scheduled_post_id` | Liên kết sang lịch đăng sau khi duyệt/tự tạo |
| `error`, `ai_usage_id` | Để đối chiếu quota và chi phí |

### Luồng hằng ngày

1. **Planner** chạy mỗi 10–15 phút. Với mỗi rule đang `active`, nếu giờ đăng kế tiếp nằm trong `lead_minutes` và chưa có `automation_run` cho ngày đó, thì chèn một dòng `GENERATING`. Ràng buộc duy nhất làm bước này an toàn khi chạy song song; ai chèn được thì người đó soạn.
2. **Soạn nội dung** bằng dịch vụ AI hiện có. Trừ quota, tôn trọng ngân sách; lỗi → `FAILED` kèm lý do, **không** tự thử lại vô hạn.
3. `REVIEW`: lưu `draft_content`, trạng thái `PENDING_REVIEW`, thông báo người dùng. Người dùng sửa và Duyệt → tạo `scheduled_post` đúng giờ rule → `SCHEDULED`. Không duyệt kịp → `SKIPPED`.
   `AUTO`: tạo `scheduled_post` ngay.
4. Từ đây `PublishingScheduler` lo phần đăng, retry và ghi log như mọi lịch khác.

### Bảo vệ (bắt buộc trước khi mở cho người dùng thật)

- **Cờ tính năng** `POSTIVA_AUTOMATION_ENABLED`, mặc định tắt; thêm cờ theo người dùng cho `AUTO`.
- **Tự tạm dừng rule** khi: token hết hạn/thiếu quyền, bài gần nhất của rule ở `UNKNOWN`, hết quota/ngân sách AI, kênh bị ngắt. Ghi `paused_reason` và hiển thị rõ; chỉ người dùng mới bật lại.
- **Giới hạn một bài mỗi kênh mỗi ngày mỗi rule**, và giới hạn số rule mỗi người dùng.
- **Chống lặp nội dung**: so với vài bài gần nhất của cùng rule, nếu quá giống thì soạn lại một lần rồi bỏ qua ngày đó; Meta có thể coi bài lặp là spam.
- **Instagram cần media**: rule nhắm Instagram bắt buộc có ảnh mặc định hoặc nguồn ảnh; nếu không thì không cho lưu.
- **Chủ tài khoản bị khóa** → rule dừng (đã có hành vi tương tự với lịch).
- **Nhật ký kiểm toán** cho mọi lần tạo, sửa, bật, tắt rule.
- Thời điểm lưu theo UTC như hiện tại; chỉ rule giữ múi giờ IANA để tính "19:00 hôm nay". Kiểm thử ranh giới ngày và chuyển giờ mùa hè.

### API đề xuất

| Phương thức | Đường dẫn | Mục đích |
|---|---|---|
| `GET/POST` | `/api/automation/rules` | Danh sách, tạo rule |
| `PUT/DELETE` | `/api/automation/rules/{id}` | Sửa, xóa |
| `POST` | `/api/automation/rules/{id}/pause` · `/resume` | Tạm dừng, bật lại |
| `GET` | `/api/automation/runs?status=PENDING_REVIEW` | Hàng đợi chờ duyệt, lịch sử chạy |
| `PUT` | `/api/automation/runs/{id}` | Sửa bản nháp |
| `POST` | `/api/automation/runs/{id}/approve` · `/skip` | Duyệt hoặc bỏ qua |
| `GET` | `/api/automation/rules/{id}/preview` | Giờ đăng 7 ngày tới (không soạn nội dung, không tốn quota) |

Mọi endpoint theo chủ sở hữu (`user_id` lấy từ JWT), kiểm tra kênh thuộc người dùng, giới hạn tần suất giống các API AI hiện có.

## Giao diện (frontend)

- Trang mới **Đăng tự động** (`/app/automation`), mục thứ 5 trong điều hướng desktop; trên mobile nằm trong menu tài khoản để thanh dưới giữ 4 mục.
- **Wizard 4 bước**, mỗi bước một quyết định: (1) Chọn kênh, (2) Giờ và ngày trong tuần, (3) Chủ đề/mô tả sản phẩm và giọng điệu, (4) Duyệt trước hay tự động, kèm bản xem trước 7 ngày tới.
- **Hàng đợi chờ duyệt** nổi lên ở đầu `/app/schedule` và trong checklist onboarding: thẻ bài nháp, sửa tại chỗ, nút Duyệt / Bỏ qua, hiện giờ đăng dự kiến.
- Trạng thái rule luôn nhìn thấy: Đang chạy / Tạm dừng (kèm lý do và nút bật lại). Không dùng màu làm tín hiệu duy nhất.
- Mở rộng onboarding: sau khi có bài đầu tiên đã đăng, gợi ý **bước 4 tùy chọn** "Bật đăng hằng ngày". Không chặn người dùng, bỏ qua được.
- Trước khi chọn `AUTO`: hộp xác nhận nói rõ "Bài sẽ tự đăng công khai, không qua bước duyệt", và nhắc quyền sửa/hủy trong Lịch đăng.

## Giai đoạn và tiêu chí hoàn thành

| Giai đoạn | Nội dung | Hoàn thành khi |
|---|---|---|
| **A. Nền tảng** | Migration, entity, repository, cờ tính năng, ràng buộc duy nhất `(rule_id, run_date)` | Migration chạy trên DB trống và trên staging có dữ liệu; test hai worker cùng chèn chỉ ra một dòng |
| **B. Planner và soạn nội dung** | Job định kỳ, gọi dịch vụ AI, ghi quota, trạng thái `GENERATING → PENDING_REVIEW/FAILED` | Test tích hợp với PostgreSQL thật; hết quota thì dừng rule, không lặp vô hạn |
| **C. Duyệt và tạo lịch** | API duyệt/bỏ qua, tạo `scheduled_post` đúng giờ theo múi giờ rule | Bài duyệt xuất hiện ở Lịch đăng và được scheduler đăng; quá giờ chưa duyệt thì `SKIPPED` |
| **D. Bảo vệ** | Tự tạm dừng, chống lặp, giới hạn, kiểm toán | Mỗi điều kiện dừng có test; `UNKNOWN` không bao giờ dẫn tới đăng tiếp |
| **E. Giao diện** | Wizard, hàng đợi duyệt, trạng thái rule, bước onboarding tùy chọn | Hoàn tất được luồng tạo rule → duyệt → thấy bài ở Lịch đăng trên 375px và 1440px |
| **F. Pilot** | Bật cờ cho vài tài khoản thử, chế độ `REVIEW`, Page thử nghiệm, theo dõi 2 tuần | Không có bài trùng, không có bài đăng ngoài ý muốn, hàng đợi duyệt được dùng thật |
| **G. `AUTO`** | Mở theo từng người dùng sau khi F ổn | Chỉ khi đã diễn tập timeout/restart và đối soát `UNKNOWN` trên staging như tài liệu scheduler yêu cầu |

## Kiểm thử cần có

- Hai planner chạy đồng thời chỉ tạo một `automation_run` mỗi ngày mỗi rule.
- Ranh giới múi giờ: rule `Asia/Ho_Chi_Minh` lúc 00:30 và 23:30; múi giờ có chuyển giờ mùa hè.
- Hết quota/ngân sách AI giữa chừng; AI trả lỗi; AI bị tắt bằng công tắc.
- Token lỗi giữa hai ngày; bài hôm trước `UNKNOWN` → rule tự dừng.
- Chủ tài khoản bị khóa; kênh bị ngắt khi rule còn bật.
- Người dùng A không đọc/sửa được rule và bản nháp của người dùng B.

## Chi phí và vận hành

- Mỗi bài tự soạn tốn một lượt AI. Gói Free có hạn mức theo kỳ, nên rule hằng ngày sẽ dùng khoảng 30 lượt mỗi kỳ 30 ngày và có thể hết quota; giao diện phải nói rõ điều này khi tạo rule và gợi ý Pro khi gần hết. Cần xác nhận hạn mức Free/Pro hiện hành trong cấu hình trước khi viết nội dung này lên giao diện.
- Theo dõi: số run mỗi trạng thái, tỉ lệ `FAILED`/`SKIPPED`, số rule tự dừng và lý do, chi phí AI cho automation tách riêng.

## Quyết định cần chốt

1. **Đợt đầu chỉ `REVIEW`** (khuyến nghị) hay cho `AUTO` ngay? Khuyến nghị `REVIEW` vì pilot hiện khuyến cáo không dùng lịch tự động cho nội dung quan trọng.
2. **Nguồn nội dung đợt đầu**: chỉ `BRIEF` (AI soạn từ mô tả) hay thêm danh sách nội dung có sẵn (`EVERGREEN_LIST`)? Khuyến nghị `BRIEF` trước, vì danh sách có sẵn dễ gây lặp.
3. **Nền tảng đợt đầu**: Facebook Page (nền tảng pilot chính). Instagram/Threads để sau cho tới khi chúng được kiểm chứng.
4. **Tính phí**: automation là tính năng riêng của Pro, hay chỉ dùng chung quota AI hiện có?
5. **Thông báo chờ duyệt**: chỉ trong ứng dụng, hay thêm email? Email cần `MAIL_*` hoạt động và chính sách gửi mail.
