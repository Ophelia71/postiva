# Nghiệp vụ MVP Postiva — vận hành và thu tiền thật

> Phiên bản 1.1 — ngày 15/10/2026. Theo xác nhận của chủ dự án, Postiva là sản phẩm đi vào hoạt động và có thu tiền thật. Đây là đặc tả nghiệp vụ mục tiêu; không phải xác nhận các chức năng đã được triển khai. Giá 99.000 đồng và quota 5/50 trong bản trước chưa được chủ dự án xác nhận, nên chỉ là đề xuất; không thay thế giá hoặc quyền lợi đã cam kết với khách hàng.

> Cập nhật kỹ thuật 17/10/2026: mã nguồn có adapter payOS tắt mặc định; VNPAY trong đặc tả dưới đây là đề xuất cũ, chưa phải cổng đã chốt. payOS không có sandbox riêng. Xem [phạm vi và điều kiện mở bán payOS](docs/BILLING_PAYOS.md) trước khi cấu hình thu tiền.

Tài liệu này cụ thể hóa [định hướng dự án và actors](DINH_HUONG_DU_AN_VA_ACTORS.md). Khi triển khai MVP, dùng các quy tắc trong tài liệu này cho trạng thái tài khoản, gói, quota và thanh toán.

## 1. Phạm vi vận hành

| Nội dung | Quyết định |
| --- | --- |
| Khách hàng mục tiêu | Chủ cửa hàng nhỏ hoặc người làm nội dung tự quản lý một thương hiệu. |
| Mô hình sử dụng | Mỗi người dùng sở hữu dữ liệu của mình; chưa có nhóm, workspace hoặc phân quyền nhân viên. |
| Vai trò lưu trong hệ thống | `USER`, `ADMIN`. Guest là người chưa đăng nhập, không phải role lưu trong database. |
| Trạng thái tài khoản | `ACTIVE`, `LOCKED`. Đăng ký chưa xác thực nằm trong dữ liệu đăng ký chờ, chưa phải tài khoản sử dụng được. |
| Gói dịch vụ | Free và Pro. Role và gói được quản lý riêng. |
| Free | Giá 0 đồng; đề xuất 5 lượt tạo nội dung AI mỗi kỳ 30 ngày, chờ xác nhận quota thực tế. |
| Pro | Gói trả phí thật theo giá đang công bố; đề xuất ban đầu 99.000 đồng và 50 lượt/30 ngày, chưa phải mức đã được phê duyệt. |
| Khác biệt giữa hai gói | Quota AI. Cả hai vẫn được chỉnh sửa, đăng ngay, lên lịch và xem dữ liệu của mình. |
| Cách thanh toán | Trả trước qua cổng thanh toán production; người dùng chủ động mua hoặc gia hạn. VNPAY là phương án tích hợp đề xuất, chưa xác nhận nhà cung cấp đang dùng. |
| Khi hết Pro | Chuyển về Free, giữ dữ liệu và các lịch đăng còn hợp lệ. |
| Kết nối mạng xã hội cho khách hàng | Người dùng tự kết nối qua OAuth và quyền phù hợp với nền tảng được cung cấp. Token nhập thủ công hiện tại chỉ phục vụ nội bộ hoặc hỗ trợ kỹ thuật có kiểm soát. |
| Môi trường thử nghiệm | Sandbox/staging để kiểm thử; dữ liệu và kết quả thử không cấp quyền hoặc ghi doanh thu vào production. |

Giá, quota, điều kiện gia hạn và chính sách hỗ trợ phải khớp thông tin đang công bố cho khách hàng. Giá phải tính đến chi phí AI, hạ tầng, phí cổng thanh toán và vận hành; chưa đủ dữ liệu để xác nhận mức 99.000 đồng có phù hợp. Backend lưu phiên bản quyền lợi và giá của từng đơn/kỳ; cập nhật gói chỉ áp dụng cho giao dịch mới theo chính sách công bố, không giảm quyền lợi đã bán.

## 2. Actors và quyền

| Actor | Quyền trong MVP |
| --- | --- |
| Guest | Xem giới thiệu/bảng gói, đăng ký, xác thực OTP và đăng nhập. |
| USER đang `ACTIVE` | Quản lý nội dung, kênh kết nối và lịch đăng của mình; tạo bài theo quota; xem gói, mức sử dụng, giao dịch; mua/gia hạn Pro. |
| ADMIN đang `ACTIVE` | Xem/tìm kiếm USER; xem trạng thái, gói, quota và giao dịch; khóa/mở tài khoản với lý do; xử lý đối soát/yêu cầu hoàn tiền theo quyền được cấp và chính sách; xem nhật ký thao tác. |
| OpenAI API | Nhận yêu cầu tạo nội dung, trả kết quả hoặc lỗi. |
| Meta APIs | Xác minh kênh kết nối, nhận yêu cầu xuất bản và trả kết quả. |
| Cổng thanh toán production | Thu tiền, trả kết quả thanh toán, cung cấp dữ liệu tra soát/hoàn tiền và đối soát. VNPAY là nhà cung cấp đề xuất. |
| Dịch vụ email | Gửi OTP và thông báo tài khoản theo khả năng hiện có. |

Quy tắc phân quyền:

- Đăng ký công khai luôn tạo `USER`; không nhận role do frontend gửi lên.
- Admin được cấp nội bộ bằng cơ chế khởi tạo an toàn. MVP không có chức năng cấp role hoặc khóa tài khoản ADMIN qua giao diện quản trị USER.
- Backend kiểm tra quyền và chủ sở hữu dữ liệu trên từng thao tác. Người dùng không truy cập được bài viết, kênh, quota hoặc giao dịch của tài khoản khác.
- ADMIN dùng tài khoản quản trị riêng, không mặc nhiên được xem nội dung riêng tư, mật khẩu hoặc token mạng xã hội. Kiểm tra luồng khách hàng bằng tài khoản USER riêng.
- Không cho admin sửa số tiền của đơn đã tạo hoặc tự đổi giao dịch thành công. Pro trả phí được cấp từ kết quả production đã xác thực qua thông báo cổng hoặc truy vấn phía máy chủ. Hoàn tiền là nghiệp vụ riêng, có nhật ký và tham chiếu giao dịch gốc.

## 3. Vòng đời tài khoản

| Sự kiện | Kết quả |
| --- | --- |
| Gửi đăng ký | Lưu đăng ký chờ và gửi OTP; chưa tạo quyền sử dụng dịch vụ. |
| OTP hợp lệ | Tạo `USER + ACTIVE`; cấp kỳ Free đầu tiên với quota theo cấu hình đang công bố. |
| OTP sai hoặc hết hạn | Không tạo tài khoản; người dùng thực hiện lại luồng xác thực/đăng ký. Thời hạn OTP hiện tại là 10 phút. |
| Admin khóa USER | Chuyển `ACTIVE → LOCKED`, lưu người thực hiện, thời điểm và lý do. |
| Admin mở khóa USER | Chuyển `LOCKED → ACTIVE`, lưu lý do; giữ dữ liệu, lịch sử và quota đã dùng. |

Tài khoản `LOCKED` không được đăng nhập sử dụng dịch vụ, gọi API nghiệp vụ, tạo thanh toán mới hoặc thực hiện xuất bản nền. JWT đã cấp cũng phải bị từ chối ở các yêu cầu tiếp theo; không chờ token tự hết hạn.

Đề xuất: khóa tài khoản không dừng đồng hồ thời hạn Pro và không tự hoàn quota. Việc hoàn tiền/bù thời gian phải theo chính sách đã công bố; không tự áp dụng quy tắc mới cho hợp đồng hiện hữu. Thanh toán hợp lệ của đơn đã tạo trước khi khóa vẫn được ghi nhận và xử lý quyền lợi; tài khoản không tự được mở khóa. Người bị khóa vẫn có kênh liên hệ hỗ trợ để tra soát tiền và quyền lợi.

MVP không xóa cứng tài khoản qua Admin. Các luồng xóa dữ liệu/deauthorization hiện có dành cho Meta vẫn được giữ riêng.

## 4. Quyền lợi và kỳ sử dụng

| Quyền lợi | Free | Pro |
| --- | --- | --- |
| Giá | 0 đồng | Theo giá bán được duyệt; đề xuất 99.000 đồng/30 ngày |
| Tạo nội dung AI | Đề xuất 5 lượt/kỳ | Đề xuất 50 lượt/kỳ |
| Chỉnh sửa bài đã tạo | Có | Có |
| Đăng ngay và lên lịch | Có | Có |
| Kết nối các nền tảng đang được hỗ trợ | Có | Có |
| Xem bài viết, trạng thái và báo cáo hiện có | Có | Có |
| Mua/gia hạn Pro | Có | Có |

MVP chưa đặt giới hạn số kênh hoặc số lịch đăng khác nhau giữa Free và Pro. Gói không làm thay đổi quyền trên từng kênh do nền tảng mạng xã hội cấp.

Quy tắc kỳ sử dụng:

1. Một kỳ dài đúng 30 ngày liên tiếp, không phải tháng dương lịch. Thời gian lưu và so sánh thống nhất theo UTC; giao diện hiển thị giờ Việt Nam.
2. Kỳ có hiệu lực từ thời điểm bắt đầu, hết hiệu lực khi `now >= ends_at`. Backend xác định gói có hiệu lực tại thời điểm xử lý, kể cả khi tác vụ nền chưa cập nhật nhãn trạng thái.
3. Free bắt đầu khi xác thực đăng ký; cứ hết 30 ngày bắt đầu kỳ Free kế tiếp với quota Free đã cấu hình. Bỏ qua các kỳ đã trôi qua nếu người dùng không truy cập lâu ngày; không cộng quota của các kỳ bỏ qua.
4. Mua Pro khi đang Free: kỳ Pro bắt đầu tại thời điểm backend xác nhận thanh toán thành công và kéo dài 30 ngày. Quota Free còn lại không chuyển sang Pro.
5. Gia hạn khi Pro còn hiệu lực: thêm một kỳ Pro sau kỳ đã mua cuối cùng. Kỳ mới có quota theo quyền lợi đã mua, chỉ dùng được khi đến ngày bắt đầu; gia hạn sớm không reset quota kỳ hiện tại.
6. Không có kỳ Pro tiếp theo khi hết hạn: bắt đầu kỳ Free mới tại thời điểm Pro hết hạn, với quota Free đã cấu hình. Nếu thời điểm đó đã qua nhiều kỳ, áp dụng quy tắc Free ở mục 3.
7. Quota còn dư không chuyển kỳ; hết quota không làm mất quyền sửa bài, đăng hoặc lên lịch.

Ví dụ với quota đề xuất 50 lượt: USER đang Pro, còn 10 ngày và đã dùng hết lượt. Thanh toán gia hạn hôm nay nối thêm 30 ngày sau ngày hết hạn; quota mới chỉ có khi kỳ tiếp theo bắt đầu. Quy tắc này phải được hiển thị trước thanh toán. MVP không bán thêm lượt AI lẻ.

## 5. Cách tính quota AI

**Một lượt = một yêu cầu tạo nội dung thành công, có kết quả hợp lệ và được lưu thành bài viết của người dùng.**

Quota này áp dụng cho tạo nội dung văn bản. Sinh ảnh/video bằng AI không thuộc phạm vi MVP; gợi ý hình ảnh dạng văn bản trong bài không phải một lượt sinh ảnh riêng.

| Tình huống | Cách tính |
| --- | --- |
| Tạo một bài có nội dung cho Facebook, Instagram và Threads trong cùng yêu cầu | 1 lượt, không phải 3 lượt. |
| Chủ động bấm tạo lại và có kết quả mới thành công | Thêm 1 lượt. |
| Chỉnh sửa/lưu thủ công, đăng bài, lên lịch | Không trừ lượt AI. |
| Chấm điểm theo quy tắc hoặc tạo lịch theo template hiện tại | Không trừ lượt, vì các luồng hiện tại không gọi AI. |
| Input không hợp lệ hoặc hết quota | Từ chối trước khi gọi OpenAI; không trừ lượt. |
| Lỗi OpenAI, kết quả không hợp lệ hoặc không lưu được bài | Không tính lượt thành công cho người dùng; vẫn ghi nhận lỗi để theo dõi chi phí hệ thống nếu có. |
| Frontend mất kết nối nhưng backend đã lưu thành công | Tính 1 lượt; người dùng xem lại bài trong danh sách. |
| Gửi lại cùng mã yêu cầu do bấm đúp/mất kết nối | Trả trạng thái/kết quả của yêu cầu trước; không tạo bài hoặc tính lượt lần hai. |

Các quy tắc cần bảo đảm khi triển khai:

- Backend là nơi quyết định quota và kỳ sử dụng. Không tin số lượt, gói hoặc user ID do frontend khai báo.
- Mã yêu cầu được kiểm tra trong phạm vi từng USER. Dùng lại mã cho nội dung đầu vào khác phải bị từ chối, không dùng để thay thế yêu cầu trước.
- Khi tiếp nhận yêu cầu, giữ chỗ một lượt để nhiều yêu cầu đồng thời không vượt hạn mức. Thành công thì chốt lượt, thất bại đã xác định thì giải phóng lượt.
- Yêu cầu còn đang xử lý hoặc chưa rõ kết quả giữ trạng thái đang xử lý; mất kết nối ở frontend không tự động hoàn lượt rồi chạy yêu cầu mới.
- Một yêu cầu đã nhận thuộc kỳ tại thời điểm giữ chỗ, kể cả khi hoàn tất sau lúc chuyển kỳ. Không chuyển lượt đó sang kỳ mới.
- Trang tài khoản hiển thị gói, số lượt đã dùng, đang xử lý, còn lại và ngày kết thúc kỳ. Khi hết quota, hiển thị ngày có lượt mới và lựa chọn mua Pro nếu đang Free.

Nhật ký tối thiểu: người dùng, kỳ sử dụng, mã yêu cầu duy nhất, trạng thái xử lý, thời điểm, bài tạo ra và thông tin sử dụng token/model khi nhà cung cấp có trả về. Quota cho khách hàng tính bằng lượt; chi phí nội bộ theo dõi riêng, không quy đổi thành ví tiền.

## 6. Thanh toán thật

### 6.1. Phạm vi

- Production thu tiền thật qua nhà cung cấp được chủ dự án lựa chọn. VNPAY là phương án đề xuất; nếu đang dùng nhà cung cấp khác thì dùng tích hợp và hợp đồng thực tế làm căn cứ.
- Sandbox chỉ phục vụ kiểm thử trước phát hành. Tách cấu hình, khóa bí mật, callback và dữ liệu giữa hai môi trường; kết quả sandbox không kích hoạt gói production.
- Một đơn mua đúng một kỳ Pro 30 ngày. Người dùng thanh toán chủ động; không lưu thẻ và không tự động trừ tiền định kỳ.
- Backend tạo đơn với mã duy nhất và chụp lại giá, đơn vị tiền tệ, quyền lợi tại thời điểm tạo. Frontend chỉ chọn gói, không quyết định số tiền.
- Giá đơn là giá bán đã được duyệt và đang công bố, kèm thông tin phí/thuế theo mô hình kinh doanh. Mức 99.000 VND đang là đề xuất. Cửa sổ thanh toán đề xuất 15 phút, cần khớp cấu hình cổng đang dùng.
- Người dùng có một đơn còn mở tại một thời điểm; bấm thanh toán lại trong thời hạn đó trả về đơn hiện có. Đơn hết thời hạn có thể được thay bằng đơn mới.
- Người mua xem được tổng số tiền, quyền lợi, thời hạn, quy tắc gia hạn và chính sách hoàn tiền trước khi xác nhận; sau đó xem được lịch sử và xác nhận thanh toán. Thông tin hóa đơn/chứng từ được xử lý theo quy trình kinh doanh thực tế.

### 6.2. Luồng chính

1. USER đang `ACTIVE` chọn mua/gia hạn Pro và xem giá, quota, thời gian dự kiến có hiệu lực.
2. Backend tạo hoặc lấy lại đơn thanh toán còn mở, rồi chuyển người dùng sang trang thanh toán production của nhà cung cấp đã cấu hình.
3. Người dùng thực hiện giao dịch trên trang của cổng thanh toán.
4. Backend nhận thông báo của cổng, kiểm tra chữ ký, đơn, số tiền, môi trường, đơn vị nhận thanh toán và kết quả. Khi thiếu thông báo, truy vấn giao dịch phía máy chủ để xác nhận; không tin dữ liệu do trình duyệt tự gửi.
5. Ghi nhận kết quả thanh toán đã xác thực bền vững, rồi cấp đúng một kỳ Pro liên kết với đơn. Có thể thực hiện cùng giao dịch database hoặc lưu công việc cấp gói để thử lại an toàn. Khi gia hạn, nối kỳ mới vào cuối các kỳ đã mua.
6. Trang quay về đọc trạng thái backend: chờ xác nhận tiền; đã nhận tiền/đang kích hoạt; hoặc đã kích hoạt. Nếu cấp gói lỗi, giữ nguyên sự thật đã thanh toán, thử lại và báo vận hành; không yêu cầu khách trả tiền lần nữa.

VNPAY phân biệt IPN để cập nhật kết quả với ReturnURL để hiển thị kết quả. Việc kiểm tra dữ liệu và xử lý thông báo lặp lại dựa trên [tài liệu tích hợp VNPAY PAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), đối chiếu ngày 15/10/2026.

### 6.3. Trạng thái và ngoại lệ

| Trạng thái đơn | Ý nghĩa |
| --- | --- |
| `PENDING` | Đã tạo, chưa có kết quả thanh toán được backend xác nhận. |
| `PAID` | Backend đã xác minh cổng ghi nhận thanh toán thành công. Theo dõi cấp gói riêng bằng `PENDING`, `GRANTED`, `FAILED`, có thử lại. |
| `FAILED` | Cổng đã báo kết quả không thành công hợp lệ. |
| `EXPIRED` | Hết cửa sổ thanh toán nhưng chưa nhận kết quả thành công hợp lệ. |

- Quay về trang thành công, sửa tham số URL hoặc đóng trình duyệt không tự thay đổi trạng thái đơn hay quyền sử dụng.
- Cùng một thông báo gửi nhiều lần chỉ ghi nhận và cấp gói một lần. `PAID` không bị hạ về thất bại/hết hạn bởi thông báo đến sau.
- `EXPIRED` vẫn có thể chuyển thành `PAID` nếu thông báo/truy vấn hợp lệ xác nhận tiền đã được thanh toán. Đơn giữ nguyên giá/quyền lợi đã chụp. Nếu thời gian hoặc số tiền bất thường, đưa vào tra soát và xử lý tiền thực nhận; không bỏ qua tiền của khách chỉ vì đơn đã hết hạn tại Postiva.
- Nếu hai đơn khác nhau đều được xác nhận đã thanh toán hợp lệ, mỗi đơn cấp một kỳ; các kỳ nối tiếp nhau, không chồng thời gian và không reset quota hiện tại.
- Chữ ký sai, sai số tiền, mã đơn không tồn tại hoặc dữ liệu mâu thuẫn: không cấp gói; lưu sự kiện để kiểm tra. Không cho admin đổi đơn thành `PAID` để bỏ qua xác minh.
- Thành công nhưng lỗi cấp gói phải có thể xử lý lại an toàn. Không cấp quyền hai lần; trạng thái thanh toán và trạng thái cấp gói được theo dõi riêng để phát hiện khách đã trả tiền nhưng chưa nhận dịch vụ.
- Lịch sử thanh toán chỉ hiển thị đơn của người dùng hiện tại. Admin xem được dữ liệu giao dịch cần cho vận hành, không xem thông tin thẻ.
- Hoàn tiền, đối soát, hỗ trợ giao dịch và xác nhận thanh toán thuộc phạm vi vận hành của MVP có thu tiền.

Nếu chọn VNPAY, hoàn tất làm việc với nhà cung cấp để có cấu hình merchant production, tài khoản nhận tiền và điều kiện đối soát thực tế. Hồ sơ, phí và lịch chuyển tiền lấy theo thỏa thuận được duyệt; không suy ra từ tài khoản sandbox. Tham khảo [thông tin kết nối của VNPAY](https://vnpay.vn/cong-thanh-toan-chia-khoa-thanh-cong-cho-website-thuong-mai-dien-tu-qd5mre6igtm). Callback dùng HTTPS truy cập được từ cổng; secret chỉ lưu ở backend và có cơ chế thay khóa.

### 6.4. Tra soát và đối soát

- Tác vụ nền truy vấn những đơn còn chờ hoặc có kết quả mâu thuẫn; dữ liệu được xác thực trước khi cập nhật trạng thái và thử cấp gói. Vận hành có danh sách đơn cần xử lý, không dựa vào ảnh chụp chuyển khoản.
- Với VNPAY, phản hồi truy vấn API thành công không đồng nghĩa giao dịch thanh toán thành công; phải kiểm tra trạng thái giao dịch theo [tài liệu QueryDr](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).
- Đối chiếu hằng ngày giao dịch thu, hoàn tiền, phí và tiền được chuyển về tài khoản nhận tiền. Phân biệt số tiền khách trả, phí và tiền thực nhận; lịch đối soát theo thỏa thuận với cổng.
- Lưu mã giao dịch cổng, mã đơn, môi trường, sự kiện xác thực và lần xử lý. Cảnh báo khi đã nhận tiền nhưng chưa cấp gói, đối soát lệch hoặc callback lỗi kéo dài.

### 6.5. Hoàn tiền và hỗ trợ khách hàng

- Dùng chính sách hoàn tiền đang áp dụng cho khách hàng. Nếu chưa có, chủ dự án phải xác nhận điều kiện và công bố trước giao dịch mới; không tự đặt thời hạn hoặc cam kết hoàn tiền trong tài liệu kỹ thuật.
- Tiếp nhận yêu cầu gắn với đơn gốc, lý do và thông tin liên hệ; hỗ trợ tra soát cả khi khách không đăng nhập được. Admin duyệt/từ chối theo chính sách và ghi rõ người xử lý.
- Ghi riêng trạng thái yêu cầu hoàn: `REQUESTED`, `APPROVED`, `PROCESSING`, `SUCCEEDED`, `REJECTED`, `FAILED`. Lịch sử thu tiền gốc được giữ, không sửa thành chưa từng thanh toán.
- Chỉ xác nhận đã hoàn sau kết quả đáng tin cậy từ nhà cung cấp/kênh chi trả được phê duyệt; API nhận yêu cầu không mặc nhiên chứng minh khách đã nhận tiền. Số tiền hoàn lũy kế không vượt số tiền đã thu; yêu cầu gửi lại không hoàn hai lần.
- Khi hoàn thành, cập nhật quyền lợi của kỳ liên quan theo quyết định đã thông báo; giữ bài viết, nhật ký sử dụng và các kỳ khác đã thanh toán. Theo dõi xử lý quyền lợi riêng và thử lại nếu lỗi, không làm mất dấu tiền đã hoàn.
- Quy trình đầu tiên có thể do vận hành thực hiện qua công cụ của cổng, nhưng Postiva vẫn phải lưu mã hoàn, trạng thái và kết quả đối soát. Tham chiếu khả năng truy vấn/hoàn của VNPAY: [QueryDr và Refund](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html).

## 7. Nội dung, lịch đăng và trạng thái tài khoản

| Tình huống | Quyết định |
| --- | --- |
| Hết quota AI | Chặn tạo nội dung AI mới; vẫn sửa, đăng và lên lịch bài đã có. |
| Pro hết hạn | Giữ bài viết, kênh và lịch sử; lịch đăng hợp lệ vẫn được xử lý vì Free cũng có quyền lên lịch. |
| USER bị khóa | Hủy các lịch `SCHEDULED` chưa được gửi đi, chuyển `CANCELLED` và lưu lý do `ACCOUNT_LOCKED`. Bộ xử lý nền phải kiểm tra tài khoản trước khi gửi bài. |
| USER được mở khóa | Không tự khôi phục hoặc đăng bù các lịch bị hủy; người dùng tự chọn lại thời gian. |
| Bài đã được gửi sang nền tảng trước thời điểm khóa | Có thể vẫn hoàn tất bên ngoài; ghi nhận kết quả thực tế. Khóa tài khoản không xóa bài đã xuất bản. |
| Token hết hạn hoặc kênh bị ngắt kết nối | Không báo đăng thành công; giữ bài, lưu lỗi và yêu cầu người dùng kết nối lại trước khi thử lại. |
| Đăng bài thất bại | Không trừ thêm quota AI; chỉ tạo nội dung AI mới mới phát sinh lượt. |

Báo cáo chỉ thể hiện số liệu có nguồn. Các chỉ số tương tác chưa đồng bộ được phải hiển thị “chưa có dữ liệu”, không dùng số 0 giả định để kết luận hiệu quả nội dung.

## 8. Màn hình và dữ liệu cần bổ sung

| Phần | Nội dung tối thiểu |
| --- | --- |
| Tài khoản và gói | Trạng thái tài khoản; gói hiện tại; quota đã dùng/đang xử lý/còn lại; thời hạn; các kỳ Pro đã gia hạn. |
| Mua/gia hạn Pro | Giá bán đã duyệt, quyền lợi, tổng tiền, chính sách hỗ trợ/hoàn tiền, thời điểm kỳ mới bắt đầu; thông báo gia hạn không reset quota hiện tại. |
| Kết quả/lịch sử thanh toán | Mã đơn, tiền đã thu, trạng thái thanh toán, trạng thái cấp gói, thời điểm, kỳ Pro liên quan và thông tin hoàn tiền nếu có. |
| Admin quản lý USER | Danh sách phân trang, tìm kiếm, xem trạng thái/gói/quota; khóa/mở với lý do. |
| Admin vận hành thanh toán | Tra cứu thu/hoàn tiền, đơn chờ xác nhận, lỗi cấp gói, lệch đối soát và nhật ký thao tác. |
| Hỗ trợ và khôi phục tài khoản | Quên/đặt lại mật khẩu an toàn; kênh hỗ trợ tra soát tiền và quyền lợi cho khách hàng. |

Dữ liệu bổ sung gồm trạng thái tài khoản/lý do khóa; danh mục gói; kỳ sử dụng có quota được chụp tại thời điểm cấp; đơn và sự kiện thanh toán; công việc cấp gói có thử lại; yêu cầu hoàn tiền; dữ liệu đối soát; nhật ký AI và admin. Mỗi kỳ Pro được cấp từ thanh toán phải truy ra đơn gốc. Mỗi giao dịch xác định rõ môi trường và nhà cung cấp. Mọi thay đổi schema thực hiện bằng migration có phiên bản.

## 9. Tiêu chí nghiệm thu nghiệp vụ

- [ ] Đăng ký và OTP thành công tạo `USER + ACTIVE + Free`, có quota theo cấu hình đang công bố.
- [ ] USER không gọi được API admin và không đọc/sửa được dữ liệu của USER khác.
- [ ] Admin khóa/mở USER được, có lý do và nhật ký; không khóa ADMIN qua luồng này.
- [ ] USER bị khóa dùng JWT cũ cũng không truy cập được API nghiệp vụ; lịch chưa gửi bị hủy và không tự chạy lại sau mở khóa.
- [ ] Tạo bài thành công dùng đúng một lượt; một bài nhiều nền tảng vẫn tính một lượt.
- [ ] Hết quota thì không gọi AI; tạo đồng thời không vượt quota; gửi lại cùng mã yêu cầu không tạo/tính hai lần.
- [ ] Lỗi tạo bài không trừ lượt thành công; mất kết nối sau khi lưu không tạo thêm bài khi thử lại cùng yêu cầu.
- [ ] Sửa bài, đăng ngay, lên lịch và đăng lại sau lỗi không trừ lượt AI.
- [ ] Thanh toán production hợp lệ cấp đúng một kỳ Pro với giá/quota đã mua; giá do backend quyết định.
- [ ] Chỉ quay về ReturnURL hoặc nhận chữ ký sai không cấp Pro; IPN lặp không cộng thêm kỳ.
- [ ] Gia hạn sớm nối thêm kỳ, không reset quota hiện tại; hai đơn đã trả tiền được nối thành hai kỳ riêng.
- [ ] IPN hợp lệ đến muộn vẫn ghi nhận đúng; trạng thái `PAID` không bị ghi đè bởi thông báo cũ.
- [ ] Hết Pro chuyển về Free đúng thời điểm, không mất bài hoặc lịch đăng; quota cũ không cộng dồn.
- [ ] Thanh toán của đơn đã tạo trước khi khóa vẫn được ghi nhận nhưng không tự mở khóa tài khoản.
- [ ] Đã thu tiền nhưng lỗi cấp gói được phát hiện và thử lại; khách không bị yêu cầu thanh toán thêm.
- [ ] Thiếu IPN vẫn tra soát được từ cổng; API truy vấn thành công không bị nhầm thành thanh toán thành công.
- [ ] Hoàn tiền có lý do, trạng thái và mã tham chiếu; không vượt tiền đã thu hoặc chi lặp; đối soát được kết quả.
- [ ] Kết quả sandbox không cập nhật đơn, kỳ sử dụng hoặc doanh thu production.
- [ ] Khôi phục mật khẩu và kênh hỗ trợ hoạt động; chỉ số tương tác thiếu dữ liệu được ghi rõ.
- [ ] Migration được kiểm tra trên dữ liệu sao chép, không mất giao dịch hoặc quyền lợi của khách hiện hữu; có bản sao lưu có thể phục hồi.

## 10. Giới hạn và bước triển khai tiếp theo

Ngoài MVP: nhiều nhân viên/workspace; thêm role nghiệp vụ; mua lượt AI lẻ hoặc ví credit; trừ tiền tự động; chỉnh giá/gói bằng trang admin; cấp Pro thủ công; quảng cáo trả phí; phân tích tương tác nâng cao. Thu tiền thật, đối soát, hoàn tiền và hỗ trợ khách hàng thuộc phạm vi vận hành.

OAuth và quyền production phù hợp phải hoàn thiện cho từng nền tảng được cung cấp công khai cho khách hàng. Chuẩn bị Meta App/quyền truy cập và làm việc với cổng thanh toán có thể tiến hành song song ngay. Chỉ cam kết các nền tảng và tính năng thực tế đã vận hành được.

Thứ tự hoàn thiện trên hệ thống đang hoạt động:

1. Xác nhận giá, quota, chính sách và cổng đang dùng; kiểm kê dữ liệu khách hàng, tiền đã thu và quyền lợi còn hiệu lực. Bảo toàn các cam kết hiện hữu.
2. Thiết lập migration, sao lưu và kiểm tra phục hồi; thêm trạng thái tài khoản, nhật ký admin và cơ chế cấp admin nội bộ mà không reset dữ liệu đang dùng.
3. Bảo vệ API và tác vụ nền; hoàn thiện quản lý USER, khôi phục mật khẩu và hỗ trợ tài khoản.
4. Xây gói, kỳ sử dụng và quota; chuyển quyền lợi hiện hữu sang mô hình mới có đối chiếu, tránh cấp trùng hoặc làm mất thời hạn đã trả tiền.
5. Tích hợp/thống nhất thanh toán hiện có: kiểm thử sandbox, đưa cấu hình production vào vận hành, bổ sung thử lại cấp gói, tra soát, đối soát và hoàn tiền. Không thu tiền lần hai để bù cho lỗi chuyển đổi hệ thống.
6. Hoàn thiện OAuth/quyền cho các nền tảng đã công bố; theo dõi lỗi xuất bản và nghiệm thu luồng khách hàng thực tế.

## 11. Các thông tin cần chủ dự án xác nhận

- Cổng đang dùng hoặc đã ký kết, tài khoản nhận tiền và quy trình vận hành thanh toán hiện tại; không gửi secret qua tài liệu.
- Giá bán, quota và các gói đã bán thực tế. Cho đến khi có xác nhận, mức 99.000 đồng và 5/50 lượt chỉ là đề xuất.
- Điều kiện gia hạn, hoàn tiền/bù thời gian, kênh hỗ trợ và thời gian xử lý đang cam kết với khách hàng.
- Phiên bản đang deploy, nơi lưu giao dịch/quyền lợi hiện hữu và các nền tảng Meta đã có quyền phục vụ khách hàng.

Các số liệu này có thể khác mã nguồn trong workspace; cần đối chiếu với hệ thống vận hành trước khi viết migration hoặc thay đổi quyền lợi. Tài liệu này không tự thay đổi giá, dữ liệu khách hàng hay cấu hình production.
