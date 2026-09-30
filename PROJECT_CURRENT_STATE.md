# Project Current State

> Phạm vi phân tích: source hiện có tại repository ngày 2026-09-30. Không tính `node_modules`, `target`, `dist`, `.git`, IDE files và dependency cache. Trạng thái được kết luận từ implementation thực tế, không dựa riêng vào README/tên file.

## 1. Project Overview

Postiva là một modular monolith gồm React SPA, Spring Boot API và PostgreSQL. Luồng sản phẩm chính hiện có là: đăng ký bằng OTP email, đăng nhập JWT, kết nối Facebook/Instagram/Threads, tạo nội dung thủ công hoặc nhờ OpenAI, upload media, đăng ngay hoặc đặt lịch, sau đó xem lịch/báo cáo và quản lý bài.

Kết quả rà soát:

- 33 feature/nhóm feature được nhận diện.
- 17 `COMPLETE`, 9 `PARTIAL`, 4 `STUB`, 3 `BROKEN`.
- 51 HTTP API endpoint trong 12 controller.
- 5 external integration: OpenAI, Facebook Graph API, Instagram Graph API, Threads Graph API, SMTP.
- Frontend production build thành công.
- Backend có 13 unit test nhưng chưa chạy được trong môi trường phân tích vì Maven local repository ngoài workspace không cho phép ghi tracking file. Đây là trạng thái xác minh `UNKNOWN`, không phải bằng chứng test fail.

Các điểm nổi bật nhất:

- Luồng đăng Facebook/Instagram/Threads đã có publisher thật, không phải mock.
- Access token social được mã hóa AES-GCM trước khi lưu.
- Callback OAuth Facebook được public đúng; callback Instagram và Threads hiện bị Spring Security chặn vì không nằm trong allow-list.
- Meta deauthorization/data-deletion callback cũng bị yêu cầu JWT, vì vậy không thể được Meta gọi trực tiếp.
- Comments, reactions/engagement và realtime chỉ có UI/client skeleton; backend chưa có implementation tương ứng.
- `application.yml` chứa JWT secret mặc định hard-code và các biến `POSTIVA_JWT_*` nằm ở namespace không được `JwtService` sử dụng.

## 2. Technology Stack

| Layer | Công nghệ thực tế | Bằng chứng chính |
|---|---|---|
| Backend | Java 21, Spring Boot 3.3.5 | `backend/pom.xml` |
| HTTP | Spring MVC; WebClient từ WebFlux cho outbound HTTP | `spring-boot-starter-web`, `spring-boot-starter-webflux` |
| Security | Spring Security, stateless Bearer JWT, BCrypt | `SecurityConfig.java`, `JwtService.java`, `AuthService.java` |
| ORM | Spring Data JPA + Hibernate; JSONB qua `@JdbcTypeCode(SqlTypes.JSON)` | entity/repository packages |
| Database | PostgreSQL 16 | `database/schema.sql`, `docker-compose.yml` |
| Frontend | React 19, TypeScript 6, React Router 7, Vite 8 | `frontend/package.json` |
| State/client | Axios, Zustand (store rất nhỏ), React hooks | `frontend/src/lib/api.ts`, `frontend/src/store/appStore.ts` |
| UI | Tailwind CSS 4, Lucide React | `frontend/package.json`, `frontend/src/index.css` |
| Realtime client | STOMP JS và SockJS client | `frontend/src/lib/realtime.ts`, `frontend/src/hooks/useWebSocket.ts`; backend chưa có server |
| AI | OpenAI Chat Completions + Structured Outputs (`json_schema`) | `OpenAiService.java` |
| Social | Meta/Facebook Graph, Instagram Graph, Threads Graph | `backend/.../social/client/*GraphClient.java` |
| Email | Spring Mail / SMTP | `EmailService.java` |
| File storage | Local filesystem + database metadata | `LocalFileStorage.java`, `FileService.java` |
| Build | Maven; npm/Vite | `backend/pom.xml`, `frontend/package.json` |
| Deployment | Multi-stage Docker, Nginx TLS/reverse proxy, Docker Compose, Jenkins | Dockerfiles, `nginx.conf`, `docker-compose.yml`, `Jenkinsfile` |

Các thư viện backend quan trọng: JJWT 0.11.5, Spring Validation, Jackson, Lombok (khai báo nhưng source chủ yếu dùng Java thường). Không thấy SDK OpenAI/Meta; integration dùng `WebClient` trực tiếp.

## 3. Architecture

Backend là **modular monolith theo feature, bên trong dùng layered architecture/MVC**. Một Spring Boot process chứa các module `auth`, `post`, `social`, `scheduler`, `file`, `calendar`, `scoring`, `template`, `business`, `appdata`.

Dependency thực tế:

```text
React Page / Component
        ↓ Axios + Bearer JWT
Spring @RestController
        ↓
Feature Service / orchestration service
        ↓                    ↓
Spring Data Repository       WebClient / SMTP / local filesystem
        ↓                    ↓
PostgreSQL                   OpenAI / Meta / mail server
```

Luồng publish dùng Strategy pattern:

```text
SocialPostService / FacebookBusinessPostService
        ↓
PublishingService
        ↓ chọn theo Platform
PlatformPublisher
  ├─ FacebookPublisher  → FacebookGraphClient
  ├─ InstagramPublisher → InstagramGraphClient
  └─ ThreadsPublisher   → ThreadsGraphClient
```

Các module không hoàn toàn độc lập: `appdata` truy cập trực tiếp nhiều repository/client để dựng read model và sửa/xóa bài; `social` phụ thuộc `post`, `file`, `scheduler`; `scheduler` phụ thuộc `social` và `post`. Vì vậy đây không phải Clean Architecture hoặc microservices.

## 4. Project Structure

```text
postiva/
├─ backend/
│  ├─ src/main/java/com/postiva/
│  │  ├─ ai/          # prompt, gọi OpenAI, parse structured output
│  │  ├─ appdata/     # read model cho dashboard/lịch/báo cáo, sửa/xóa bài
│  │  ├─ auth/        # đăng ký OTP, login, JWT, current user
│  │  ├─ business/    # entity/repository hồ sơ doanh nghiệp; chưa có flow
│  │  ├─ calendar/    # sinh lịch nội dung 7 ngày
│  │  ├─ common/      # response, enum, global exception handler
│  │  ├─ config/      # security, CORS, properties
│  │  ├─ file/        # upload/serve/attach local media
│  │  ├─ mail/        # SMTP email
│  │  ├─ post/        # product brief, generated post, AI generation
│  │  ├─ scheduler/   # schedule, publish worker, retry, logs
│  │  ├─ scoring/     # rule-based post score
│  │  ├─ social/      # OAuth, token, Graph clients, publishers, sync
│  │  ├─ template/    # industry templates và seeder
│  │  └─ user/        # User entity/repository
│  ├─ src/main/resources/application.yml
│  ├─ src/test/       # 13 unit tests
│  ├─ .env.example
│  ├─ Dockerfile
│  └─ pom.xml
├─ frontend/
│  ├─ src/
│  │  ├─ components/  # post details/edit/delete/repost, common UI
│  │  ├─ hooks/       # WebSocket hook chưa có server tương ứng
│  │  ├─ lib/         # Axios, auth storage, app-data hooks, realtime
│  │  ├─ pages/       # landing, auth, create, connections, schedule, reports
│  │  ├─ store/       # Zustand backend URL store (không được page dùng)
│  │  ├─ App.tsx      # routes, auth guard, app shell
│  │  └─ main.tsx
│  ├─ nginx.conf
│  ├─ Dockerfile
│  └─ package.json
├─ database/schema.sql
├─ docker-compose.yml
├─ Jenkinsfile
└─ README.md
```

## 5. Feature Summary

| # | Feature | Status | Lý do |
|---:|---|---|---|
| 1 | Landing page và SPA routing | COMPLETE | Route public `/`, app shell và các route chính hoạt động trong source; frontend build thành công. |
| 2 | Đăng ký + OTP email | COMPLETE | OTP được sinh, hash BCrypt, có TTL, lưu pending registration, gửi SMTP và verify để tạo user. |
| 3 | Login/logout và JWT session | COMPLETE | BCrypt authentication, JWT filter, `/auth/me`, Axios interceptor và localStorage session đã nối end-to-end. |
| 4 | Lấy current user | COMPLETE | JWT subject → repository → `UserResponse`; frontend phục hồi phiên bằng `/auth/me`. |
| 5 | Industry templates | COMPLETE | Entity/repository/service/controller và seeder có đủ; API backend hoạt động độc lập. |
| 6 | AI content generation | COMPLETE | Product brief → OpenAI structured output → draft post → response; lỗi config/API được map rõ. |
| 7 | Đọc/cập nhật AI draft | COMPLETE | Ownership check theo user, GET/PUT post, lưu platform contents/hashtags/CTA. |
| 8 | Upload/serve/attach media | COMPLETE | Validate type/size, lưu local, metadata DB, attach/copy/replace theo post; UI upload được nối. |
| 9 | Lịch nội dung 7 ngày | PARTIAL | Backend tạo/lấy lịch thật; không có frontend gọi API, item chưa nối sang generated post. |
| 10 | Chấm điểm bài viết | PARTIAL | Backend rule-based và lưu `post_scores`; không có frontend gọi/hiển thị. |
| 11 | Facebook OAuth + lưu Page | COMPLETE | State one-time, code exchange, long-lived token, `/me/accounts`, AES-GCM token storage; callback được public. |
| 12 | Instagram OAuth | BROKEN | Service/client đầy đủ nhưng `/api/social/instagram/callback` bị security yêu cầu JWT; browser redirect không gửi Bearer token. |
| 13 | Threads OAuth | BROKEN | Tương tự Instagram: callback không nằm trong public allow-list. |
| 14 | Manual social connection | COMPLETE | Facebook/Instagram/Threads đều validate token bằng remote profile rồi lưu encrypted; UI có form. |
| 15 | List/check/refresh/disconnect social account | COMPLETE | Backend ownership check và UI Connections gọi đủ; refresh token áp dụng cho Instagram Login/Threads. |
| 16 | Token lifecycle | PARTIAL | Encrypt/decrypt và expiry check có; Instagram/Threads refresh có; Facebook không có automatic refresh và không lưu refresh token riêng. |
| 17 | Multi-platform composer/publish orchestration | PARTIAL | UI → `/social-posts` → post/media → từng target; method orchestration không transactional nên nhiều target có thể thành công một phần khi lỗi giữa vòng lặp. |
| 18 | Scheduled posting/background worker | COMPLETE | Lưu schedule, `@Scheduled`, trạng thái/log/attempt, publisher thật và manual retry đã có. |
| 19 | Danh sách schedule và retry | PARTIAL | API hoàn chỉnh nhưng frontend hiện chỉ đọc read model `/app-data/posts`, không gọi list/retry chuyên dụng. |
| 20 | Facebook publishing | COMPLETE | Text, một ảnh, nhiều ảnh, video/file hoặc URL; lưu external ID/log. |
| 21 | Instagram publishing | COMPLETE | Single/carousel image/video, container polling và publish; yêu cầu public media URL được validate. Có thể dùng manual connection dù OAuth callback đang hỏng. |
| 22 | Threads publishing | COMPLETE | Text/single media/carousel, polling và publish; có thể dùng manual connection. |
| 23 | Facebook post sync/import | COMPLETE | Import remote posts, tạo brief/post/schedule/log và xóa local record khi remote post biến mất. |
| 24 | Sửa/xóa post | PARTIAL | Scheduled/local post sửa được; published Facebook sync remote; published Instagram/Threads cố ý không hỗ trợ sửa/xóa. |
| 25 | Repost Facebook | COMPLETE | Modal dùng post cũ, upload/URL media và gọi Facebook business post flow. |
| 26 | Dashboard/reports | PARTIAL | Danh sách/trạng thái/chart số bài có dữ liệu thật; views/likes/comments/shares/reactions/followers luôn bằng 0. |
| 27 | Comments/reply/reactions | STUB | GET comments luôn trả `[]`; UI gọi `/comments/reply` nhưng backend không có endpoint; reaction metrics hard-code 0. |
| 28 | Realtime social events | STUB | Frontend STOMP/SockJS subscribe topic, backend không có dependency/config/handler WebSocket/STOMP. |
| 29 | Meta deauthorization/data deletion | BROKEN | Verify signed request và DB receipt đã có, nhưng tất cả callback này bị JWT protection; Meta không thể gọi. |
| 30 | Business profile | STUB | Chỉ có entity/repository/schema, không controller/service/frontend consumer. |
| 31 | AI generation audit logs | STUB | Chỉ có bảng SQL `ai_generation_logs`; không có entity/repository/service ghi log. |
| 32 | Publish-log viewing | PARTIAL | Backend endpoint/read model có; frontend không gọi hoặc hiển thị log riêng. |
| 33 | Docker/CI/CD deployment | PARTIAL | Build/push/deploy có; backend image skip tests, pipeline không chạy test/lint và cấu hình server nằm trực tiếp trong Jenkinsfile. |

## 6. Feature → File Map

| Feature | Frontend | Controller/entry | Service/core | Persistence | External |
|---|---|---|---|---|---|
| Register/OTP | `frontend/src/pages/AuthPage.tsx` | `auth/controller/AuthController.java` | `AuthService.java`, `EmailService.java` | `PendingRegistrationRepository.java`, `UserRepository.java` | SMTP |
| Login/JWT | `AuthPage.tsx`, `App.tsx`, `lib/auth.ts`, `lib/api.ts` | `AuthController.java` | `JwtService.java`, `JwtAuthenticationFilter.java`, `CustomUserDetailsService.java` | `UserRepository.java` | - |
| AI generate post | `pages/CreatePostPage.tsx` | `post/controller/PostController.java` | `PostService.java`, `OpenAiService.java`, `PromptBuilder.java`, `TemplateService.java` | `ProductBriefRepository.java`, `GeneratedPostRepository.java` | OpenAI |
| Draft read/update | Chưa gọi GET/PUT trực tiếp từ page hiện tại | `PostController.java` | `PostService.java` | `GeneratedPostRepository.java` | - |
| Template | Không có consumer frontend | `TemplateController.java` | `TemplateService.java`, `TemplateSeeder.java` | `IndustryTemplateRepository.java` | - |
| Score | Không có consumer frontend | `ScoringController.java` | `ScoringService.java` | `PostScoreRepository.java` | - |
| Calendar 7 ngày | Không có consumer frontend | `CalendarController.java` | `CalendarService.java`, `TemplateService.java` | `ContentCalendarRepository.java`, `CalendarItemRepository.java` | - |
| File/media | `CreatePostPage.tsx`, `RepostPostModal.tsx`, `lib/media.ts` | `FileController.java` | `FileService.java`, `LocalFileStorage.java` | `UploadedFileRepository.java` | Local filesystem |
| Facebook OAuth | `ConnectionsPage.tsx` | `MetaController.java` | `MetaOAuthService.java`, `MetaOAuthStateService.java`, `SocialAccountService.java`, `TokenCipher.java` | `SocialAccountRepository.java` | Facebook Graph |
| Instagram OAuth | `ConnectionsPage.tsx` | `InstagramController.java` | `InstagramOAuthService.java`, shared state/account services | `SocialAccountRepository.java` | Instagram Graph |
| Threads OAuth | `ConnectionsPage.tsx` | `ThreadsController.java` | `ThreadsOAuthService.java`, shared state/account services | `SocialAccountRepository.java` | Threads Graph |
| Account management | `ConnectionsPage.tsx` | `MetaController.java`, Instagram/Threads manual endpoints | `SocialAccountService.java` | `SocialAccountRepository.java` | 3 Graph APIs |
| Create/publish multi-platform | `CreatePostPage.tsx` | `SocialPostController.java` | `SocialPostService.java`, `PublishingService.java`, 3 publishers | brief/post/schedule/log/file repositories | 3 Graph APIs |
| Direct Facebook/repost | `RepostPostModal.tsx` | `FacebookBusinessPostController.java` | `FacebookBusinessPostService.java`, `FacebookPublisher.java` | brief/post/schedule/log repositories | Facebook Graph |
| Background schedule/retry | `SchedulePage.tsx` chỉ dùng read model | `PublishingController.java` | `PublishingService.java`, `PublishingScheduler.java` | `ScheduledPostRepository.java`, `PublishLogRepository.java` | 3 Graph APIs |
| Sync Facebook history | `ConnectionsPage.tsx` | `MetaController.syncPosts` | `FacebookPostSyncService.java` | brief/post/schedule/log repositories | Facebook Graph |
| Report/schedule read model | `ReportsPage.tsx`, `SchedulePage.tsx`, `components/PostDetails.tsx` | `AppDataController.java` | Controller tự orchestration + `SocialAccountService`, `FileService` | post/schedule/log repositories | Facebook Graph cho edit/delete |
| Meta data deletion | Không có UI (provider callback) | 3 social controllers | `MetaSignedRequestService.java`, `SocialAccountService.java` | `SocialDataDeletionRequestRepository.java` | Meta signed request |
| Realtime/comments | `lib/realtime.ts`, `hooks/useWebSocket.ts`, `PostDetails.tsx` | Chỉ có stub comments GET | Không có | Không có | Không có server |
| Deployment | - | - | Docker/Nginx/Jenkins | PostgreSQL volume, upload volume | Docker Hub/remote host |

## 7. API Endpoints

Quy ước authentication dựa trên `SecurityConfig`: `Public` chỉ áp dụng cho 3 auth POST, GET/HEAD files, Facebook callback và `/error`; các endpoint còn lại là `JWT`. Điều này làm một số provider callback bị `BROKEN` như ghi bên dưới.

| Method | Endpoint | Controller/method | Auth | Mô tả/trạng thái |
|---|---|---|---|---|
| POST | `/api/auth/register` | `AuthController.register` | Public | Tạo pending registration và gửi OTP |
| POST | `/api/auth/register/verify` | `AuthController.verifyRegistration` | Public | Verify OTP, tạo user, trả JWT |
| POST | `/api/auth/login` | `AuthController.login` | Public | Login email/password, trả JWT |
| GET | `/api/auth/me` | `AuthController.me` | JWT | Current user |
| POST | `/api/files/upload` | `FileController.upload` | JWT | Upload media multipart |
| GET | `/api/files/{fileId}` | `FileController.get` | Public | Serve media inline |
| POST | `/api/posts/generate` | `PostController.generate` | JWT | Sinh content bằng OpenAI và lưu draft |
| GET | `/api/posts/{postId}` | `PostController.getPost` | JWT | Lấy generated post thuộc user |
| PUT | `/api/posts/{postId}` | `PostController.update` | JWT | Sửa draft fields |
| POST | `/api/posts/{postId}/score` | `ScoringController.score` | JWT | Rule-based score và lưu kết quả |
| GET | `/api/templates` | `TemplateController.getTemplates` | JWT | Danh sách template ngành |
| GET | `/api/templates/{industryCode}` | `TemplateController.getTemplate` | JWT | Template theo code |
| POST | `/api/calendars/generate` | `CalendarController.generate` | JWT | Sinh lịch 7 ngày |
| GET | `/api/calendars/{calendarId}` | `CalendarController.getCalendar` | JWT | Lấy lịch thuộc user |
| POST | `/api/social/meta/connect-url` | `MetaController.connectUrl` | JWT | URL Facebook OAuth |
| GET | `/api/social/meta/callback` | `MetaController.callback` | Public | Facebook OAuth callback |
| GET | `/api/social/meta/accounts` | `MetaController.accounts` | JWT | List social accounts (không chỉ Meta) |
| POST | `/api/social/meta/facebook/manual` | `MetaController.connectManual` | JWT | Kết nối Facebook bằng Page token |
| POST | `/api/social/meta/facebook/test-post` | `MetaController.testPost` | JWT | Tạo unpublished Facebook test post |
| POST | `/api/social/meta/facebook/sync-posts` | `MetaController.syncPosts` | JWT | Đồng bộ lịch sử Page |
| POST | `/api/social/meta/accounts/{accountId}/check` | `MetaController.checkAccount` | JWT | Check token/profile remote |
| POST | `/api/social/meta/accounts/{accountId}/refresh` | `MetaController.refreshAccount` | JWT | Refresh token/profile khi phù hợp |
| DELETE | `/api/social/meta/accounts/{accountId}` | `MetaController.disconnect` | JWT | Clear token và soft-disconnect |
| POST | `/api/social/meta/deauthorize` | `MetaController.deauthorize` | JWT (BROKEN) | Provider callback lẽ ra phải public |
| POST | `/api/social/meta/delete-data` | `MetaController.deleteData` | JWT (BROKEN) | Provider data deletion callback |
| GET | `/api/social/meta/delete-data/status/{confirmationCode}` | `MetaController.deletionStatus` | JWT | Tra receipt xoá dữ liệu |
| GET | `/api/social/instagram/connect-url` | `InstagramController.connectUrl` | JWT | URL Instagram OAuth |
| GET | `/api/social/instagram/callback` | `InstagramController.callback` | JWT (BROKEN) | Browser OAuth callback bị chặn |
| POST | `/api/social/instagram/manual` | `InstagramController.connectManual` | JWT | Kết nối manual |
| POST | `/api/social/instagram/deauthorize` | `InstagramController.deauthorize` | JWT (BROKEN) | Provider callback bị chặn |
| POST | `/api/social/instagram/delete-data` | `InstagramController.deleteData` | JWT (BROKEN) | Provider callback bị chặn |
| GET | `/api/social/instagram/delete-data/status/{confirmationCode}` | `InstagramController.deletionStatus` | JWT | Tra receipt |
| GET | `/api/social/threads/connect-url` | `ThreadsController.connectUrl` | JWT | URL Threads OAuth |
| GET | `/api/social/threads/callback` | `ThreadsController.callback` | JWT (BROKEN) | Browser OAuth callback bị chặn |
| POST | `/api/social/threads/manual` | `ThreadsController.connectManual` | JWT | Kết nối manual |
| POST | `/api/social/threads/deauthorize` | `ThreadsController.deauthorize` | JWT (BROKEN) | Provider callback bị chặn |
| POST | `/api/social/threads/delete-data` | `ThreadsController.deleteData` | JWT (BROKEN) | Provider callback bị chặn |
| GET | `/api/social/threads/delete-data/status/{confirmationCode}` | `ThreadsController.deletionStatus` | JWT | Tra receipt |
| POST | `/api/social-posts` | `SocialPostController.create` | JWT | Tạo/update post và publish/schedule nhiều target |
| POST | `/api/facebook-business/posts` | `FacebookBusinessPostController.create` | JWT | Tạo/repost Facebook trực tiếp |
| POST | `/api/publishing/schedule` | `PublishingController.schedule` | JWT | Tạo schedule |
| POST | `/api/publishing/now` | `PublishingController.publishNow` | JWT | Publish ngay |
| GET | `/api/publishing` | `PublishingController.list` | JWT | List schedule của user |
| POST | `/api/publishing/{scheduledPostId}/retry` | `PublishingController.retry` | JWT | Đưa FAILED về SCHEDULED |
| GET | `/api/app-data/overview` | `AppDataController.overview` | JWT | Summary dashboard; engagement stub 0 |
| GET | `/api/app-data/posts` | `AppDataController.posts` | JWT | Flatten post theo schedule/platform |
| GET | `/api/app-data/posts/{postId}/comments` | `AppDataController.comments` | JWT | STUB: luôn trả list rỗng |
| PUT | `/api/app-data/posts/{postId}` | `AppDataController.updatePost` | JWT | Sửa local/scheduled; sync published Facebook |
| DELETE | `/api/app-data/posts/{postId}` | `AppDataController.deletePost` | JWT | Xóa local/Facebook; không xóa published IG/Threads |
| GET | `/api/app-data/channels` | `AppDataController.channels` | JWT | Read model social channel |
| GET | `/api/app-data/publish-logs` | `AppDataController.publishLogs` | JWT | Publish logs của user |

Frontend còn gọi `POST /api/comments/reply` trong `frontend/src/components/PostDetails.tsx`, nhưng không có controller tương ứng. Endpoint đó không nằm trong 51 endpoint backend.

## 8. Authentication Flow

### Register

```text
AuthPage.handleSubmit
→ POST /api/auth/register
→ AuthController.register
→ AuthService.register
→ normalize email + check UserRepository
→ BCrypt hash password và OTP
→ PendingRegistrationRepository.save (TTL 10 phút)
→ EmailService.sendRegistrationOtp → SMTP
→ user nhập mã
→ POST /api/auth/register/verify
→ AuthService.verifyRegistration
→ BCrypt verify OTP → UserRepository.save → delete pending
→ JwtService.generateToken
→ AuthPage/saveAuthSession(localStorage)
```

### Login/session restore

```text
POST /api/auth/login
→ AuthenticationManager/DaoAuthenticationProvider
→ CustomUserDetailsService → UserRepository
→ BCrypt verify → JwtService.generateToken
→ localStorage `postiva.auth`
→ Axios interceptor gắn Authorization: Bearer ...
→ JwtAuthenticationFilter xác minh mỗi request
→ App.tsx gọi GET /api/auth/me để phục hồi user
```

Không có refresh-token endpoint/entity. JWT hết hạn dẫn tới 401; Axios xoá session và phát `postiva:auth-expired`.

## 9. Social Account Connection Flow

### Facebook OAuth — COMPLETE

```text
ConnectionsPage
→ GET /api/social/meta/connect-url (JWT)
→ MetaOAuthService.createConnectUrl
→ MetaOAuthStateService.issue: state in-memory, TTL 10 phút
→ Facebook OAuth dialog
→ GET /api/social/meta/callback?code&state (Public)
→ consume state → FacebookGraphClient.exchangeCode
→ exchangeLongLivedUserToken
→ GET /me + GET /me/accounts
→ SocialAccountService.saveFacebookPage
→ TokenCipher.encrypt(AES-GCM)
→ SocialAccountRepository.save
→ redirect /app/connections?facebook=connected
```

### Instagram/Threads OAuth — BROKEN tại callback security

Hai flow có code tương đương: create URL → one-time state → exchange short/long-lived token → `/me` → encrypt/save. Tuy nhiên `SecurityConfig` chỉ permit `/api/social/meta/callback`; Instagram/Threads callback yêu cầu JWT trong khi redirect trình duyệt không gắn Authorization header.

### Manual connection — COMPLETE

`ConnectionsPage` gọi endpoint `/manual` theo platform. `SocialAccountService` gọi remote profile để xác nhận token/account trước khi mã hóa và upsert account. Đây là đường vòng đang cho phép Instagram/Threads publishing hoạt động khi OAuth callback chưa sửa.

### Account lifecycle

- `checkCurrentAccount`: expiry check + remote profile fetch.
- `refreshCurrentAccount`: refresh long-lived Instagram Login/Threads token nếu gần hết hạn, rồi refresh profile.
- `prepareForPublishing`: tự refresh trong cửa sổ 7 ngày.
- Facebook Page token không có refresh implementation; khi hết hạn yêu cầu kết nối lại.
- `disconnect`: xóa ciphertext token, expiry và đánh dấu `disconnectedAt`; không hard-delete.

## 10. Automatic Posting Flow

### Luồng thực tế từ Create Post page

```text
CreatePostPage
  ├─ tùy chọn POST /api/posts/generate
  │    → PostService → OpenAiService → OpenAI /v1/chat/completions
  │    → product_briefs + generated_posts(DRAFT)
  ├─ POST /api/files/upload (0..20 media)
  └─ POST /api/social-posts
       → SocialPostService.create
       → validate account ownership/platform/content/time
       → create hoặc update ProductBrief + GeneratedPost
       → FileService.replacePostMedia
       → mỗi target:
            ├─ publish ngay → PublishingService.publishNow
            └─ đặt lịch    → PublishingService.schedule
```

### Publish ngay

```text
PublishingService.create
→ ScheduledPost(status=SCHEDULED) + post status=SCHEDULED
→ publishOne
→ status=PUBLISHING, attemptCount++
→ SocialAccountService.prepareForPublishing
→ decrypt access token
→ FileService.allMediaForPost
→ PlatformPublisher.publish
→ Graph API
→ success: PUBLISHED + externalPostId + PublishLog SUCCESS
→ failure: FAILED + lastError + PublishLog FAILED
→ refresh aggregate GeneratedPost status
```

Lỗi provider trong `publishOne` được bắt và chuyển thành target `FAILED`, không ném ra ngoài. Vì vậy response `/social-posts` có thể chứa target thành công và thất bại cùng lúc.

### Scheduled publish

```text
ScheduledPost(status=SCHEDULED, scheduledTime)
→ PublishingScheduler @Scheduled fixedDelay
→ PublishingService.publishDuePosts
→ query tối đa 20 record đến hạn
→ publishOne cho từng record
→ trạng thái/log như publish ngay
```

Retry là thủ công: `POST /api/publishing/{id}/retry` chỉ chấp nhận `FAILED`, reset về `SCHEDULED` với thời gian hiện tại; worker sẽ xử lý lần sau.

### Platform behavior

- Facebook: text; một ảnh; album tối đa 10 ảnh; một video; URL ảnh/video.
- Instagram: bắt buộc media; JPEG hoặc MP4/MOV; carousel tối đa 10; media phải có public URL.
- Threads: text hoặc media; carousel tối đa 20; media phải có public URL.
- Không thấy automatic retry/backoff. Chỉ có polling container readiness cho Instagram/Threads và manual retry record FAILED.
- Không có webhook nhận sự kiện publish. Các endpoint signed request là deauthorize/data deletion, không phải content webhook.

## 11. External API Integrations

| API | Purpose/auth | Environment variables | Files | External endpoints gọi |
|---|---|---|---|---|
| OpenAI | Sinh post; Bearer API key | `OPENAI_API_KEY`, `OPENAI_MODEL` | `OpenAiService.java`, `PromptBuilder.java`, `OpenAiProperties.java`, `application.yml` | `POST https://api.openai.com/v1/chat/completions` |
| Facebook Graph | OAuth, Page profile/list, publish/update/delete/sync | `META_APP_ID`, `META_APP_SECRET`, `META_API_VERSION`, `META_REDIRECT_URI` | `MetaOAuthService.java`, `FacebookGraphClient.java`, `FacebookPublisher.java`, `FacebookPostSyncService.java` | OAuth dialog; `/oauth/access_token`, `/me`, `/me/accounts`, `/{pageId}`, `/{pageId}/feed`, `/photos`, `/videos`, `/posts`, `/{postId}` |
| Instagram Graph | OAuth, profile, token refresh, media containers/publish | `INSTAGRAM_APP_ID`, `INSTAGRAM_APP_SECRET`, `INSTAGRAM_API_VERSION`, `INSTAGRAM_REDIRECT_URI` | `InstagramOAuthService.java`, `InstagramGraphClient.java`, `InstagramPublisher.java` | Instagram authorize/API token; Graph `/access_token`, `/refresh_access_token`, `/me`, `/{accountId}/media`, `/media_publish`, container status |
| Threads Graph | OAuth, profile, token refresh, container/publish | `THREADS_APP_ID`, `THREADS_APP_SECRET`, `THREADS_REDIRECT_URI` | `ThreadsOAuthService.java`, `ThreadsGraphClient.java`, `ThreadsPublisher.java` | Threads authorize; Graph `/oauth/access_token`, `/access_token`, `/refresh_access_token`, `/me`, `/me/threads`, `/me/threads_publish`, container status |
| SMTP | OTP, registration-success, login notification | `POSTIVA_MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | `EmailService.java`, `AuthService.java`, `application.yml` | SMTP server cấu hình |

Không có Google, TikTok, storage cloud, payment hoặc email API khác trong source.

## 12. Database Model

| Table/entity | File | Purpose | Related feature/status |
|---|---|---|---|
| `users` / `User` | `user/entity/User.java` | Account, password hash, role | Auth — COMPLETE |
| `pending_registrations` / `PendingRegistration` | `auth/entity/PendingRegistration.java` | OTP registration pending | Register — COMPLETE |
| `business_profiles` / `BusinessProfile` | `business/entity/BusinessProfile.java` | Shop/business profile | STUB, unused |
| `industry_templates` / `IndustryTemplate` | `template/entity/IndustryTemplate.java` | Template lists JSONB | COMPLETE |
| `product_briefs` / `ProductBrief` | `post/entity/ProductBrief.java` | User/AI content input | Generate/publish |
| `generated_posts` / `GeneratedPost` | `post/entity/GeneratedPost.java` | Platform contents, CTA, media hint, status | Draft/publish |
| `post_scores` / `PostScore` | `scoring/entity/PostScore.java` | Rule score history | PARTIAL (backend only) |
| `content_calendars` / `ContentCalendar` | `calendar/entity/ContentCalendar.java` | 7-day calendar header | PARTIAL |
| `calendar_items` / `CalendarItem` | `calendar/entity/CalendarItem.java` | Daily idea/platform/status | PARTIAL |
| `uploaded_files` / `UploadedFile` | `file/entity/UploadedFile.java` | Local media metadata | COMPLETE |
| `social_accounts` / `SocialAccount` | `social/entity/SocialAccount.java` | Connected provider account + encrypted token | Social connect/publish |
| `scheduled_posts` / `ScheduledPost` | `scheduler/entity/ScheduledPost.java` | Queue/state/attempt/error | Scheduling |
| `publish_logs` / `PublishLog` | `scheduler/entity/PublishLog.java` | Success/failure/external ID | Publishing |
| `ai_generation_logs` | chỉ `database/schema.sql` | Intended AI audit/cost log | STUB: không có Java model/write path |
| `social_data_deletion_requests` / `SocialDataDeletionRequest` | `social/entity/SocialDataDeletionRequest.java` | Confirmation receipt | Entity có nhưng thiếu trong `schema.sql`; Hibernate `ddl-auto:update` có thể tự tạo |

Relationship logic từ schema/ID fields:

```text
User
├─ PendingRegistration (qua email, không FK)
├─ BusinessProfile
├─ ProductBrief ── GeneratedPost ── PostScore
│                     ├─ UploadedFile
│                     └─ ScheduledPost ── PublishLog
├─ ContentCalendar ── CalendarItem ──(optional) GeneratedPost
├─ UploadedFile
└─ SocialAccount ── ScheduledPost
```

Entity dùng UUID ID fields thay vì JPA object relationship (`@ManyToOne` không xuất hiện). Quan hệ và ownership chủ yếu được service/repository query kiểm soát.

Schema/deployment observations:

- `database/schema.sql` drop/recreate các bảng khi chạy init script trên database volume mới; đây không phải migration versioned.
- `spring.jpa.hibernate.ddl-auto=update` đồng thời được bật, nên schema SQL và entity có thể tự drift.
- `social_data_deletion_requests` có entity nhưng không có DDL thủ công.
- `ai_generation_logs` có DDL nhưng không có entity/code sử dụng.
- Compose đặt database container là `mydatabase`, trong khi backend default URL dùng database `postiva`; production phải cung cấp `POSTIVA_DB_URL` khớp.

## 13. Frontend Feature Map

| Feature | Page/component | API client/call | Backend API |
|---|---|---|---|
| Landing | `HomePage.tsx` | Không | - |
| Auth | `AuthPage.tsx`, `App.tsx`, `lib/auth.ts`, `lib/api.ts` | Axios direct | `/auth/register`, `/register/verify`, `/login`, `/me` |
| Connections | `ConnectionsPage.tsx` | Axios direct + `useAppDataResource` | connect URLs/manual, check/refresh/disconnect/sync/test, `/app-data/channels` |
| Compose/AI/upload/publish | `CreatePostPage.tsx` | Axios direct | `/posts/generate`, `/files/upload`, `/social-posts`, `/app-data/channels` |
| Schedule calendar | `SchedulePage.tsx` | `useAppDataResource` + PUT/DELETE | `/app-data/posts` |
| Reports | `ReportsPage.tsx` | `useAppDataResource` + PUT/DELETE | `/app-data/overview`, `/app-data/posts` |
| Detail/comments | `PostDetails.tsx` | `useAppDataResource`, Axios | comments GET stub; reply endpoint missing |
| Repost | `RepostPostModal.tsx` | Axios | `/facebook-business/posts`, `/files/upload` |
| Realtime refetch | `lib/realtime.ts`, `hooks/useWebSocket.ts` | STOMP/SockJS | Backend missing |
| Preferences | `lib/preferences.tsx` | Local DOM/storage only | Cố định light + Vietnamese; không có user settings backend |

Frontend không gọi các backend API: templates, calendars, score, `/api/publishing` list/retry, publish logs, GET/PUT draft post trực tiếp. Zustand store chỉ giữ backend URL và không thấy consumer ngoài file khai báo.

## 14. Backend Feature Map

| Module | Controller | Service | Repository/entity | Ghi chú |
|---|---|---|---|---|
| auth/user/mail | `AuthController` | `AuthService`, JWT/security services, `EmailService` | user + pending repositories/entities | Stateless JWT, OTP SMTP |
| post/ai | `PostController` | `PostService`, `OpenAiService`, `PromptBuilder` | brief/post repositories/entities | Structured output |
| file | `FileController` | `FileService`, `LocalFileStorage` | uploaded file | GET media public |
| template | `TemplateController` | `TemplateService`, `TemplateSeeder` | industry template | Backend complete |
| calendar | `CalendarController` | `CalendarService` | calendar/item | Backend-only |
| scoring | `ScoringController` | `ScoringService` | post score | Backend-only |
| social account/OAuth | Meta/Instagram/Threads controllers | OAuth/state/account/signed-request services | social account/deletion request | Security callback gaps |
| social publish | `SocialPostController`, `FacebookBusinessPostController` | orchestration + publishers + Graph clients | post/file/schedule/log | Real outbound publish |
| scheduler | `PublishingController` | `PublishingService`, `PublishingScheduler` | schedule/log | Poll max 20 per cycle |
| app read model | `AppDataController` | mostly controller orchestration | post/schedule/log, account/file service | Metrics/comments stub |
| business | none | none | entity/repository only | STUB |

## 15. Current System Flow

```text
Visitor opens landing page
        ↓
Register → receive OTP email → verify → JWT
or Login → JWT
        ↓
Connections page
        ├─ Facebook OAuth works
        ├─ Instagram/Threads OAuth callbacks currently blocked
        └─ Manual connection works for all three
        ↓
Create Post
        ├─ upload media
        ├─ optionally generate per-platform text with OpenAI
        └─ edit captions/CTA/hashtags and select connected accounts
        ↓
POST /api/social-posts
        ├─ Publish now → provider Graph API
        └─ Schedule → scheduled_posts → Spring scheduler → provider Graph API
        ↓
publish_logs + generated post status
        ↓
Schedule/Reports pages read /api/app-data
        ├─ edit/delete local or published Facebook post
        ├─ repost to Facebook
        └─ engagement/comments remain placeholder
```

## 16. Complete Features

Các feature số 1–8, 11, 14–15, 18, 20–23 và 25 trong Feature Summary được đánh giá `COMPLETE`. “Complete” ở đây nghĩa là flow code tương đối đầy đủ; feature cần credential/dịch vụ bên ngoài vẫn phụ thuộc cấu hình runtime hợp lệ.

Unit tests hiện có tập trung vào:

- OpenAI strict request schema/response normalization/missing key.
- JWT generation/validation/tampering.
- AES-GCM token cipher.
- Facebook OAuth long-lived token exchange.
- Meta signed request verification/deletion.
- Social token refresh/deauthorization/deletion behavior.

## 17. Partial Features

| Feature | Phần đã có | Phần còn thiếu/hạn chế có bằng chứng |
|---|---|---|
| Calendar | Controller/service/entity/repository | Không có frontend; calendar item không được chuyển thành post |
| Scoring | Rule engine + DB save | Không có frontend/read history endpoint |
| Token lifecycle | Encrypt, expiry, Instagram/Threads refresh | Facebook refresh không có; không có refresh-token model chung |
| Multi-target publish | Validate/create/media/publish từng target | Không có outer transaction; partial success có thể để state dở |
| List/retry schedule | API backend | UI không gọi retry/list; chỉ hiển thị app-data |
| Edit/delete | Local + published Facebook | Published Instagram/Threads không hỗ trợ |
| Reports | Count/status/platform/time chart | Engagement/follower/comments/reactions đều hard-code 0 |
| Publish logs | Backend endpoint | Không có frontend view |
| CI/CD | Docker build/push/deploy | Không chạy test/lint; backend Docker build dùng `-DskipTests=true` |

## 18. Missing / Unfinished Implementation

Chỉ liệt kê phần được source reference hoặc đã có một phía:

- Frontend `PostDetails.tsx` gọi `POST /api/comments/reply`, backend không có endpoint.
- Frontend subscribe `/ws-social`, `/topic/facebook`, `/topic/comments`, backend không có WebSocket/STOMP config/dependency/producer.
- Backend comments endpoint tồn tại nhưng luôn trả list rỗng.
- Backend trả các field engagement/reaction/follower nhưng luôn là `0`/`"0"`.
- Instagram và Threads OAuth callback có controller/service nhưng thiếu public security matcher.
- Cả ba provider có deauthorize/delete-data controller nhưng thiếu public security matcher.
- `BusinessProfile` và repository không có service/controller/frontend.
- `ai_generation_logs` có schema nhưng không có Java entity/repository và không được `OpenAiService` ghi.
- `social_data_deletion_requests` có Java entity/repository nhưng thiếu DDL trong `database/schema.sql`.
- Frontend không dùng calendar, scoring, templates, publishing list/retry, publish-log endpoints đã có ở backend.
- User preferences/settings không tồn tại ở backend; frontend provider cố định light theme và Vietnamese.

## 19. TODO / FIXME / Suspicious Code

Không tìm thấy TODO/FIXME/HACK/NotImplemented/`UnsupportedOperationException` trong production code. Các kết quả đáng chú ý:

| File | Line/method | Problem |
|---|---|---|
| `backend/.../appdata/controller/AppDataController.java` | `comments` | Trả `List.of()` không đọc provider/DB: stub rõ ràng. |
| Cùng file | `overview`, `postMap`, `fallbackPostMap` | Toàn bộ engagement/reaction metrics hard-code 0. |
| `frontend/src/components/PostDetails.tsx` | `submitReply` | Gọi endpoint backend không tồn tại. |
| `frontend/src/lib/realtime.ts` | client/subscriptions | Subscribe topic không có server backend. |
| `frontend/src/hooks/useWebSocket.ts` | `useWebSocket` | Hook song song, cũng không có server; không thấy consumer. |
| `backend/src/main/resources/application.yml` | `app.jwt.secret` | SECURITY WARNING: Secret is hard-coded. Giá trị không được chép vào tài liệu này. |
| Cùng file + `JwtService.java` | JWT property namespaces | `POSTIVA_JWT_SECRET`/expiration map vào `postiva.security.*`, nhưng `JwtService` đọc `app.jwt.*`; env variables không có hiệu lực cho service hiện tại. |
| `backend/.../social/service/SocialPostService.java` | `create` | Orchestration nhiều DB write + nhiều target nhưng không `@Transactional`. |
| `backend/.../scheduler/service/PublishingService.java` | `publishDuePosts` | Query rồi đổi status không có DB locking/claim nguyên tử. |
| `database/schema.sql` | init script | Drop toàn bộ bảng trước create; phù hợp fresh init nhưng không phải migration an toàn. |

Các `return null` trong helper như `FileService.attachToPost`, `PostService.titleFrom`, `AppDataController.resolveTargetSchedule/deletePublishedFacebookPost` có ý nghĩa “không có dữ liệu/lỗi” rõ và không tự thân là placeholder. `UnsupportedOperationException` chỉ xuất hiện trong dynamic proxy của test.

## 20. Technical Risks

| Mức | Risk | Bằng chứng/tác động |
|---|---|---|
| Critical | JWT signing secret hard-code và env override sai namespace | `application.yml` có secret mặc định; `JwtService` đọc `app.jwt.*`, không đọc `postiva.security.*`. Tất cả deployment dùng cùng secret nếu không override trực tiếp property đúng. |
| High | OAuth Instagram/Threads không hoàn tất | Callback bị JWT protection trong `SecurityConfig`. |
| High | Meta deauthorize/data deletion không thể được provider gọi | Endpoints đều bị JWT protection dù provider chỉ gửi signed request. |
| High | Scheduler có nguy cơ publish trùng khi nhiều instance | Không có pessimistic lock, `SKIP LOCKED`, atomic claim hoặc distributed lock; nhiều worker có thể cùng đọc `SCHEDULED`. |
| High | Multi-target post không atomic | `SocialPostService.create` không transactional; target trước có thể publish, target sau lỗi, DB/media có thể chỉ hoàn tất một phần. External API vốn không rollback được. |
| High | Media endpoint public cho mọi UUID | `SecurityConfig` permit GET `/api/files/**`; `FileService.requireFile` không ownership check. Điều này phục vụ Meta fetch nhưng mọi người biết UUID đều đọc được file. |
| Medium | OAuth state chỉ ở memory | Restart/multi-instance làm mất hoặc không chia sẻ state; `MetaOAuthStateService` dùng `ConcurrentHashMap`. |
| Medium | OAuth state gắn user nhưng callback public | Thiết kế state one-time bảo vệ CSRF tốt, nhưng cần shared/persistent storage khi scale. |
| Medium | Không automatic retry/backoff outbound publish | Failure chuyển thẳng `FAILED`; retry chỉ manual. Instagram/Threads chỉ retry polling container readiness, không retry toàn request. |
| Medium | Facebook token không refresh | `refreshAccessTokenIfNeeded` chỉ xử lý Instagram Login và Threads. |
| Medium | Reporting gây hiểu nhầm | UI hiển thị engagement/reaction/follower fields trong khi backend hard-code 0. |
| Medium | Database lifecycle drift | SQL init có destructive DROP, đồng thời Hibernate `ddl-auto:update`; entity deletion-request không có trong SQL, AI log chỉ có trong SQL. |
| Medium | Docker DB-name mismatch | Compose tạo `mydatabase`; backend default URL là `postiva`. Phụ thuộc `.env` để nối đúng. |
| Medium | CI không kiểm thử | Jenkins/Docker backend skip tests; pipeline không có Maven test, frontend test hoặc lint gate. |
| Medium | Secrets được truyền qua query/form tới Graph APIs | Clients thường dùng `access_token` query/form theo API pattern; proxy/access logging phải được cấu hình tránh ghi token. Source không log token trực tiếp. |
| Low | JWT nằm trong localStorage | `frontend/src/lib/auth.ts`; XSS có thể đọc token. Không có refresh token/revocation. |
| Low | Uploaded orphan files | Upload tạo file/row trước khi attach; bỏ thao tác không có cleanup job. Copy attachment dùng chung `publicId`, lifecycle delete chưa được quản lý. |
| Low | Exception semantics không đồng nhất | Một số service ném `IllegalArgumentException`, một số `ResponseStatusException`; `GlobalExceptionHandler` map thành response khác nhau. |

Điểm tích cực: social access token không lưu plaintext; `TokenCipher` dùng AES-GCM với random IV. Password và OTP được BCrypt hash. Không phát hiện API key/Meta secret thực trong tracked source hoặc `.env.example`.

## 21. Environment Variables

| Variable | Dùng cho | Required/ghi chú |
|---|---|---|
| `POSTIVA_DB_URL` | JDBC URL | Required khi container DB không trùng default |
| `POSTIVA_DB_USERNAME` | PostgreSQL username | Compose cũng dùng |
| `POSTIVA_DB_PASSWORD` | PostgreSQL password | Không nên dùng default production |
| `OPENAI_API_KEY` | OpenAI Bearer | Required cho AI generation |
| `OPENAI_MODEL` | Model | Default `gpt-4o-mini` |
| `POSTIVA_FRONTEND_ORIGIN` | CORS + OAuth redirect về frontend | Một origin duy nhất |
| `META_APP_ID`, `META_APP_SECRET`, `META_API_VERSION`, `META_REDIRECT_URI` | Facebook OAuth/Graph/signed request | Required cho Facebook OAuth/callbacks |
| `INSTAGRAM_APP_ID`, `INSTAGRAM_APP_SECRET`, `INSTAGRAM_API_VERSION`, `INSTAGRAM_REDIRECT_URI` | Instagram | Required cho OAuth/callbacks |
| `THREADS_APP_ID`, `THREADS_APP_SECRET`, `THREADS_REDIRECT_URI` | Threads | Required cho OAuth/callbacks |
| `POSTIVA_TOKEN_ENCRYPTION_KEY` | AES-GCM derivation input | Ít nhất 16 ký tự theo code; required để lưu/đọc social token |
| `POSTIVA_UPLOAD_DIR` | Local media directory | Default `uploads/images` |
| `POSTIVA_PUBLIC_BASE_URL` | Public media base for IG/Threads | Phải là URL public, không localhost |
| `POSTIVA_PUBLISH_DELAY_MS` | Scheduler fixed delay | Default 10000 ms |
| `POSTIVA_MAIL_ENABLED` | Enable SMTP | Register luôn cần OTP send thành công; false làm register trả 502 qua service logic |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP | Username/password required khi mail enabled |
| `VITE_API_BASE_URL` | Frontend Axios base | Normalize để kết thúc bằng `/api` |
| `VITE_WS_URL` | STOMP broker URL | Backend hiện thiếu server |
| `VITE_WS_TRANSPORT`, `VITE_SOCKJS_URL` | Hook SockJS tùy chọn | Có trong code nhưng thiếu `.env.example` và backend |
| `POSTIVA_JWT_SECRET`, `POSTIVA_JWT_EXPIRATION_SECONDS` | Intended JWT config | **Hiện không được `JwtService` dùng** do property mismatch |

## 22. Important Files

- `backend/src/main/resources/application.yml`: toàn bộ runtime config; chứa JWT configuration defect.
- `backend/src/main/java/com/postiva/config/SecurityConfig.java`: endpoint public/protected; nguyên nhân OAuth/data-deletion callback hỏng.
- `backend/src/main/java/com/postiva/auth/service/AuthService.java`: OTP/register/login.
- `backend/src/main/java/com/postiva/post/service/PostService.java`: AI brief/draft persistence.
- `backend/src/main/java/com/postiva/ai/service/OpenAiService.java`: external AI request/strict schema/error mapping.
- `backend/src/main/java/com/postiva/social/service/SocialPostService.java`: entry orchestration chính của create/publish.
- `backend/src/main/java/com/postiva/scheduler/service/PublishingService.java`: queue state machine và publish result.
- `backend/src/main/java/com/postiva/scheduler/PublishingScheduler.java`: background polling.
- `backend/src/main/java/com/postiva/social/service/SocialAccountService.java`: token/account lifecycle.
- `backend/src/main/java/com/postiva/social/security/TokenCipher.java`: token encryption.
- `backend/src/main/java/com/postiva/social/client/*GraphClient.java`: outbound Meta APIs.
- `backend/src/main/java/com/postiva/social/publishing/*Publisher.java`: rules/media behavior từng platform.
- `backend/src/main/java/com/postiva/appdata/controller/AppDataController.java`: frontend read model, edit/delete và các metrics stub.
- `frontend/src/pages/CreatePostPage.tsx`: frontend product flow chính.
- `frontend/src/pages/ConnectionsPage.tsx`: OAuth/manual/account management.
- `frontend/src/pages/SchedulePage.tsx`, `ReportsPage.tsx`: lịch và báo cáo.
- `frontend/src/lib/api.ts`, `auth.ts`: API/JWT client behavior.
- `database/schema.sql`: full bootstrap schema, không phải migration.
- `docker-compose.yml`, Dockerfiles, `frontend/nginx.conf`, `Jenkinsfile`: deployment.

## 23. Current Development State

Project đã vượt giai đoạn scaffold: authentication, AI generation, local media, Facebook/Instagram/Threads publisher, scheduled worker và Facebook sync đều có implementation thật. Luồng demo mạnh nhất hiện tại là Facebook OAuth → tạo/AI content → publish hoặc schedule → xem/sửa/xóa/sync Facebook.

Các khoảng trống làm hệ thống chưa production-ready là security routing cho Instagram/Threads/provider callbacks, JWT secret configuration, scheduler concurrency/idempotency, multi-target partial failure và các màn hình engagement/comments/realtime đang hiển thị contract chưa có backend.

Verification thực hiện trong lần phân tích:

- `npm.cmd run build`: **PASS** — TypeScript và Vite production build thành công.
- `mvn test`: **NOT EXECUTED/UNKNOWN** — Maven cần ghi vào local repository ngoài workspace và bị access denied trước khi resolve parent POM. Không có test source nào được báo fail.
- Git worktree ban đầu sạch; chỉ file tài liệu này được tạo. Không sửa source code.
