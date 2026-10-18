# Bảo mật mật khẩu người dùng

## Luồng sử dụng

- Login giới hạn mặc định 10 lần/15 phút theo email và 60 lần/15 phút theo IP (`POSTIVA_LOGIN_ATTEMPTS_PER_15_MINUTES` đổi giới hạn theo email). Gửi/resend OTP đăng ký giới hạn 1 lần/phút và 5 lần/giờ theo email, 20 lần/giờ theo IP. Xác minh OTP giới hạn 10 lần/10 phút theo email và 60 lần/10 phút theo IP; mỗi mã bị khóa sau 5 lần nhập sai.
- Hệ thống dùng thông báo chung cho login sai, OTP sai/hết hạn/bị khóa và đăng ký email mới/đã tồn tại để tránh lộ trạng thái tài khoản.
- Đăng ký luôn trả cùng phản hồi cho email mới và email đã có tài khoản. Email OTP được gửi bất đồng bộ; lỗi gửi không làm endpoint tiết lộ trạng thái tài khoản và mã đang chờ bị vô hiệu hóa nếu gửi thất bại.
- Tài khoản người dùng chỉ khôi phục qua email đã xác thực trong lúc đăng ký.
- `POST /api/auth/password/forgot` luôn trả cùng thông báo dù email có tài khoản hay không. Email chứa liên kết một lần, hết hạn sau 30 phút.
- `POST /api/auth/password/reset` đặt mật khẩu mới. Mật khẩu mới tối thiểu 8 ký tự và tối đa 72 byte UTF-8.
- Người dùng đang đăng nhập có thể đổi mật khẩu tại **Bảo mật tài khoản**. Thao tác này yêu cầu mật khẩu hiện tại.
- **Thu hồi tất cả phiên** cũng yêu cầu mật khẩu hiện tại. Đổi/đặt lại mật khẩu và thu hồi phiên đều tăng `auth_version`, khiến JWT đã cấp không còn hợp lệ.
- Tài khoản ADMIN tiếp tục dùng màn hình bảo mật quản trị hiện có; endpoint khôi phục mật khẩu người dùng không cấp liên kết cho ADMIN.

## Vận hành

- Flyway V7 tạo `password_reset_tokens`; không chạy lại `database/schema.sql` trên cơ sở dữ liệu đang có.
- Cấu hình `POSTIVA_MAIL_ENABLED=true`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` và `POSTIVA_FRONTEND_ORIGIN` cho môi trường phát hành. Nếu gửi email thất bại, token tương ứng bị vô hiệu hóa.
- Token thô chỉ đi trong email và fragment của liên kết; cơ sở dữ liệu lưu SHA-256. Không đưa token vào log, analytics hoặc công cụ theo dõi URL.
- Giới hạn yêu cầu theo IP/email và dọn các token cũ tự động.
- Mật khẩu và OTP đăng ký lưu bằng BCrypt; mã OTP hết hạn và bản ghi đăng ký chờ được dọn định kỳ. Meta access token và khóa TOTP lưu dạng mã hóa AES-GCM; khóa mã hóa và JWT secret phải được cấp qua secret manager/biến môi trường, không commit. `POSTIVA_JWT_SECRET` cần ít nhất 32 byte UTF-8; thay đổi khóa này sẽ làm các phiên JWT hiện tại mất hiệu lực.
- Không dùng mật khẩu PostgreSQL mặc định. `POSTIVA_DB_PASSWORD` phải được đặt trong cấu hình runtime riêng cho từng môi trường.
