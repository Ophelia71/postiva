# MFA cho tài khoản Admin

MFA bằng TOTP đã được triển khai. ADMIN phải hoàn tất thiết lập để dùng API quản trị người dùng. Tài khoản chưa thiết lập chỉ được vào trang bảo mật và xem phiên hiện tại. USER tiếp tục dùng luồng đăng nhập hiện có.

## Cấu hình máy chủ

Backend đọc `POSTIVA_MFA_ENCRYPTION_KEY`: một bí mật ngẫu nhiên ổn định, ít nhất 32 byte UTF-8. Lưu trong secret manager hoặc cấu hình runtime riêng của từng môi trường; tất cả instance của cùng môi trường dùng cùng khóa. Khóa này độc lập với khóa ký JWT và khóa mã hóa token mạng xã hội. Không commit hoặc đưa khóa vào log.

Khóa TOTP trong database được mã hóa AES-GCM với nonce ngẫu nhiên và gắn với ID admin. Sao lưu khóa mã hóa bằng quy trình quản lý bí mật của đơn vị vận hành. Thay đổi/mất khóa làm các khóa TOTP đã lưu không giải mã được; không đổi khóa tùy tiện trong lúc tài khoản đang dùng MFA.

`POSTIVA_MFA_ISSUER` (mặc định `Postiva`) là tên mục hiện trong ứng dụng xác thực. Mỗi môi trường nên dùng tên riêng, ví dụ `Postiva Local` khi chạy trên máy, để không lấy nhầm mã của môi trường khác. Đổi tên chỉ ảnh hưởng lần thiết lập sau; mục đã thêm vẫn dùng được.

Nếu thiếu khóa, các API nghiệp vụ USER vẫn hoạt động; trang bảo mật báo chưa cấu hình và không cho bắt đầu thiết lập MFA. Backend trả 503 khi không xử lý được dữ liệu mã hóa.

## Thiết lập lần đầu

1. Khởi động lại backend sau khi có migration V4 và khóa mã hóa. Đăng nhập admin bằng mật khẩu hiện tại.
2. Mở `/app/admin/security`. Nhập mật khẩu hiện tại rồi chọn **Bắt đầu thiết lập MFA**.
3. Trong ứng dụng xác thực hỗ trợ TOTP, thêm tài khoản bằng khóa thủ công. Dùng khóa hiển thị trên trang, loại theo thời gian; thuật toán SHA-1, 6 số, chu kỳ 30 giây. Khóa thiết lập hết hạn sau 10 phút.
4. Nhập mã 6 số để xác nhận. MFA chỉ bật sau khi mã hợp lệ; các JWT đã cấp mất hiệu lực.
5. Lưu 10 mã khôi phục hiển thị một lần, xác nhận đã lưu rồi đăng nhập lại. Lần đăng nhập tiếp theo dùng mã TOTP mới hoặc một mã khôi phục chưa dùng. Mã vừa xác nhận thiết lập không được dùng lại.

Khóa và mã khôi phục chỉ nằm trong bộ nhớ giao diện, không lưu vào localStorage/sessionStorage. Trang hỗ trợ tải mã khôi phục khi người dùng chủ động chọn; file đó chứa bí mật và cần giữ trong nơi lưu trữ riêng an toàn. Mã QR không gửi qua dịch vụ bên ngoài; giao diện hiện dùng cách nhập khóa thủ công.

## Đăng nhập và khôi phục

`POST /api/auth/login` tiếp tục nhận email/password, bổ sung `mfaCode` khi ADMIN đã bật MFA. Mật khẩu đúng nhưng thiếu mã trả 428, không cấp JWT; giao diện chuyển sang bước nhập mã. Mã sai/đã dùng trả 401. Sai 5 lần liên tiếp thì đăng nhập admin bị khóa 5 phút: trả 429 kèm `Retry-After` và thông báo rõ là đang bị khóa, để người dùng không tưởng mã đúng là sai. USER không cần mã MFA.

TOTP chấp nhận lệch tối đa một chu kỳ. Database lưu chu kỳ đã dùng; cùng một mã không được xác thực lại trong chu kỳ đó. Bật thời gian tự động trên máy chủ và điện thoại.

Mã khôi phục có 128 bit ngẫu nhiên, lưu hash SHA-256, mỗi mã dùng một lần. Phải có cả mật khẩu và mã. Khi dùng mã khôi phục, backend thu hồi các phiên khác. Khóa hàng người dùng bảo đảm hai yêu cầu đồng thời dùng cùng mã chỉ có một yêu cầu thành công.

Nhập sai mật khẩu ADMIN hoặc mã MFA đủ 5 lần bị chặn 5 phút. Bộ đếm/thời hạn chặn lưu trong PostgreSQL và được commit cả khi xác thực thất bại; khởi động lại backend không làm mất giới hạn. Giới hạn này tính theo tài khoản, không dựa vào IP do trình duyệt gửi. Reverse proxy production vẫn cần giới hạn lưu lượng chung cho endpoint đăng nhập.

Nếu mất điện thoại, đăng nhập bằng một mã khôi phục, sau đó vào trang bảo mật. Tạo lại mã khôi phục hoặc tắt MFA cần mật khẩu và một mã TOTP/mã khôi phục khác chưa dùng. Tạo lại mã sẽ hủy toàn bộ bộ mã cũ. Tắt MFA thu hồi phiên và đưa tài khoản về trạng thái phải thiết lập lại trước khi quản trị.

Nếu mất cả ứng dụng và tất cả mã khôi phục, không có API công khai bỏ qua MFA. Người vận hành cần xác minh chủ tài khoản bằng quy trình nội bộ, sao lưu rồi thực hiện phục hồi có kiểm soát/audit; bootstrap không reset MFA hoặc mật khẩu của admin hiện hữu.

## API quản lý MFA

Tất cả endpoint sau yêu cầu JWT của ADMIN đang ACTIVE và trả `Cache-Control: no-store`. Các phản hồi có nội dung dùng envelope `ApiResponse` hiện có.

| Phương thức | Endpoint | Body/kết quả |
| --- | --- | --- |
| GET | `/api/admin/security/mfa` | `enabled`, `configured`, `recoveryCodesRemaining`; không trả khóa hoặc hash. |
| POST | `/api/admin/security/mfa/setup` | `currentPassword` → `secret`, `otpauthUri`, `expiresAt`. |
| POST | `/api/admin/security/mfa/confirm` | `code` → `recoveryCodes` hiển thị một lần. |
| POST | `/api/admin/security/mfa/recovery-codes` | `currentPassword`, `code` → bộ `recoveryCodes` mới. |
| POST | `/api/admin/security/mfa/disable` | `currentPassword`, `code` → 204. |

JWT có chứng nhận MFA chỉ được cấp sau khi kiểm tra đủ mật khẩu và yếu tố thứ hai. Backend kiểm tra cả phiên bản xác thực, trạng thái MFA hiện tại trong database và chứng nhận trong JWT trước khi cấp authority `MFA_VERIFIED`. Mọi API quản trị người dùng yêu cầu authority này. Sửa role/flag trong localStorage không cấp quyền trên backend.

Các sự kiện `MFA_ENABLED`, `MFA_DISABLED`, `MFA_RECOVERY_USED`, `MFA_RECOVERY_REGENERATED`, `MFA_FAILED` ghi vào `admin_security_events` bất biến. Không ghi mã, khóa, mật khẩu hoặc hash bí mật trong audit.

## Migration và kiểm tra

V4 bổ sung `users.mfa_enabled=false`, bảng `admin_mfa` và loại sự kiện bảo mật. Giữ nguyên dữ liệu tài khoản, mật khẩu và `auth_version` đang có. Admin hiện hữu phải tự xác nhận bằng ứng dụng; migration không tạo khóa TOTP hoặc mã khôi phục cho họ. Áp dụng theo [hướng dẫn database](../database/README.md).

57 bài kiểm tra backend đã đạt, gồm vector chuẩn RFC 6238, mã hóa gắn ID, replay TOTP, mã khôi phục dùng một lần và đồng thời, setup hết hạn, đăng nhập hai bước, quyền USER/ADMIN, thu hồi JWT, tái tạo/tắt MFA và giới hạn nhập sai lưu trong database thật. Build TypeScript/Vite và lint đã chạy; lint còn hai cảnh báo Fast Refresh có từ trước ở file khác. Phiên này không khởi tạo được runtime trình duyệt automation, nên chưa kiểm tra trực quan bằng browser.

TOTP không chống phishing như WebAuthn/passkey. Căn cứ kỹ thuật: [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238), [OWASP MFA Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html).
