# Postiva EXE201

Postiva là source code mẫu cho website trợ lý marketing dành cho người mới bán hàng trên Facebook và Instagram. Dự án dùng React cho frontend, Spring Boot cho backend, PostgreSQL cho database và OpenAI API để tạo nội dung.

## Kiến trúc

Backend dùng mẫu Modular Monolith kết hợp Layered Architecture. Toàn bộ backend là một ứng dụng Spring Boot, nhưng code được chia theo module nghiệp vụ:

```txt
backend
└── src/main/java/com/postiva
    ├── ai
    ├── calendar
    ├── common
    ├── config
    ├── file
    ├── post
    ├── scoring
    ├── social
    └── template
```

Frontend dùng React + Vite:

```txt
frontend
└── src
    ├── api
    ├── components
    ├── pages
    └── styles.css
```

## 5 chức năng được scaffold

| Chức năng | Module chính |
|---|---|
| Tạo bài từ thông tin sản phẩm | `post`, `ai`, `template` |
| Chấm điểm bài viết | `scoring`, `post` |
| Template theo ngành | `template` |
| Lịch nội dung 7 ngày | `calendar`, `post`, `ai` |
| Kết nối Facebook/Instagram/Threads | `social` |

## Database

Schema PostgreSQL nằm ở:

```txt
database/schema.sql
```

Các bảng chính:

```txt
users
business_profiles
industry_templates
product_briefs
generated_posts
post_scores
content_calendars
calendar_items
uploaded_files
social_accounts
scheduled_posts
publish_logs
ai_generation_logs
```

## Chạy backend

Tạo database PostgreSQL, ví dụ:

```sql
CREATE DATABASE postiva;
```

Set biến môi trường:

```txt
POSTIVA_DB_URL=jdbc:postgresql://localhost:5432/postiva
POSTIVA_DB_USERNAME=postgres
POSTIVA_DB_PASSWORD=your_password
OPENAI_API_KEY=your_openai_api_key
OPENAI_MODEL=gpt-4o-mini
```

Chạy backend:

```bash
cd backend
mvn spring-boot:run
```

Backend tự đọc file `backend/.env` khi được chạy với working directory là `backend`.
Nếu chưa có `OPENAI_API_KEY`, API tạo bài sẽ trả lỗi cấu hình rõ ràng; hệ thống không sinh nội dung mẫu giả.

## Chạy frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend mặc định gọi backend tại:

```txt
http://localhost:8080/api
```

Có thể đổi bằng biến:

```txt
VITE_API_BASE_URL=http://localhost:8080/api
```

## Chạy PostgreSQL bằng Docker

Nếu máy có Docker, chạy:

```bash
docker compose up -d
```

Database mặc định:

```txt
host: localhost
port: 5432
database: postiva
username: postgres
password: postgres
```

## API mẫu

Tạo bài:

```http
POST /api/posts/generate
GET  /api/posts/{postId}
PUT  /api/posts/{postId}
```

Luồng tạo bài lưu product brief, gọi OpenAI với Structured Outputs (`json_schema`), lưu bản nháp vào
`generated_posts` và trả nội dung riêng cho Facebook, Instagram và Threads để người dùng chỉnh sửa.

Chấm điểm:

```http
POST /api/posts/{postId}/score
```

Tạo lịch 7 ngày:

```http
POST /api/calendars/generate
```

Template ngành:

```http
GET /api/templates
```

Kết nối Meta:

```http
GET /api/social/meta/connect-url
```

Module Meta hiện là skeleton để mô tả flow OAuth. Khi làm thật, nhóm cần tạo Meta Developer App, cấu hình OAuth redirect URI, xin quyền Page/Instagram và lưu token đã mã hóa.

## Phần bổ sung từ demoExe

Source đã được bổ sung có chọn lọc, dùng lại database và kiến trúc module của Postiva:

- Đăng ký, đăng nhập và JWT bằng Spring Security.
- Upload ảnh JPG/PNG/WebP tối đa 10 MB; ảnh chỉ được gắn vào bài, không gửi cho AI phân tích.
- Meta OAuth có kiểm tra `state`, đổi authorization code và lưu Facebook Page.
- Page access token được mã hóa AES-GCM trước khi lưu.
- Đăng Facebook dạng text hoặc một ảnh.
- Đăng ngay và đặt lịch bằng `scheduled_posts`, `publish_logs` và Spring Scheduler.
- React tự gắn JWT, có màn hình đăng nhập, upload ảnh, kết nối Page và lịch đăng.

Các biến môi trường bổ sung nằm trong `backend/.env.example`. Khi chưa có Meta App, backend vẫn chạy và bốn chức năng nội bộ vẫn dùng được; chức năng kết nối/đăng Facebook cần `META_APP_ID`, `META_APP_SECRET` và `POSTIVA_TOKEN_ENCRYPTION_KEY`.

API bổ sung:

```text
POST   /api/auth/register
POST   /api/auth/login
GET    /api/auth/me
POST   /api/files/upload
GET    /api/files/{fileId}
PUT    /api/posts/{postId}
GET    /api/social/meta/connect-url
GET    /api/social/meta/callback
GET    /api/social/meta/accounts
POST   /api/social/meta/facebook/manual
DELETE /api/social/meta/accounts/{accountId}
POST   /api/publishing/now
POST   /api/publishing/schedule
GET    /api/publishing
POST   /api/publishing/{scheduledPostId}/retry
```

`POST /api/social/meta/facebook/manual` chỉ dành cho developer/tester. Giao diện người dùng dùng OAuth và không yêu cầu khách hàng dán Page Access Token.
