# Quản trị người dùng Postiva

Ngày cập nhật: 16/10/2026. Phạm vi đã triển khai: cấp admin nội bộ, API quản lý USER và giao diện Admin. Gói dịch vụ, quota và thanh toán là các phần tiếp theo.

## Khởi tạo admin nội bộ

Mặc định không tự tạo admin. Sau khi database đã áp dụng Flyway theo [hướng dẫn migration](../database/README.md), cung cấp các biến sau qua môi trường/secret manager của backend:

| Biến | Giá trị |
| --- | --- |
| `POSTIVA_ADMIN_BOOTSTRAP_ENABLED` | `true` cho lần khởi tạo có chủ đích; mặc định `false`. |
| `POSTIVA_ADMIN_BOOTSTRAP_EMAIL` | Email riêng, chưa thuộc tài khoản USER. |
| `POSTIVA_ADMIN_BOOTSTRAP_PASSWORD` | Mật khẩu riêng từ 12 ký tự và tối đa 72 byte UTF-8; không có mật khẩu mặc định. |
| `POSTIVA_ADMIN_BOOTSTRAP_FULL_NAME` | Tên quản trị viên, tối đa 150 ký tự; mặc định `Postiva Administrator`. |

Backend mã hóa mật khẩu bằng PasswordEncoder hiện có và tạo `ADMIN + ACTIVE`. Nếu email đã thuộc USER, khởi tạo bị từ chối, không tự nâng quyền. Nếu đã là ADMIN, giữ nguyên mật khẩu, tên và trạng thái; đây không phải chức năng reset mật khẩu hoặc mở khóa admin. Không có API bootstrap công khai.

Sau khi khởi tạo thành công, đặt enabled về `false`, bỏ password bootstrap khỏi cấu hình runtime và đăng nhập bằng tài khoản đã cấp. Không commit credentials, không đưa password vào lệnh có thể lưu history hoặc log. Việc thêm cấu hình mẫu không tự tạo admin trên database đang vận hành.

Database local `postiva` đã được cấp một tài khoản nội bộ `admin@postiva.local` ngày 16/10/2026. Mật khẩu được tạo ngẫu nhiên và chỉ bàn giao cho người vận hành; không lưu trong tài liệu hoặc `backend/.env`. Môi trường khác cần quy trình cấp tài khoản riêng.

## API

Mọi endpoint dưới `/api/admin/**` yêu cầu JWT của ADMIN đang hoạt động. API quản trị người dùng còn yêu cầu xác thực MFA; admin chưa thiết lập chỉ dùng được trang bảo mật và `/api/auth/me`. Guest nhận 401, USER nhận 403, tài khoản khóa không xác thực được bằng JWT cũ. Các API nghiệp vụ của USER yêu cầu role USER. Service cũng kiểm tra actor, tránh phụ thuộc riêng vào việc ẩn giao diện.

| Phương thức | Endpoint | Chức năng |
| --- | --- | --- |
| GET | `/api/admin/users` | Danh sách USER, không bao gồm ADMIN. |
| GET | `/api/admin/users/{userId}` | Chi tiết USER. |
| PATCH | `/api/admin/users/{userId}/lock` | Khóa tài khoản, bắt buộc lý do. |
| PATCH | `/api/admin/users/{userId}/unlock` | Mở khóa tài khoản, bắt buộc lý do. |
| GET | `/api/admin/users/{userId}/audit` | Nhật ký khóa/mở tài khoản, phân trang. |
| POST | `/api/admin/security/password` | Admin đổi mật khẩu của chính mình; yêu cầu mật khẩu hiện tại, sau đó thu hồi các JWT cũ. |

Danh sách nhận `q` (tên/email, không phân biệt hoa thường, tối đa 255 ký tự), `status` (`ACTIVE` hoặc `LOCKED`), `page` (từ 0) và `size` (1–100, mặc định 20). Từ khóa `%` và `_` được tìm như ký tự thật. Thứ tự cố định: ngày tạo giảm dần, ID giảm dần để phân trang ổn định.

Audit nhận `page`/`size` với cùng giới hạn; bản ghi mới nhất trước. Actor có ID cố định và tên hiển thị lấy từ tài khoản hiện tại, không phải bản chụp tên tại thời điểm thao tác.

Các API trả envelope `ApiResponse`: `success`, `data`, `message`. Trang dữ liệu có `items`, `page`, `size`, `totalElements`, `totalPages`. DTO chỉ chứa các trường cho phép: ID, tên/email, role/status, ngày tạo/cập nhật, metadata khóa; không trả entity, hash mật khẩu hoặc token mạng xã hội.

Body khóa/mở:

```json
{
  "reason": "Lý do xử lý tài khoản"
}
```

Lý do phải có nội dung và tối đa 1.000 ký tự. Chuyển trạng thái thông qua `UserAccountStatusService`: khóa hàng mục tiêu, kiểm tra quyền, cập nhật tài khoản, hủy lịch chưa gửi nếu khóa và ghi audit trong cùng transaction. Không cho khóa/mở ADMIN qua API này. Gửi lại thao tác khi trạng thái đã đúng không đổi lý do gốc và không thêm audit trùng.

ID không tồn tại hoặc ID của ADMIN ở API đọc chi tiết/audit trả 404. Tham số sai kiểu trả 400; dữ liệu vi phạm ràng buộc request trả 422. Thao tác không đủ quyền trả 403.

## Giao diện

- ADMIN đã thiết lập/xác thực MFA được đưa đến `/app/admin/users`; admin chưa thiết lập chuyển đến `/app/admin/security`. USER dùng giao diện nội dung hiện có và không vào được khu vực Admin.
- Danh sách có tìm kiếm, lọc trạng thái, phân trang phía server và trạng thái đang tải/lỗi/rỗng. Bộ lọc được giữ trên URL khi mở chi tiết rồi quay lại.
- `/app/admin/users/{id}` hiển thị thông tin tài khoản, metadata khóa và nhật ký phân trang. Nút làm mới giúp kiểm tra trạng thái thực tế khi kết quả một yêu cầu chưa rõ.
- Hộp xác nhận khóa/mở dùng dialog native, có mô tả tác động và lý do bắt buộc. Khi khóa, lịch chưa gửi bị hủy; mở khóa không tự đăng bù.
- Desktop dùng bảng; màn hình nhỏ dùng danh sách thẻ. Các nút có nhãn, trạng thái không chỉ thể hiện bằng màu và dialog hỗ trợ bàn phím.
- `/app/admin/security` cho phép đổi mật khẩu. Sau khi đổi thành công, giao diện xóa phiên và yêu cầu đăng nhập lại.

## Bảo mật mật khẩu admin

`POST /api/admin/security/password` nhận `currentPassword` và `newPassword`. Mật khẩu mới tối thiểu 12 ký tự, tối đa 72 byte UTF-8 và phải khác mật khẩu hiện tại. Mật khẩu được băm bằng BCrypt đang dùng; backend khóa hàng tài khoản, cập nhật hash và tăng `users.auth_version` cùng transaction với sự kiện `PASSWORD_CHANGED` trong `admin_security_events`. Bảng sự kiện không chứa mật khẩu/hash và chặn sửa/xóa ở database.

JWT mới mang `authVersion`. JWT cũ không có claim được coi là phiên bản 0 để các phiên đang dùng tiếp tục hoạt động sau migration; khi admin đổi mật khẩu, phiên bản tăng lên và tất cả JWT cũ bị từ chối ở yêu cầu tiếp theo. Sau khi đổi, admin đăng nhập lại bằng mật khẩu mới.

MFA bằng TOTP đã có và bắt buộc trước khi ADMIN quản lý người dùng. Xem [thiết lập, đăng nhập, mã khôi phục và vận hành MFA](ADMIN_MFA.md). Admin phải tự liên kết ứng dụng; không tự bật MFA trên tài khoản local bằng một khóa chưa được chủ tài khoản xác nhận.

## Phạm vi chức năng quản trị

| Nhóm | Trạng thái | Quyền và chức năng |
| --- | --- | --- |
| Tài khoản nội bộ | Đã có | Cấp ADMIN bằng bootstrap một lần; đăng nhập, xem phiên, đổi mật khẩu và đăng xuất. ADMIN chỉ dùng API quản trị, USER chỉ dùng API nghiệp vụ khách hàng. |
| Người dùng | Đã có | Xem danh sách, tìm theo tên/email, lọc ACTIVE/LOCKED, phân trang và xem chi tiết. |
| Kiểm soát tài khoản | Đã có | Khóa/mở USER với lý do bắt buộc; xem nhật ký thao tác theo người dùng. |
| Gói và quota AI | Cần làm sau khi chốt chính sách | Xem gói Free/Pro, kỳ hiệu lực, mức sử dụng và lịch sử quota. Điều chỉnh thủ công cần lý do và audit riêng. |
| Thanh toán | Cần làm cùng tích hợp cổng thanh toán | Xem giao dịch, trạng thái xác nhận từ nhà cung cấp, đối soát, hoàn tiền và yêu cầu hỗ trợ. Không cấp quyền lợi Pro dựa trên trạng thái do trình duyệt gửi. |
| Vận hành quản trị | Cần làm trước khi mở rộng đội ngũ | Xem audit tổng hợp, quản lý nhiều admin theo mức quyền và khôi phục mật khẩu. MFA TOTP đã có. Tài khoản ADMIN không tự cấp thêm ADMIN qua API hiện tại. |

`ADMIN`/`USER` là vai trò truy cập; Free/Pro là gói dịch vụ của USER. Role không tự quyết định quota hay trạng thái thanh toán.

## Kiểm tra và giới hạn

Các kiểm tra tự động bao gồm quyền Guest/USER/ADMIN, JWT của admin bị khóa, validation, trường trả về, bootstrap không tự nâng quyền/reset admin, tìm kiếm ký tự đặc biệt, lọc/phân trang trên PostgreSQL và audit cùng transaction. Kiểm tra HTTP chạy cả Spring Boot với schema thử riêng: bootstrap admin → đăng nhập → kiểm tra quyền → khóa USER → từ chối JWT cũ/đăng nhập khi khóa → đọc audit → mở khóa và đăng nhập lại. SMTP và scheduler được tắt/thay thế trong kiểm tra này; không dùng database ứng dụng.

Frontend kiểm tra bằng TypeScript, production build và lint. Trong sandbox Windows có thể dùng `npm run build -- --configLoader native` nếu config bundler mặc định bị chặn subprocess. Browser automation trong phiên hiện tại không có trình duyệt khả dụng; kiểm tra trực quan trên desktop/mobile cần thực hiện khi có browser, không được coi là đã xác nhận bằng ảnh.

Chưa thêm chức năng cấp role qua API, xóa tài khoản, reset mật khẩu admin qua bootstrap, quản trị gói hoặc giao dịch thanh toán.
