# Hướng dẫn pilot kết nối Meta bằng access token

> **Trạng thái:** hướng dẫn cho pilot có giới hạn. Kết nối hiện tại là nhập token thủ công; Postiva chưa có OAuth tự phục vụ và chưa xác minh Meta App Review/Advanced Access cho production. Không giới thiệu OAuth là tính năng đang hoạt động.

## Phạm vi pilot đề nghị

| Nền tảng | Có trong mã nguồn | Phạm vi nên cho pilot |
|---|---|---|
| Facebook Page | Kết nối Page bằng Page Access Token; đăng chữ, ảnh (tối đa 10 ảnh theo kiểm tra trong code), một video; xem/đồng bộ bài Page. Nút kiểm tra kết nối tạo một bài Page không công khai. | Nền tảng pilot ưu tiên. Dùng Page do chính người tham gia pilot quản trị; xác nhận quyền đăng trước khi lên lịch. |
| Instagram Business | Kết nối token thủ công theo một trong các chế độ API; đăng ảnh JPEG, video MP4/MOV hoặc carousel; cần media, không hỗ trợ bài chỉ có chữ. | Chỉ thử nội bộ/nhóm giới hạn sau khi xác minh loại tài khoản, chế độ API, quyền và token cụ thể. Chưa cam kết hỗ trợ đại trà. |
| Threads | Kết nối token thủ công; đăng chữ (tối đa 500 ký tự theo kiểm tra trong code), ảnh/video và carousel. | Chỉ thử nội bộ/nhóm giới hạn sau khi xác minh quyền và khả năng đăng bằng tài khoản thực. Chưa cam kết hỗ trợ đại trà. |

Các giới hạn trong bảng là giới hạn mà mã nguồn hiện kiểm tra; quy tắc của Meta và khả năng của tài khoản có thể khác. Trước khi chạy pilot, kiểm tra lại định dạng và giới hạn đang áp dụng trong Meta Developer Dashboard và trên tài khoản đích.

## Chuẩn bị Page Access Token

Người có quyền quản trị/đầy đủ quyền với Facebook Page thực hiện trên ứng dụng Meta đã cấu hình cho dự án:

1. Mở Meta for Developers, chọn đúng ứng dụng của Postiva và dùng công cụ tạo token dành cho nhà phát triển/tester đã được thêm vào ứng dụng. Chọn quyền tối thiểu cần cho mục đích pilot. Mã nguồn hiện khai báo `pages_show_list`, `pages_read_engagement`, `pages_manage_posts`; quyền thực tế phụ thuộc cấu hình app, vai trò người dùng và Meta cấp cho ứng dụng.
2. Dùng user token có quyền đọc Page để lấy danh sách Page mà tài khoản có thể truy cập, ví dụ qua Graph API Explorer với trường `id,name,access_token` trên `/me/accounts`. Chọn đúng Page và dùng **Page Access Token** trả về cho Page đó, không dán user token nếu mục tiêu là kết nối một Page.
3. Kiểm tra lại Page ID, tên Page, quyền token và thời hạn/token status bằng công cụ Meta hiện hành. Không đưa token vào URL chia sẻ, ticket, chat, email, ảnh chụp màn hình hoặc lệnh được lưu vào lịch sử shell.
4. Trong Postiva, mở **Kết nối tài khoản**, đi tới **Trang Facebook**, nhập Page ID, tên Page và Page Access Token, rồi bấm **Kết nối**. Máy chủ gọi Meta để xác nhận token truy cập được đúng Page ID trước khi lưu.
5. Sau khi lưu, bấm **Kiểm tra**. Với Facebook, Postiva tạo một bài thử ở trạng thái không công khai để kiểm tra quyền đăng. Nếu kiểm tra lỗi, không tạo lịch đăng cho tới khi sửa quyền/token và kiểm tra thành công.

Tên công cụ và cách Meta cấp quyền có thể thay đổi. Nếu ứng dụng chưa được duyệt quyền phù hợp, token có thể chỉ dùng được với tài khoản có vai trò trong app hoặc môi trường development. Không hướng dẫn người dùng cuối vượt qua giới hạn quyền của Meta; chỉ đưa pilot cho người có quyền trên Page và được chủ dự án cho phép.

## Cấu hình media cho Instagram/Threads khi chạy local

- Trong `backend/.env`, đặt `POSTIVA_PUBLIC_BASE_URL` bằng URL HTTPS công khai trỏ tới backend đang chạy, ví dụ URL tunnel ngrok tới cổng 8080. Chế độ `dev` dùng giá trị này; nếu có `POSTIVA_LOCAL_PUBLIC_BASE_URL` thì biến đó được ưu tiên. URL phải trả về file media, không phải trang đăng nhập hoặc trang cảnh báo của tunnel.
- Đặt `POSTIVA_MEDIA_SIGNING_SECRET` bằng khóa ngẫu nhiên riêng, ổn định, có ít nhất 32 byte UTF-8. Giữ khóa ngoài Git và log; không dùng Page Access Token làm khóa ký. Khóa dùng để ký URL media có hạn sử dụng cho Meta tải file. Đổi khóa sẽ vô hiệu hóa các URL đã cấp.
- Khởi động lại backend sau khi đổi `.env`; giữ tunnel hoạt động trong lúc Meta tải và xử lý media. Facebook tải file trực tiếp nên Facebook đăng được chưa chứng minh URL media công khai đã hoạt động.
- Lỗi cấu hình hoặc lỗi xử lý media trước bước publish được ghi `FAILED` kèm nguyên nhân; lỗi mạng tạm thời ở bước chuẩn bị có thể được thử lại theo backoff. Mất phản hồi ở bước publish cuối vẫn là `UNKNOWN`, cần đối soát trước khi thử lại để tránh đăng trùng.
- Bản ghi `UNKNOWN` tạo trước bản sửa không tự đổi trạng thái. Kiểm tra nguyên nhân trong chi tiết lịch; xác nhận chưa đăng nếu lỗi xảy ra trước bước gửi bài, rồi chỉ thử lại nền tảng thất bại. Không đăng lại Facebook nếu Facebook đã thành công.

## Token hết hạn, bị thu hồi hoặc thiếu quyền

- Backend mã hóa token khi lưu. API kết nối chỉ trả ID/tên/Page và thời điểm hết hạn nếu được cung cấp; không trả lại token cho giao diện sau khi lưu.
- Postiva kiểm tra token khi kết nối, khi người dùng bấm **Kiểm tra/Cập nhật**, hoặc khi thực hiện đăng. Lỗi mà Meta nhận dạng được là hết hạn/thu hồi sẽ được báo yêu cầu lấy token mới và kết nối lại; lỗi thiếu quyền sẽ chỉ dẫn kiểm tra quyền.
- Với token Page, form hiện không nhận trường thời hạn. Nếu không có `expiresInSeconds`, hệ thống lưu thời hạn là chưa biết và **không thể cảnh báo trước ngày hết hạn**. Thu hồi hoặc hết hạn được nhận biết ở lần gọi Meta kế tiếp. Cần coi nhãn “Đã lưu · cần kiểm tra hiệu lực” là trạng thái đã lưu thông tin kết nối, không phải chứng nhận token còn dùng được.
- Nếu token hết hạn/bị thu hồi: quản trị viên Page lấy token mới, dùng **Kết nối** lại cùng Page, bấm **Kiểm tra**, rồi kiểm tra/sắp xếp lại các bài đăng bị lỗi. Không retry mù lịch mà kết quả lần trước chưa rõ.
- Instagram Login và Threads có mã làm mới token cho một số token đủ điều kiện; Facebook Page token thủ công không có cơ chế refresh tự động trong luồng hiện tại. Không hứa refresh tự động cho mọi loại token.

## Bảo mật token

- Chỉ nhập token vào Postiva qua HTTPS và chỉ trên tài khoản người sở hữu Page. Không yêu cầu người dùng gửi token cho nhân viên hỗ trợ.
- Mã nguồn mã hóa token trong DB bằng `TokenCipher` (AES-GCM); `POSTIVA_TOKEN_ENCRYPTION_KEY` phải là bí mật ổn định có ít nhất 32 byte UTF-8, được cấp ngoài repository và backup an toàn cùng quy trình khôi phục. Không commit khóa thật vào Git; thay hoặc mất khóa khiến token đã lưu không thể giải mã.
- Rà soát log trước pilot: không ghi request body/header, token đã giải mã, hoặc URL query có `access_token`; không bật wiretap/debug HTTP ghi URL/body ở client Meta. Không đưa token vào thông báo lỗi. Source hiện không có lệnh log token rõ ràng trong luồng quản lý kết nối; cấu hình logger/proxy production cần được xác minh riêng.
- Khi người dùng ngắt kết nối trong Postiva, token được xóa khỏi bản ghi. Callback deauthorization/xóa dữ liệu cũng xóa token nếu signed `user_id` khớp bản ghi; giới hạn khớp với token thủ công được mô tả bên dưới. Nếu nghi token bị lộ, thu hồi token tại Meta trước, sau đó kết nối lại bằng token mới.

## Deauthorization và xóa dữ liệu từ Meta

- Cấu hình `POSTIVA_META_PUBLIC_BASE_URL` thành origin HTTPS công khai của API (ví dụ `https://api.example.com`) trước khi khai báo callback trong Meta Developer Dashboard. Ứng dụng tạo URL trạng thái từ cấu hình này, không lấy host từ request đến.
- Các callback nhận `signed_request` nằm tại `POST /api/social/meta/deauthorize`, `POST /api/social/meta/delete-data`, cùng các route Instagram/Threads tương ứng. `GET /api/social/{meta,instagram,threads}/delete-data/status/{confirmationCode}` là endpoint công khai, không cần JWT; mã UUID ngẫu nhiên là thông tin đủ để tra trạng thái. Phản hồi chỉ gồm mã, nền tảng, trạng thái, số kết nối đã xóa và thời gian hoàn tất.
- Với kết nối được signed `user_id` khớp, deauthorization xóa token, ngắt kết nối và hủy lịch đang chờ. Nếu một worker đang gửi bài, trạng thái chuyển sang `UNKNOWN` để không retry tự động; cần kiểm tra bài trên nền tảng.
- Với kết nối được khớp, data-deletion xóa kết nối, lịch và log gắn với kết nối; đồng thời xóa metadata nhận diện bài từ Meta khỏi nội dung Postiva. Bài Facebook được đồng bộ từ Page bị xóa nếu không còn gắn với kết nối khác.
- **Giới hạn hiện tại:** callback của Meta xác định người dùng bằng app-scoped `user_id`, trong khi luồng Page Access Token thủ công không lưu ID đó. Vì vậy không thể bảo đảm callback sẽ tìm được mọi kết nối token thủ công. Chưa được xem là luồng xóa dữ liệu đã kiểm chứng cho người dùng thật cho tới khi có OAuth hoặc cơ chế liên kết ID tin cậy và thử callback thật. Người dùng pilot cần ngắt kết nối trong Postiva và gửi yêu cầu xóa qua kênh hỗ trợ khi cần xóa dữ liệu liên quan.
- Chưa xác minh callback từ Meta Developer Dashboard hoặc máy chủ production. URL cấu hình chỉ hợp lệ nếu truy cập được công khai qua HTTPS sau reverse proxy.

## Điều kiện tiếp nhận pilot

- Pilot giới hạn ở nhóm người hiểu cách tạo token và có quyền quản trị Page; mỗi người tự kết nối Page thuộc quyền của họ.
- Tại thời điểm đầu, bật Facebook Page làm luồng pilot chính. Instagram và Threads chỉ được giới thiệu là thử nghiệm nếu đã kiểm chứng trên tài khoản phù hợp.
- Hướng dẫn người dùng cách lấy token, kiểm tra kết nối, tạo bài thử không công khai, nhận biết lỗi quyền/token và kết nối lại. Công bố rõ OAuth chưa khả dụng, token có thể bị thu hồi/hết hạn và bài tự động có thể cần can thiệp khi Meta từ chối.
- Cho tới khi scheduler có cơ chế claim chống chạy trùng và retry/đối soát an toàn, giới hạn pilot ở kiểm tra kết nối và đăng thử/đăng ngay có người giám sát; không dùng lịch tự động cho nội dung quan trọng hoặc cam kết giờ đăng.
- Theo dõi log đã được làm sạch, lỗi Meta, lịch FAILED và yêu cầu hỗ trợ; có người phụ trách pilot xử lý trước khi mở rộng.
- Tiếp tục App Review/Advanced Access và OAuth song song. Chỉ đổi thông điệp sản phẩm sang “kết nối OAuth” sau khi callback đã triển khai, được kiểm thử, và quyền cần thiết được Meta cấp/duyệt.

## Tình trạng đã kiểm tra trong mã nguồn

- Không tìm thấy authorize/callback OAuth đang được dùng; UI hiện gửi token thủ công tới API.
- Endpoint kết nối Facebook gọi Graph API để xác minh Page ID trước khi lưu token mã hóa.
- Nút **Kiểm tra** Facebook gọi API đăng bài không công khai; Instagram/Threads gọi API đọc hồ sơ nên chưa chứng minh đầy đủ quyền publish.
- Thời hạn token tùy chọn qua API nhưng không có trường nhập ở UI; Page token không có thời hạn thì chỉ phát hiện lỗi khi gọi Meta.
- Source có refresh tự động gần hạn cho một số Instagram Login/Threads token; không có refresh tương tự cho Facebook Page token thủ công.
- Kiểm tra này dựa trên source; chưa thử token thật, Meta Dashboard, App Review hoặc máy chủ production.
