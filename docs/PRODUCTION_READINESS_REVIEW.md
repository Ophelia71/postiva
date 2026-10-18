# Đánh giá khả năng đưa Postiva vào sử dụng thực tế

Ngày rà soát: 16/10/2026. Phạm vi: mã nguồn trong workspace, cấu hình triển khai trong repository và kết quả kiểm tra cục bộ. Đây không phải chứng nhận môi trường production; chưa truy cập máy chủ, Meta App Dashboard hoặc tài khoản thanh toán thực tế.

**Cập nhật 17/10/2026:** Đã triển khai bước 1 gồm giới hạn login/OTP, mô hình kỳ Free/Pro, quota nguyên tử, chống gửi trùng yêu cầu AI và lịch sử sử dụng. Local đã migrate V5, bảo toàn dữ liệu cũ; full backend 72 tests pass. Xem [phạm vi và bằng chứng bước 1](USER_SECURITY_AND_AI_USAGE.md). Các mục A/C bên dưới mô tả phát hiện tại thời điểm audit 16/10; phần kiểm soát này đã được bổ sung, còn quên/đặt lại mật khẩu USER chưa triển khai. Scheduler, OAuth, CI/vận hành, thanh toán và analytics tiếp tục theo thứ tự; kết luận chưa sẵn sàng bán công khai vẫn giữ nguyên.

## 1. Kết luận

**Chưa đủ điều kiện mở công khai và thu tiền trên phạm vi chức năng đang định hướng.** Có nền tảng để tiếp tục hoàn thiện và thử nghiệm nội bộ có giám sát. Trước khi mời khách hàng bên ngoài cần xử lý các điểm chặn bên dưới, giới hạn chi phí và kiểm chứng luồng đăng bài trên tài khoản thực tế được cho phép.

Không nên dùng tỷ lệ phần trăm hoàn thành để quyết định phát hành: một lỗi đăng trùng hoặc cấp sai quyền sau thanh toán có thể ảnh hưởng trực tiếp khách hàng dù phần lớn giao diện đã có.

## 2. Những phần đã có

- Đăng ký xác thực email, đăng nhập JWT, kiểm tra tài khoản ACTIVE/LOCKED và phiên bản xác thực.
- Admin quản lý người dùng, khóa/mở với lý do, audit; đổi mật khẩu và MFA TOTP, mã khôi phục, giới hạn thử sai dành cho admin.
- Flyway V1–V4, Hibernate validate; đã có quy trình kiểm tra migration và bảo toàn dữ liệu cục bộ.
- Tạo nội dung qua OpenAI, quản lý bài và media, lịch đăng, adapter kết nối nền tảng và lưu kết quả đăng.
- Token mạng xã hội được mã hóa; có xử lý signed request cho deauthorization/data deletion.
- Có Docker, Jenkins và Nginx TLS. Sự hiện diện của cấu hình chưa chứng minh triển khai thực tế đã vận hành đúng.

## 3. Các điểm chặn cần xử lý

### A. AI chưa được kiểm soát chi phí — trước khi mở đăng ký công khai

`backend/src/main/java/com/postiva/post/service/PostService.java:51` gọi AI khi tạo bài nhưng chưa có subscription, quota theo người dùng, sổ ghi nhận sử dụng hoặc cơ chế giữ chỗ quota khi nhiều yêu cầu đồng thời. Không tìm thấy module payment/billing tương ứng trong source hiện tại.

Hậu quả: tài khoản có thể gửi nhiều yêu cầu làm phát sinh chi phí thực; timeout rồi bấm lại cũng có thể tạo thêm lượt gọi nhà cung cấp.

Cần làm: mô hình entitlement Free/Pro tách khỏi role USER/ADMIN; quota có cập nhật nguyên tử, request idempotency, usage ledger, giới hạn đồng thời và ngân sách toàn hệ thống. Quy định rõ khi nào tính lượt, hoàn lượt khi lỗi và chu kỳ reset. Giá, quota và nhà cung cấp thanh toán phải được chủ dự án chốt; không coi con số đề xuất trước đây là yêu cầu đã duyệt.

### B. Lịch đăng chưa có cơ chế chống xử lý trùng — trước khi cam kết tự động đăng

`scheduler/service/PublishingService.java:130` lấy tối đa 20 lịch và gọi nền tảng trong một transaction. `scheduler/repository/ScheduledPostRepository.java:15` chưa claim công việc nguyên tử hoặc khóa chống nhiều worker lấy cùng lịch.

Rủi ro suy ra từ mã: hai instance có thể cùng đăng; nền tảng đã nhận bài nhưng tiến trình chết trước DB commit sẽ làm trạng thái nội bộ không phản ánh kết quả. Retry khi kết quả chưa rõ có thể đăng lần nữa. Đây là rủi ro thiết kế, chưa phải sự cố đã tái hiện trên production.

Cần làm: claim có lease/khóa và commit trước khi gọi API; transaction riêng từng công việc; timeout, backoff và giới hạn retry; lưu trạng thái kết quả chưa xác định để đối soát thay vì retry mù. Không hứa exactly-once nếu API nền tảng không hỗ trợ. Chuẩn hóa UTC/timezone vì hiện dùng LocalDateTime và thời gian máy chủ.

### C. Bảo vệ đăng nhập và OTP người dùng còn thiếu — trước khi mở công khai

`auth/service/AuthService.java:68,122,171`: OTP có thời hạn và hash nhưng chưa có bộ đếm thử sai/cooldown gửi lại; đăng nhập USER chưa có bảo vệ tương đương admin. Việc gửi email nằm trong transaction cũng cần xử lý lỗi và khả năng retry rõ ràng.

Cần làm: giới hạn theo tài khoản và IP với cấu hình proxy tin cậy, giới hạn gửi email, thông báo tránh dò tài khoản, kiểm thử nhiều request đồng thời; bổ sung quên/đặt lại và đổi mật khẩu USER với token một lần, có hạn và thu hồi phiên phù hợp. Tham khảo [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html).

### D. Kết nối Meta chưa sẵn sàng cho khách hàng tự phục vụ

Controller Facebook/Instagram/Threads hiện cung cấp kết nối bằng token thủ công; chưa thấy authorize/callback OAuth trong source đang dùng. Nhập token phù hợp kiểm thử có hỗ trợ, gây khó khăn cho khách hàng phổ thông.

Cần làm: chọn nền tảng phát hành đầu tiên và tập quyền tối thiểu; OAuth với state một lần, ràng buộc phiên/người dùng, redirect URI chính xác; kiểm tra quyền thực tế, hết hạn/thu hồi token và kết nối lại. Đối chiếu App Review/access level và xác minh doanh nghiệp theo quyền, sản phẩm và mô hình sử dụng trên Meta Dashboard. Source không đủ để kết luận app đã hoặc chưa được Meta duyệt; không mặc định mọi tích hợp có cùng yêu cầu.

Các GET `/api/social/{meta,instagram,threads}/delete-data/status/{confirmationCode}` hiện được public không cần JWT và chỉ nhận UUID ngẫu nhiên để tra mã xác nhận; URL trả về được tạo từ `POSTIVA_META_PUBLIC_BASE_URL` bắt buộc HTTPS, tránh tin `Host` header. Data-deletion dọn kết nối, lịch/log và metadata bài Meta; deauthorization xóa token, hủy lịch chờ và chuyển tác vụ đang gửi sang UNKNOWN. Tuy nhiên, luồng token thủ công không lưu app-scoped Meta user ID, nên chưa thể đảm bảo callback khớp đúng tất cả kết nối. Cần triển khai/kiểm chứng cơ chế liên kết ID qua OAuth hoặc cách được Meta hỗ trợ, rồi thử signed callback thật và URL public trên staging.

### E. Thanh toán còn thiếu — chặn phát hành gói trả phí

Chưa thấy luồng đơn hàng, giao dịch, subscription và xử lý callback thanh toán trong source. Không nên thu tiền rồi cấp Pro bằng kết quả redirect trên trình duyệt.

Cần làm: tạo đơn phía server, xác thực chữ ký callback/webhook, đối chiếu số tiền/tiền tệ/mã đơn, xử lý lặp và sai thứ tự, cấp quyền nguyên tử, hết hạn gói, đối soát và hoàn tiền/hủy theo nghiệp vụ. Chỉ mở trả phí sau khi kiểm thử callback giả, lặp, thiếu, chậm; mất kết nối và giao dịch thật có kiểm soát. Chọn nhà cung cấp sau khi chốt pháp nhân/tài khoản nhận tiền và mô hình bán gói.

## 4. Các phần chưa hoàn chỉnh khác

| Phần | Bằng chứng và việc cần hoàn thiện |
|---|---|
| Thống kê và bình luận | Số lượt xem/thích/bình luận/chia sẻ đã được gỡ khỏi payload và giao diện; trang nêu rõ chưa đồng bộ, endpoint bình luận trả HTTP 501. Đồng bộ tương tác/bình luận vẫn chưa triển khai. |
| Trạng thái đăng | Lịch/báo cáo làm mới định kỳ; màn lịch hiển thị lỗi gần nhất, số lần thử, thời điểm retry, thao tác thử lại và thông báo trong ứng dụng. Cần kiểm chứng timeout, Meta nhận bài nhưng app mất kết quả, token bị thu hồi và worker restart trên staging; thông báo email/push chưa có. |
| Media | `file/storage/LocalFileStorage.java:37` tin MIME từ request. Cần kiểm tra nội dung file, quota dung lượng, dọn file mồ côi và backup media. GET/HEAD file đang public: ai có URL có thể tải cả media bản nháp; cần chốt quyền riêng tư và cơ chế URL ký có hạn phù hợp việc nền tảng tải media. |
| Timeout AI | OpenAI chờ tới 90 giây, frontend 120 giây, Nginx trong repo chưa ghi đè proxy_read_timeout. Mặc định Nginx là 60 giây giữa hai lần đọc: có nguy cơ 504 nếu upstream im lặng lâu. Cần thống nhất timeout hoặc chuyển job bất đồng bộ. [Tài liệu Nginx](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_read_timeout). |
| CI/CD | Backend Docker build bỏ qua tests; Jenkins chưa có bước test/lint và smoke test sau deploy. Compose dùng image latest. Cần gate kiểm tra, deploy image theo digest/tag cố định, health/readiness check và rollback rõ ràng. |
| Vận hành | Chưa thấy bằng chứng trong repo về backup tự động/restore định kỳ, cảnh báo lỗi đăng bài/chi phí/dung lượng, mục tiêu khôi phục và hướng dẫn xử lý sự cố. Cần kiểm tra hệ thống ngoài repo trước khi kết luận thiếu hoàn toàn. Backup phải gồm DB, media và khóa mã hóa; mất khóa có thể khiến token/MFA không giải mã được. |
| Trải nghiệm và hỗ trợ | Cần kiểm thử end-to-end trên điện thoại, trạng thái chờ/lỗi/hết quyền, email thật, quy trình hỗ trợ mất MFA, xóa tài khoản và chính sách dữ liệu/thanh toán đúng hành vi sản phẩm. |

## 5. Bằng chứng kiểm tra và giới hạn

- Log full backend gần nhất `backend/target/admin-mfa-final-full-tests.log`, hoàn tất 16/10/2026 15:25 +07: **57 tests, 0 lỗi, 0 bỏ qua**. Đây là kết quả có sẵn được đối chiếu, không phải chạy lại toàn bộ trong lần audit này. Không cộng các file surefire cũ vì có thể còn báo cáo của class đã thay đổi/xóa.
- Lần audit này chạy trực tiếp `node frontend/tests/auth-session.test.mjs`: **4/4 pass**. Chế độ `node --test` ban đầu bị môi trường sandbox chặn spawn EPERM; chạy file trực tiếp thành công.
- Build/lint frontend đã qua ở đợt triển khai trước, còn hai cảnh báo Fast Refresh; chưa chạy lại trong lần audit này.
- Chưa kiểm thử trình duyệt end-to-end, tải/concurrency toàn hệ thống, sự cố mạng với Meta, thanh toán thật hay cấu hình máy chủ production. Các test admin/migration không chứng minh toàn bộ luồng khách hàng đã sẵn sàng.
- Source đang có thay đổi chưa commit; không suy ra máy chủ đang chạy phiên bản tương ứng. Lần rà soát này không thay đổi dữ liệu hoặc deploy.

## 6. Thứ tự triển khai đề nghị

1. **Bảo vệ đăng ký/đăng nhập/OTP và chi phí AI**: rate limit; chốt mô hình gói; quota nguyên tử và usage ledger. Đây là công việc nên làm tiếp ngay.
2. **Ổn định đăng bài**: claim job, transaction riêng, timeout/retry/đối soát, timezone; thử hai worker và tình huống crash sau khi nền tảng nhận bài.
3. **Hoàn thiện OAuth cho một nền tảng trước**; sửa URL xác nhận xóa dữ liệu; chuẩn bị hồ sơ và kiểm tra quyền trên Meta song song vì phụ thuộc bên ngoài.
4. **Hoàn thiện vận hành và kiểm thử pilot**: CI gate, image cố định, backup/restore cả dữ liệu và khóa, cảnh báo; test toàn bộ hành trình bằng tài khoản kiểm thử riêng. Bật MFA admin thực tế và xác minh đường khôi phục.
5. **Triển khai thanh toán và vòng đời gói**, dựa trên entitlement/quota đã có; qua đối soát và kiểm thử giao dịch rồi mới nhận tiền khách hàng.
6. **Hoàn thiện analytics/bình luận hoặc thu hẹp phạm vi công bố**. Có thể phát hành MVP không gồm chúng nếu giao diện và nội dung bán hàng nói rõ, không trả số 0 như số liệu thật.

Điều kiện pilot bên ngoài: đã chặn lạm dụng/chi phí, bảo vệ lịch đăng, quyền truy cập chéo tài khoản được kiểm thử, luồng Meta được phép và chạy thật, có giám sát và khôi phục. Điều kiện bán công khai: thêm thanh toán/đối soát, quyền lợi gói chính xác, hỗ trợ và chính sách dữ liệu phù hợp; có bằng chứng kiểm thử trên bản triển khai sẽ phát hành.
