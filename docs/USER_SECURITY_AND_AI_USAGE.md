# Bước 1: bảo vệ login/OTP và quản lý lượt AI

Cập nhật 17/10/2026. Tài liệu mô tả phần đã triển khai trong mã nguồn; không xác nhận đã phát hành production hoặc mở bán Pro. Chính sách quota, chi phí và công tắc vận hành mới nhất ở [AI_PLANS_AND_COST_CONTROL.md](AI_PLANS_AND_COST_CONTROL.md).

## Login và OTP

Các giới hạn lưu trong PostgreSQL, dùng cập nhật nguyên tử. Restart hoặc chạy nhiều backend cùng database không làm mất bộ đếm. Khóa bucket lưu hash của email/IP; không lưu mật khẩu, OTP hoặc token trong bộ đếm.

| Thao tác | Theo tài khoản/email | Theo IP |
| --- | --- | --- |
| Login | Mặc định 10 yêu cầu/15 phút, kể cả yêu cầu thành công; cấu hình được | 60 yêu cầu/15 phút |
| Đăng ký/gửi lại OTP | Tối thiểu 60 giây giữa hai yêu cầu; tối đa 5 yêu cầu/giờ | 20 yêu cầu/giờ |
| Xác thực OTP | Tối đa 10 yêu cầu/10 phút; mỗi mã khóa sau 5 lần sai | 60 yêu cầu/10 phút |

- Tất cả địa chỉ email được trim/lowercase trước khi tạo khóa giới hạn.
- Giới hạn IP áp dụng trước khi đọc/validate body. Admin vẫn giữ lớp chống thử sai MFA riêng, cộng với giới hạn login chung.
- OTP có hạn 10 phút và chỉ lưu hash. Sai lần thứ 5 khóa mã đó; cần gửi mã mới. Mã đúng sau khi mã đã khóa vẫn bị từ chối. Không mở lại mã chỉ vì hết thời gian chờ HTTP.
- Tạo/gửi lại/xác thực đăng ký cùng email được tuần tự hóa trong DB; xác thực đồng thời một OTP chỉ tạo một tài khoản.
- Các lỗi xác thực vẫn commit bộ đếm thử sai; lỗi DB thực sự vẫn rollback.
- Email gửi sau khi OTP được lưu và commit, ngoài transaction. Nếu SMTP lỗi, API trả 502; dữ liệu chờ và giới hạn gửi lại vẫn được giữ. Yêu cầu gửi lại sau cooldown để nhận mã mới.
- Đăng ký email đã có tài khoản trả cùng dạng 202 như yêu cầu mới, không trả thông báo xác nhận email tồn tại. Đây là giảm lộ thông tin qua phản hồi; không phải cam kết chống mọi phân tích thời gian phản hồi.
- HTTP 429 có `Retry-After` (giây); frontend hiển thị thời gian chờ và nút gửi lại mã. Có giới hạn độ dài email và giới hạn 72 byte UTF-8 của mật khẩu đăng ký BCrypt.

### Reverse proxy

Mặc định lấy IP kết nối trực tiếp, không tin `X-Forwarded-For` hoặc `X-Real-IP` từ client. Chỉ đọc `X-Real-IP` khi peer thuộc `POSTIVA_TRUSTED_PROXY_ADDRESSES` đã cấu hình. Nginx trong repo ghi đè header này bằng `$remote_addr`.

Khi chạy sau Nginx, cấu hình đúng IP/CIDR của proxy được kiểm soát, giữ backend trong mạng phù hợp. Nếu để trống, các khách hàng qua cùng proxy dùng chung bucket IP. Không đặt `0.0.0.0/0` hoặc `::/0`; không bật ForwardedHeaderFilter/remote-IP rewriting tùy ý vì có thể làm mất địa chỉ peer dùng để xác minh proxy. Cấu hình hiện dùng `server.forward-headers-strategy: none`.

## Free/Pro và kỳ sử dụng

Role `USER/ADMIN` độc lập với `FREE/PRO`. `subscription_periods` lưu thời điểm bắt đầu/kết thúc UTC, quota của kỳ, số đã dùng và số đang giữ chỗ. Quota là bản chụp theo kỳ, không tự giảm khi thay cấu hình.

- Mặc định hiện tại: Free dùng thử 30 lượt/30 ngày; Pro 50 lượt/30 ngày. Đây là quota AI, chưa phải giá bán. `VerifiedSubscriptionPlanService` là provisioning nội bộ chỉ dành cho sự kiện thanh toán đã xác minh; chưa có checkout/webhook hay API tự mua Pro. Migration V11 nâng quota các kỳ Free còn hiệu lực, giữ nguyên số lượt đã dùng.
- Tài khoản mới có kỳ Free khi xác thực thành công. Tài khoản cũ chưa có kỳ bắt đầu Free khi lần đầu truy cập phần sử dụng/tạo AI; migration không sửa user hoặc tự cấp Pro.
- Có kỳ Pro hợp lệ thì dùng quota đã ghi cho kỳ đó. Hết Pro và không có kỳ tiếp theo thì về Free. Mua/gia hạn Pro vẫn thuộc bước tích hợp thanh toán.
- Kỳ Free kéo dài đúng 30 ngày liên tiếp. Bỏ qua kỳ đã trôi qua khi lâu không dùng; không cộng dồn quota. Thời điểm hết hạn là `now >= ends_at`.
- Hết lượt AI vẫn được chỉnh sửa, đăng và lên lịch bài của mình. Tài khoản LOCKED vẫn bị chặn nghiệp vụ.

## Tạo nội dung AI và lịch sử

`POST /api/posts/generate` bắt buộc header `Idempotency-Key` là UUID. Frontend tạo key cho mỗi yêu cầu mới, giữ key khi gửi lại sau lỗi mạng; gửi lại cùng key/nội dung không gọi OpenAI lần thứ hai. Các client cũ phải được cập nhật cùng backend.

1. Validate input và quyền sở hữu media trước khi gọi AI.
2. Khóa bản ghi USER trong transaction ngắn, kiểm tra kỳ/quota/giới hạn, giữ một lượt và lưu yêu cầu `RESERVED`.
3. Commit trước khi gọi OpenAI, không giữ transaction/row lock trong lúc chờ nhà cung cấp.
4. Lưu bài và chuyển một lượt từ reserved sang used trong cùng transaction.
5. Lỗi trả lại quota người dùng nhưng vẫn giữ lịch sử và số lần yêu cầu trong hạn mức ngày, vì nhà cung cấp có thể đã phát sinh chi phí.

| Trạng thái | Nghĩa | Quota |
| --- | --- | --- |
| RESERVED | Đang chờ xử lý | Giữ 1 lượt |
| SUCCEEDED | Đã lưu bài thành công | Tính 1 lượt |
| FAILED | Yêu cầu thất bại rõ ràng | Không tính lượt |
| UNKNOWN | Gián đoạn/timeout hoặc chưa xác định đầy đủ kết quả | Hoàn lượt người dùng; giữ số lần gọi để kiểm soát chi phí |

Chỉ một yêu cầu đang chạy trên mỗi user. Key cũ đang chạy trả 409; key đã FAILED/UNKNOWN cũng không chạy lại tự động. Muốn tạo lại sau lỗi cần yêu cầu mới. Response 409 có `X-AI-Request-State` khi thích hợp để frontend phân biệt. Key dùng lại với nội dung khác bị từ chối. Bài đã xóa không được tạo lại ngầm bằng key cũ.

Yêu cầu RESERVED quá 5 phút chuyển UNKNOWN và hoàn giữ chỗ khi người dùng xem quota hoặc tạo yêu cầu tiếp theo. Việc này tuần tự hóa theo USER; hoàn một lần. Không tự gọi lại nhà cung cấp. Nếu việc hoàn gặp lỗi DB, reservation được giữ để xử lý lần tiếp theo, không nới quota âm thầm.

Mặc định thêm 20 lần yêu cầu/người/ngày và 100 lần/toàn hệ thống/ngày (UTC). Bao gồm yêu cầu đã giữ chỗ nhưng bị lỗi/gián đoạn; việc refund không reset trần này. Đây là **giới hạn số yêu cầu**, không phải sổ chi phí bằng tiền hoặc số token thanh toán thực của OpenAI. Độ dài input và output AI cũng có giới hạn.

| API | Quyền và dữ liệu |
| --- | --- |
| `GET /api/usage` | USER đang hoạt động; gói, kỳ, quota đã dùng/giữ chỗ/còn lại của chính mình |
| `GET /api/usage/requests?page=0&size=20` | Lịch sử của chính mình; tối đa 50 dòng/trang, không trả prompt hoặc secret |
| `POST /api/posts/generate` | USER đang hoạt động, input hợp lệ, có Idempotency-Key và quota |

Trang tạo bài hiển thị quota, ngày hết kỳ và lịch sử có phân trang. Nginx chờ upstream tối đa 135 giây giữa các lần đọc, phù hợp timeout OpenAI 90 giây và frontend 120 giây hiện tại.

## Cấu hình

| Biến | Mặc định | Ghi chú |
| --- | --- | --- |
| POSTIVA_FREE_AI_LIMIT | 5 | Áp dụng khi tạo kỳ Free mới |
| POSTIVA_PRO_AI_LIMIT | 300 | Lượt AI mỗi kỳ 30 ngày của gói Pro (mua qua payOS hoặc admin cấp) |
| POSTIVA_AI_USER_DAILY_ATTEMPTS | 20 | Trần số lần yêu cầu mỗi ngày UTC |
| POSTIVA_AI_GLOBAL_DAILY_ATTEMPTS | 100 | Đặt 0 để tạm dừng yêu cầu AI mới |
| POSTIVA_LOGIN_ATTEMPTS_PER_15_MINUTES | 10 | Tổng số lần gửi login theo email trong cửa sổ |
| POSTIVA_TRUSTED_PROXY_ADDRESSES | Trống | Danh sách IP/CIDR proxy được tin, phân cách dấu phẩy |

Không chỉnh SQL quota đang dùng để giả lập giao dịch thành công. Cần kiểm tra quyền lợi khách hàng hiện hữu trước khi áp dụng giới hạn trên production và chốt chính sách gói trước khi mở bán.

## Migration và kiểm tra

V5 thêm rate limit/usage, V8 thêm bảng chi phí AI, ngân sách, công tắc vận hành và audit; V9 lưu token ghi cache. Không sửa migration V1–V8, không reset schema và không đổi dữ liệu user/bài cũ. Thực hiện quy trình backup → restore thử → migrate → đối chiếu trong [hướng dẫn database](../database/README.md) trước khi nâng cấp database đang vận hành.

Bộ kiểm tra mới dùng PostgreSQL riêng và HTTP thật, mock SMTP/OpenAI để không gửi email hoặc tiêu tiền: giới hạn account/IP, header IP giả, OTP hết hạn/thử sai/verify đồng thời, race giữ quota, idempotency, lỗi nhà cung cấp, lỗi ghi DB, hoàn quota, kỳ hết hạn và cô lập lịch sử giữa hai user.

Kết quả kiểm tra workspace ngày 17/10/2026:

- Backend: **114 test, 0 lỗi, 29 bỏ qua** bằng JDK 21; các test PostgreSQL thật cần `POSTIVA_TEST_DB_URL` nên chưa chạy trong lượt kiểm tra này.
- Frontend: TypeScript, lint và production build thành công; lint còn hai cảnh báo Fast Refresh, bundle hiện vượt ngưỡng cảnh báo 500 kB.
- Local database từng được nâng từ V4 lên V5 sau backup/restore thử; chưa xác minh hoặc áp dụng V8/V9 lên database đang vận hành.
- Công cụ kiểm tra UI không có trình duyệt kết nối; chưa xác minh trực quan hoặc chạy end-to-end trên trình duyệt. SMTP/OpenAI thật, cấu hình proxy production và quyền lợi thương mại vẫn cần kiểm chứng trước khi phát hành.

Chưa bao gồm: quên/đặt lại mật khẩu USER, OAuth Meta, scheduler chống trùng, thanh toán, số liệu tương tác thực, CI gate/vận hành production hoặc kiểm thử trình duyệt end-to-end.

Tham khảo thiết kế: [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html), [PostgreSQL INSERT/ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html).
