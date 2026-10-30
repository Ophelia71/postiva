# Thanh toán Pro qua payOS: phạm vi, cấu hình và điều kiện mở bán

Cập nhật 17/10/2026. Mã nguồn có tích hợp **payOS cho giao dịch VND tại Việt Nam**, nhưng đây là phương án kỹ thuật đang chờ chủ dự án xác nhận merchant/pháp nhân, giá bán và chính sách. `POSTIVA_BILLING_ENABLED=false` và giá mặc định bằng 0: bản triển khai hiện tại không tự nhận tiền.

Theo [hướng dẫn payOS](https://payos.vn/docs/api/) và [xác thực tổ chức](https://payos.vn/docs/huong-dan-su-dung/xac-thuc-to-chuc/), cá nhân, hộ kinh doanh hoặc doanh nghiệp cần có tài khoản đã xác thực, tài khoản ngân hàng nhận tiền và kênh thanh toán trước khi lấy Client ID, API Key, Checksum Key. Chủ dự án cần xác nhận pháp nhân/chủ tài khoản và điều kiện hóa đơn, thuế, phí theo thỏa thuận của mình. Không dùng tài khoản của lập trình viên làm merchant thực tế.

## Cách đơn giản: chuyển khoản VietQR, admin xác nhận

Khi payOS chưa cấu hình, Postiva bán Pro bằng chuyển khoản thẳng vào tài khoản của shop. Chỉ cần ba biến:

```text
POSTIVA_BANK_CODE=TPB
POSTIVA_BANK_ACCOUNT_NO=<số tài khoản>
POSTIVA_BANK_ACCOUNT_NAME=<TÊN CHỦ TÀI KHOẢN, KHÔNG DẤU>
```

Người mua bấm Nâng lên Pro, nhận mã VietQR (img.vietqr.io) đã điền số tiền và nội dung `POSTIVA<mã đơn>`, chuyển khoản rồi bấm "Tôi đã chuyển khoản". Đơn mở 3 ngày. Admin vào **Thanh toán → Chuyển khoản chờ xác nhận**, tìm trong app ngân hàng giao dịch đúng số tiền và nội dung, nhập mã giao dịch ngân hàng và bấm **Đã nhận tiền**: đơn thành `PAID` (tham chiếu `BANK:<mã>`, không dùng lại được cho đơn khác) và Pro bật ngay, kỳ Pro có nguồn `BANK:<mã đơn>`. **Không thấy tiền** đóng đơn kèm lý do; tiền đến muộn vẫn xác nhận được. Mọi thao tác ghi vào `payment_admin_audit`. Đơn chuyển khoản không bao giờ gọi payOS; khi payOS đã sẵn sàng thì payOS được dùng cho đơn mới.

## Luồng đã cài đặt (payOS)

1. Backend trả giá, quota AI và thời hạn từ cấu hình; người dùng không gửi số tiền. Mỗi đơn chụp giá, quota và kỳ Pro vào PostgreSQL. Một người dùng chỉ có một đơn `CREATING`/`PENDING` còn mở; `Idempotency-Key` ngăn gửi lặp.
2. Backend tạo [payment link](https://payos.vn/docs/api/) với mã đơn duy nhất, chữ ký HMAC-SHA256 và hạn 15 phút. Checkout chỉ nhận URL HTTPS đúng host `pay.payos.vn`. Nếu phản hồi tạo link bị mất, tác vụ nền thử lại cùng mã đơn và tra cứu link tại payOS.
3. Return/cancel URL chỉ hiển thị trạng thái lấy từ backend. Endpoint webhook công khai `/api/billing/payos/webhook` kiểm tra chữ ký của `data` theo [quy tắc payOS](https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/). Sau đó đối chiếu mã đơn, VND, số tiền, payment link, tài khoản nhận tiền nếu đã biết, và mã giao dịch. Trạng thái `PAID` không bị hạ bởi callback đến muộn; cùng giao dịch/sự kiện chỉ ghi một lần.
4. Xác nhận đã trả tiền được lưu trước việc cấp gói. `grant_status` riêng (`PENDING`/`GRANTED`/`FAILED`) cho phép retry khi ghi entitlement lỗi. Mỗi mã đơn chỉ tạo một kỳ Pro; gia hạn đang Pro nối sau kỳ đã mua cuối cùng. Khi Pro hết hạn, dịch vụ quay về Free qua thời hạn kỳ; không tự động trừ tiền định kỳ.
5. Tác vụ nền mỗi 2 phút tra cứu payOS cho các đơn chờ, hết hạn, có bất thường và thử cấp quyền lại cho đơn đã trả. Phản hồi provider phải có chữ ký hợp lệ; chỉ `PAID` với tổng tiền đúng và một tham chiếu giao dịch xác định mới được cấp Pro. Đơn đã hết hạn tại Postiva vẫn có thể ghi nhận tiền đến muộn và cấp kỳ đã mua.
6. Người dùng yêu cầu hoàn toàn bộ tiền một đơn; admin đã qua MFA duyệt, đánh dấu xử lý bên ngoài, rồi **chỉ xác nhận thành công sau khi kiểm tra chứng từ ngân hàng/cổng** và nhập mã hoàn độc nhất. Kỳ Pro của đơn đó được vô hiệu hóa, lịch sử thu tiền và lượt AI cũ vẫn giữ. payOS [API payment link hiện công bố](https://payos.vn/docs/api/) không có thao tác refund cho payment link, nên chuyển tiền hoàn và đối chiếu tiền về tài khoản khách là việc vận hành bên ngoài Postiva. Trạng thái `SUCCEEDED` ở Postiva là xác nhận thủ công của admin, không phải webhook hoàn tiền của payOS.

Các API: `GET /api/billing/offer`, `POST /api/billing/orders` (header `Idempotency-Key: UUID`), `GET /api/billing/orders`, `GET /api/billing/orders/{id}`, `POST /api/billing/orders/{id}/cancel`, `POST /api/billing/orders/{id}/refresh`, `POST/GET /api/billing/orders/{id}/refund-requests`; admin xem đơn/sự kiện/nhật ký, đối soát thủ công và xử lý hoàn dưới `/api/admin/billing/**`. Mọi API admin cần ADMIN hoạt động và MFA đã xác minh. Lịch sử khách hàng chỉ trả đơn của chính họ.

## Cấu hình trước khi mở bán

Đặt các biến sau bằng secret manager hoặc môi trường máy chủ, không commit giá trị thật:

```text
POSTIVA_BILLING_ENABLED=false
POSTIVA_PRO_PRICE_VND=149000
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
```

Return/cancel URL mặc định là `POSTIVA_FRONTEND_ORIGIN` + `/app/billing`, chính sách hoàn tiền mặc định là trang công khai `/refund-policy` của frontend. Chỉ đặt `POSTIVA_BILLING_RETURN_URL`, `POSTIVA_BILLING_CANCEL_URL`, `POSTIVA_BILLING_REFUND_POLICY_URL` khi muốn trỏ nơi khác. Khi chạy trên máy (không có profile staging/prod) chấp nhận `http://localhost`/`127.0.0.1`; staging/prod vẫn bắt buộc HTTPS. Webhook local đi qua ngrok: `https://<ngrok>/api/billing/payos/webhook`.

Khi người mua bấm thanh toán, trang Gói Pro chuyển thẳng sang trang mã QR của payOS. Lúc quay về, trang gọi `POST /api/billing/orders/{id}/refresh` để hỏi payOS ngay (không phải chờ webhook hay đợt đối soát 2 phút); nếu người mua đã hủy trên trang payOS, đơn được ghi nhận là đã hủy và lần mua sau tạo đơn mới thay vì trả lại link đã đóng.

`POSTIVA_PRO_PRICE_VND` phải là giá cuối cùng người mua sẽ trả (gồm các khoản cần công bố theo mô hình kinh doanh), đã được duyệt và khớp trang bán hàng; giá đã được chủ dự án chốt ngày 30/10/2026: 149.000 VND cho 30 ngày, 300 lượt AI (Free giữ 30 lượt). Cần chốt quota, kỳ 30 ngày, điều kiện hoàn tiền, hóa đơn/chứng từ và người hỗ trợ trước khi bật `POSTIVA_BILLING_ENABLED=true`. URL phải là HTTPS công khai; webhook phải đăng ký trên kênh payOS và kiểm tra bằng tính năng xác nhận webhook của nhà cung cấp.

Khi tạm dừng bán bằng `POSTIVA_BILLING_ENABLED=false`, **giữ khóa payOS và webhook hoạt động** cho các đơn đã tạo: backend vẫn nhận tiền đến muộn, đối soát và cấp quyền còn nợ. Không xóa khóa hoặc tắt endpoint trong lúc còn đơn chưa kết thúc.

payOS [không cung cấp sandbox riêng](https://payos.vn/docs/moi-truong-test/). Chỉ dùng merchant/thẻ ngân hàng thử nghiệm có kiểm soát và giao dịch giá trị nhỏ **sau khi** đã xác minh cấu hình, chạy unit/integration test, có phương án hoàn và được chủ dự án duyệt việc phát sinh tiền thật. Tách pilot merchant khỏi tài khoản nhận tiền kinh doanh nếu có thể. Không tự đặt `POSTIVA_BILLING_ENABLED=true` trong môi trường người dùng trước khi hoàn thành các bước này.

## Kiểm tra bắt buộc trước khi nhận tiền khách

- Chạy `mvn test` với PostgreSQL test chuyên dụng (`POSTIVA_TEST_DB_URL=jdbc:postgresql://localhost:5432/postiva_migration_test`), `npx tsc -b`, `npm run lint`, frontend build. Áp dụng V10 trên bản sao dữ liệu staging, đối chiếu dữ liệu cũ và có backup/restore.
- Trên merchant thật, thử tạo link, thanh toán một giao dịch giá trị nhỏ, webhook hợp lệ/chữ ký sai, mất webhook rồi đối soát, callback lặp/sai thứ tự, khách đóng trang, link hết hạn và thanh toán đến muộn, gia hạn hai kỳ, khóa user sau khi tạo đơn, hoàn tiền thật và chứng từ. So sánh đơn Postiva với báo cáo payOS và sao kê ngân hàng. Ghi lại bằng chứng từng ca.
- Giám sát đơn `PAID` mà `grant_status != GRANTED`, `REVIEW`, webhook lỗi và chênh lệch số tiền. Xuất báo cáo thu/hoàn/phí/tiền thực nhận để kế toán đối chiếu theo lịch merchant. Tác vụ hiện tại đối chiếu **trạng thái giao dịch**, chưa tự đối chiếu phí và số tiền quyết toán ngân hàng.
- Xác nhận live URL của bản sẽ phát hành, email hỗ trợ, quy trình hoàn tiền cho người không đăng nhập được, hóa đơn và chính sách công khai. Chỉ mở bán sau khi có người vận hành xử lý lệch/hoàn tiền.

Nếu chủ dự án đã ký merchant VNPAY/MoMo hoặc bán ở thị trường khác, giữ công tắc tắt và thay adapter cổng trước khi mở bán. Không dùng chữ ký payOS cho cổng khác.
