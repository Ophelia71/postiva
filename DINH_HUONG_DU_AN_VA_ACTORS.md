# Định hướng dự án Postiva và các actor

> Tài liệu định hướng, cập nhật ngày 15/10/2026. Các nội dung ghi **đề xuất** là phạm vi cần triển khai, chưa phải tính năng đã hoàn thành.

Theo xác nhận của chủ dự án, Postiva đi vào hoạt động và có thu tiền thật. Quy tắc mục tiêu tại [Nghiệp vụ MVP](NGHIEP_VU_MVP.md) bao gồm trạng thái tài khoản, quyền admin, gói Free/Pro, quota, thanh toán production, đối soát và hoàn tiền. Giá/quota đề xuất phải được xác nhận trước khi áp dụng; giữ nguyên quyền lợi đã cam kết với khách hàng.

## 1. Hướng phát triển

Postiva là ứng dụng web giúp chủ cửa hàng và người làm marketing quản lý nội dung mạng xã hội. Người dùng nhập thông tin sản phẩm, nhận gợi ý bài viết từ AI, chỉnh sửa, kết nối kênh, đăng ngay hoặc lên lịch, rồi xem trạng thái và báo cáo.

Hướng sản phẩm phù hợp là **SaaS cho cửa hàng nhỏ**, với luồng cốt lõi: **tạo nội dung → duyệt/chỉnh sửa → đăng hoặc lên lịch → theo dõi kết quả**. Bản đầu ưu tiên một người dùng quản lý nội dung của mình. Nhiều nhân viên cùng làm việc trong một workspace là tính năng mở rộng sau MVP.

Vai trò `USER` và `ADMIN` dùng để phân quyền hệ thống. Gói Free và Pro biểu thị quyền sử dụng dịch vụ; gói cước không phải là role.

## 2. Hiện trạng và phạm vi đề xuất

| Năng lực | Hiện có trong dự án | Cần bổ sung cho MVP |
| --- | --- | --- |
| Tài khoản | Đăng ký/OTP/JWT; `USER`/`ADMIN`; `ACTIVE/LOCKED`; API/giao diện quản lý USER và audit. | Trang tài khoản khách hàng, khôi phục mật khẩu. |
| Nội dung AI | Tạo và lưu bài viết qua OpenAI; người dùng có thể chỉnh sửa. | Ghi nhận lượt dùng và giới hạn theo gói. |
| Mạng xã hội | Kết nối Facebook, Instagram, Threads bằng cách dán access token; có chức năng đăng bài. | OAuth để người dùng tự cấp quyền khi phát hành cho khách hàng thật. |
| Lịch đăng | Đăng ngay, đặt lịch, xử lý bài đến hạn, xem trạng thái. | Quy tắc rõ cho bài lỗi, bài đã lên lịch khi gói hết hạn. |
| Báo cáo | Có danh sách và biểu đồ theo bài/trạng thái. | Chỉ hiển thị chỉ số tương tác khi có dữ liệu thật từ nền tảng. |
| Quản trị | Cấp admin nội bộ; đổi mật khẩu/thu hồi JWT; MFA TOTP; API/giao diện tìm kiếm/phân trang USER, khóa/mở và nhật ký bất biến. | Quản lý gói và giao dịch khi có mô hình thanh toán. |
| Thanh toán | Chưa thấy tích hợp trong mã nguồn được rà soát; cần đối chiếu hệ thống đang vận hành. | Gói trả trước 30 ngày, thanh toán production, cấp gói, tra soát/đối soát và hoàn tiền. |

Các nhận định về mã nguồn dựa trên [API xác thực](backend/src/main/java/com/postiva/auth/controller/AuthController.java), [cấu hình bảo mật](backend/src/main/java/com/postiva/config/SecurityConfig.java), [dịch vụ tạo bài](backend/src/main/java/com/postiva/post/service/PostService.java), [API lập lịch](backend/src/main/java/com/postiva/scheduler/controller/PublishingController.java) và [giao diện kết nối](frontend/src/pages/ConnectionsPage.tsx). Đối chiếu thêm phiên bản đang deploy và quy trình kinh doanh thực tế trước khi sửa hệ thống đang dùng.

## 3. Actor chính

Actor chính là người chủ động bắt đầu một mục tiêu nghiệp vụ với Postiva.

| Actor | Mục tiêu | Use case chính |
| --- | --- | --- |
| **Khách chưa đăng nhập (Guest)** | Tìm hiểu và bắt đầu sử dụng Postiva. | Xem trang giới thiệu; đăng ký; xác thực OTP; đăng nhập. |
| **Người dùng (USER)** | Tạo và vận hành nội dung cho cửa hàng/thương hiệu. | Tạo, chỉnh sửa bài; kết nối kênh; đăng ngay hoặc lên lịch; xem bài, lịch và báo cáo; xem hạn mức; mua/gia hạn gói Pro; xem lịch sử thanh toán. |
| **Quản trị viên (ADMIN)** | Vận hành dịch vụ và hỗ trợ người dùng. | Quản lý trạng thái USER; xem gói/quota/giao dịch; xử lý tra soát, đối soát và hoàn tiền theo chính sách; xem nhật ký thao tác. |

Tài khoản admin phải được cấp nội bộ; đăng ký công khai luôn tạo `USER`. API quản trị phải kiểm tra quyền ở backend. Admin không mặc nhiên cần quyền xem nội dung riêng tư hoặc access token mạng xã hội của người dùng.

## 4. Actor phụ

Actor phụ là hệ thống bên ngoài cung cấp kết quả cho use case của actor chính.

| Actor phụ | Vai trò trong luồng |
| --- | --- |
| **OpenAI API** | Tạo nội dung từ thông tin sản phẩm; trả kết quả hoặc lỗi để Postiva ghi nhận. |
| **Meta Graph APIs: Facebook, Instagram, Threads** | Cấp quyền kết nối, xác minh tài khoản, nhận yêu cầu đăng bài và trả kết quả. |
| **VNPAY** *(đề xuất)* | Cung cấp trang thanh toán và gửi thông báo kết quả về backend. |
| **Dịch vụ email/SMTP** | Gửi OTP và thông báo liên quan đến tài khoản. |

Bộ lập lịch chạy trong backend là **thành phần nội bộ**, không phải actor bên ngoài. Ngân hàng của khách hàng tương tác thông qua cổng thanh toán, nên MVP không cần tích hợp trực tiếp từng ngân hàng.

## 5. Use case trọng tâm

1. **Đăng ký:** Guest nhập thông tin → Postiva gửi OTP qua email → Guest xác thực → hệ thống tạo tài khoản `USER` ở gói Free.
2. **Tạo bài:** USER nhập thông tin sản phẩm → hệ thống kiểm tra hạn mức → OpenAI tạo bản nháp → USER chỉnh sửa và lưu.
3. **Xuất bản:** USER chọn tài khoản mạng xã hội đã kết nối → đăng ngay hoặc đặt lịch → Postiva ghi trạng thái thành công/thất bại.
4. **Theo dõi:** USER xem danh sách bài, lịch đăng, lỗi và báo cáo thuộc tài khoản của mình.
5. **Nâng cấp:** USER chọn Pro → backend tạo đơn với giá do hệ thống quyết định → USER thanh toán → backend xác thực thông báo từ cổng → kích hoạt thời hạn Pro.
6. **Quản trị:** ADMIN tìm người dùng, xem trạng thái/gói/giao dịch và xử lý khóa tài khoản hoặc ngoại lệ có lưu lý do.

## 6. Hướng thanh toán

**Mô hình thu tiền thật:** Free và Pro trả trước theo kỳ 30 ngày, gia hạn thủ công. Mức Free **5 lượt**, Pro **50 lượt AI** và **99.000 đồng/kỳ** hiện chỉ là đề xuất, chưa phải giá/quota được chủ dự án xác nhận. Gia hạn sớm đề xuất nối thêm kỳ tiếp theo, không reset quota hiện tại; quy tắc phải khớp cam kết với khách hàng. Xem đầy đủ tại [Nghiệp vụ MVP](NGHIEP_VU_MVP.md).

Thu tiền bằng cấu hình **production** của cổng đã lựa chọn; sandbox dùng để kiểm thử và không cấp quyền production. Backend xác thực kết quả từ cổng qua IPN hoặc truy vấn máy chủ; ReturnURL chỉ hiển thị. Tách trạng thái đã trả tiền với trạng thái đã cấp gói, xử lý thông báo lặp và thử lại khi cấp gói lỗi. VNPAY là phương án đề xuất: [tài liệu thanh toán](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), [truy vấn và hoàn tiền](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html). Có đối soát, kênh hỗ trợ và chính sách hoàn tiền trong phạm vi vận hành.

Khi gói hết hạn và không có kỳ Pro tiếp theo, hệ thống giữ bài viết và lịch sử rồi bắt đầu kỳ Free mới. Lịch đăng hợp lệ vẫn hoạt động vì cả Free và Pro đều có quyền lên lịch. Khi tài khoản bị khóa, các lịch chưa gửi bị hủy và không tự chạy lại sau mở khóa.

## 7. Hướng kết nối Meta bằng OAuth

Hiện dự án dùng cách người dùng dán access token. Để khách hàng thật tự kết nối kênh, nên xây OAuth: nút kết nối → màn hình cấp quyền của Meta → callback backend đổi mã lấy token → lưu token được mã hóa → hiển thị kênh đã kết nối. Đăng nhập Postiva bằng email/mật khẩu là luồng riêng; OAuth Meta chỉ cấp quyền cho kênh mạng xã hội.

Tài khoản có vai trò trong Meta App phục vụ thử nghiệm nội bộ. Khi cung cấp cho khách hàng bên ngoài, cần kiểm tra yêu cầu **Business Verification**, **App Review** và **Advanced Access** theo từng quyền/API và cấu hình ứng dụng, tham chiếu [tài liệu Meta](https://developers.facebook.com/docs/graph-api/overview/access-levels/). OAuth và quyền phù hợp là phần cần hoàn thiện cho các nền tảng được cung cấp công khai. Chuẩn bị việc này song song với tích hợp cổng thanh toán.

## 8. Dữ liệu và thứ tự triển khai

Flyway đã được thiết lập với V1 khởi tạo database mới, V2 thêm trạng thái tài khoản/nhật ký admin, V3 thu hồi phiên JWT khi admin đổi mật khẩu và V4 bổ sung MFA TOTP; Hibernate chuyển sang validate. Database hiện hữu phải đối chiếu và baseline rõ ràng theo [hướng dẫn migration](database/README.md), không tự đánh dấu baseline. Cần bổ sung tiếp gói/kỳ sử dụng, đơn/sự kiện thanh toán, cấp gói, hoàn tiền/đối soát và lượt AI. [File reset cũ](database/schema.sql) có bảng `ai_generation_logs` nhưng mã ứng dụng chưa ghi vào bảng này; file chứa `DROP TABLE` và không còn được Docker tự nạp. Giữ sao lưu và bảo toàn quyền lợi hiện hữu khi nâng cấp.

Thứ tự hợp lý:

1. Xác nhận giá/quota, chính sách, cổng và dữ liệu đang vận hành; bảo toàn quyền lợi khách hiện hữu. Chuẩn bị cổng production và quyền Meta song song.
2. Chuẩn hóa migration/sao lưu; bảo vệ API, thêm trạng thái tài khoản và quản lý USER.
3. Thêm gói Free/Pro và quota AI; chuyển dữ liệu hiện hữu có đối chiếu.
4. Hoàn thiện thanh toán production, thử lại cấp gói, tra soát, đối soát, hoàn tiền và hỗ trợ khách hàng.
5. Hoàn thiện OAuth/quyền cho các nền tảng cung cấp công khai và nghiệm thu vận hành.

**Ngoài phạm vi MVP:** nhiều thành viên trong một workspace, tự động trừ tiền định kỳ, ví credit và các chỉ số báo cáo chưa có nguồn dữ liệu xác thực.
