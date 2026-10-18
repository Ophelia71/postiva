# Gói Free/Pro và kiểm soát chi phí AI

## Chính sách đang áp dụng

| Nội dung | Free | Pro |
| --- | --- | --- |
| Lượt tạo bài AI | 30 lượt dùng thử mỗi kỳ 30 ngày | 50 lượt mỗi kỳ 30 ngày |
| Cách tính lượt | Trừ 1 lượt khi bài được lưu thành công | Trừ 1 lượt khi bài được lưu thành công |
| Khi AI từ chối, lỗi hoặc không lưu được bài | Không trừ lượt; yêu cầu vẫn tính vào hạn mức chống lạm dụng | Không trừ lượt; yêu cầu vẫn tính vào hạn mức chống lạm dụng |
| Quyền sử dụng các chức năng khác | Như hiện tại | Như hiện tại; gói hiện chỉ tăng hạn mức AI |

Kỳ mặc định là 30 ngày liên tục theo UTC, không phải tháng lịch. Một yêu cầu có thể đang được giữ chỗ trong lúc xử lý; lượt chỉ được tính sau khi bài và bản ghi sử dụng cùng commit thành công. Cùng `Idempotency-Key` và cùng nội dung sẽ trả lại bài cũ; dùng lại key với nội dung khác bị từ chối. Mỗi tài khoản có tối đa một yêu cầu AI đang xử lý, 20 lần thử/ngày; toàn hệ thống tối đa 100 lần thử/ngày theo mặc định.

Pro phải được mở bằng sự kiện thanh toán đã xác minh, có mã tham chiếu duy nhất và thời hạn gói. `VerifiedSubscriptionPlanService.activateProFromVerifiedPayment(...)` là điểm provisioning nội bộ cho webhook thanh toán; hiện dự án chưa tích hợp cổng thanh toán hay endpoint tự mua Pro. Hạn mức và ngày bắt đầu/kết thúc được lưu thành snapshot, nên thay đổi cấu hình không sửa các kỳ đang chạy.

## Chính sách lỗi và chi phí

- Lỗi xác định từ nhà cung cấp giải phóng phần ngân sách dự phòng và không trừ lượt gói.
- Timeout hoặc HTTP 5xx trước khi ứng dụng lưu được bài được đánh dấu chưa xác định; quota gói được trả lại nhưng ngân sách dự phòng vẫn bị giữ để tránh gọi lặp làm tăng chi phí. Phản hồi có nội dung hợp lệ nhưng thiếu số token vẫn có thể lưu bài và tính quota; phần chi phí chưa rõ tiếp tục giữ chỗ.
- Khi bảng chi phí OpenAI đã có dữ liệu cho ngày đã qua ít nhất một ngày trọn vẹn, phần dự phòng chưa xác định của ngày đó được giải phóng sau đối soát.
- Nếu OpenAI đã trả token usage nhưng ứng dụng không phân tích/lưu được bài, chi phí token vẫn được ghi nhận, còn lượt gói không bị trừ.
- Hạn mức chống lạm dụng tính cả lần gọi lỗi. Retry cùng key không gọi nhà cung cấp lần nữa.

Mỗi lần gọi giữ trước một khoản tối đa 2 lần ước lượng, dựa trên 32.000 token đầu vào và 1.800 token đầu ra. Dữ liệu này khóa ngân sách ngày và tháng dưới transaction/row lock, nên nhiều worker không thể đồng thời vượt hạn mức. Khi chạm ngân sách hoặc khoản dự phòng tiếp theo không còn vừa, API dừng yêu cầu mới. Cảnh báo được ghi một lần mỗi kỳ khi đạt 80%.

Chi phí theo từng yêu cầu là ước lượng từ số token OpenAI trả về và đơn giá cấu hình tại thời điểm gọi. Đối soát chuẩn theo hóa đơn OpenAI bật khi backend có `OPENAI_ADMIN_API_KEY` với quyền đọc chi phí và `OPENAI_PROJECT_ID` của project Postiva riêng. Backend lấy dữ liệu từ [OpenAI Organization Costs API](https://platform.openai.com/docs/api-reference/usage/costs); đồng bộ mỗi 6 giờ. Project riêng tránh tính lẫn chi tiêu khác trong cùng Organization. Nếu chưa cấu hình Admin API key/project, màn hình quản trị hiển thị rõ đây chỉ là ước lượng theo token.

Mặc định backend dùng `gpt-6-luna` với reasoning effort thấp cho tác vụ viết bài. Giá cấu hình mỗi triệu token là input `$0.10`, đọc cache `$0.01`, ghi cache `$0.125` và output `$0.50`; ứng dụng lưu riêng số token ghi cache để không tính thiếu chi phí. Giá có thể thay đổi theo model, độ dài prompt, processing tier và bảng giá hiện hành; dashboard admin hiển thị model cùng các đơn giá đang cấu hình. Giá token vẫn là ước lượng theo cấu hình, còn Organization Costs API là nguồn đối soát tổng project. GPT-4o mini đã được OpenAI công bố ngừng API ngày 14/01/2027 và khuyến nghị thay bằng GPT-6 Luna, nên mặc định mới đã chuyển model. Tham khảo [bảng giá API OpenAI](https://developers.openai.com/api/docs/pricing) và [lịch ngừng model OpenAI](https://developers.openai.com/api/docs/deprecations). Không dùng giá này làm giá bán gói Pro.

## Cấu hình production

Các biến môi trường được khai báo trong `backend/.env.example`. Mặc định ngân sách là `$10/ngày` và `$100/tháng`, cảnh báo ở 80%; có thể điều chỉnh theo ngân sách vận hành. `POSTIVA_AI_ENABLED=false` là công tắc tắt khẩn cấp ở lớp cấu hình. Admin đã bật MFA cũng có thể dùng trang `/app/admin/ai-budget` để bật/tắt ở runtime; hai công tắc đều phải bật thì hệ thống mới nhận yêu cầu. Runtime switch được ghi lý do vào audit table.

Trước khi đưa vào production:

1. Tạo OpenAI Project riêng cho Postiva, cập nhật model và đơn giá theo bảng giá hiện hành.
2. Chọn ngân sách ngày/tháng nhỏ cho pilot; kiểm tra dashboard, log cảnh báo và kết quả đối soát.
3. Cấp `OPENAI_ADMIN_API_KEY` chỉ có quyền cần thiết, lưu bằng secret manager; không đưa vào frontend hoặc log.
4. Chạy Flyway migration 9; giữ backup DB trước khi phát hành. Migration 9 chỉ thêm cách lưu token ghi cache và đơn giá tương ứng.
5. Chỉ bật Pro sau khi payment webhook xác minh chữ ký, trạng thái thanh toán, user, kỳ đăng ký và mã sự kiện duy nhất. Phần webhook/checkout còn thuộc công việc thanh toán tiếp theo.

## API vận hành

- `GET /api/admin/ai-budget`: ngân sách ngày/tháng, ước lượng token, chi phí project đã đối soát, khoản dự phòng, trạng thái đồng bộ và số yêu cầu chi phí chưa xác định.
- `PATCH /api/admin/ai-budget/switch` với `{ "enabled": false, "reason": "..." }`: dừng/khôi phục AI. Route yêu cầu admin đã xác minh MFA.

Giới hạn hiện tại là bảo vệ theo chi phí mà backend ghi nhận hoặc OpenAI đối soát cho Project. Mức trần không thể ngăn chi tiêu phát sinh trực tiếp ngoài ứng dụng trong cùng OpenAI Project; vì vậy cần dùng project riêng và cấu hình hard spend limit/alert ở phía OpenAI nếu tài khoản hỗ trợ.
