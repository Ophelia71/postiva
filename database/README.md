# Migration database Postiva

Flyway quản lý schema tại `backend/src/main/resources/db/migration`. Hibernate dùng `ddl-auto: validate`; không tự thêm/sửa bảng nữa. Hai Docker Compose đã bỏ mount file reset `schema.sql`. File reset cũ vẫn còn để tham chiếu lịch sử, không dùng để nâng cấp dữ liệu hiện hữu.

## Các phiên bản

| Phiên bản | Mục đích |
| --- | --- |
| V1 | Khởi tạo 15 bảng nền tảng cho database trống, khớp kiểu dữ liệu của entity hiện tại. Không dùng để tạo lại bảng trên database có dữ liệu. |
| V2 | Thêm trạng thái/metadata khóa và bảng nhật ký admin. Các USER/ADMIN cũ mặc định `ACTIVE`; không đổi ID, mật khẩu, role, timestamp hoặc nội dung cũ. |
| V3 | Thêm `users.auth_version` mặc định 0 và nhật ký sự kiện bảo mật admin để thu hồi JWT cũ khi đổi mật khẩu. Không đổi hash mật khẩu hoặc phiên bản của tài khoản cũ. |
| V4 | Thêm trạng thái MFA mặc định chưa thiết lập, kho khóa TOTP mã hóa/hash mã khôi phục/bộ đếm nhập sai và sự kiện bảo mật MFA. Admin phải tự xác nhận ứng dụng trước khi quản trị. |
| V5 | Bộ đếm login/OTP, số lần thử sai OTP, kỳ Free/Pro và quota/lịch sử AI. Không đổi dữ liệu cũ; tài khoản cũ được tạo kỳ khi bắt đầu dùng tính năng quota. |
| V6 | Thêm claim/lease cho lịch đăng, lưu thời điểm theo UTC và cách ly lịch cũ đang ở trạng thái xử lý để tránh đăng lặp. |
| V7 | Thêm luồng đặt lại mật khẩu và token khôi phục có thời hạn. |
| V8 | Thêm ngân sách AI ngày/tháng, khoản giữ chỗ chi phí, số token/chi phí theo yêu cầu, công tắc AI có audit và số tiền đối soát từ OpenAI Project. |
| V9 | Bổ sung số token ghi cache cùng đơn giá để hạch toán riêng theo cấu trúc usage của model mới. Chỉ thêm cột và constraint, giữ dữ liệu V8. |

Schema đã được đọc đối chiếu từ database cấu hình trong workspace; đây không phải xác nhận schema đang deploy luôn giống bản local. Các khác biệt đã tính đến gồm `post_scores.score` kiểu INTEGER và bảng `social_data_deletion_requests` mà file reset cũ chưa có. Không tự chuyển NUMERIC sang INTEGER trên dữ liệu đang dùng.

## Database trống

Khởi động backend với đúng `POSTIVA_DB_URL`, `POSTIVA_DB_USERNAME`, `POSTIVA_DB_PASSWORD`. Flyway chạy migration mới nhất (hiện V9) trước khi JPA khởi tạo. PostgreSQL không cần được nạp `schema.sql` bằng Docker. TemplateSeeder của ứng dụng tiếp tục thêm template mặc định còn thiếu.

## Database đã có dữ liệu: thao tác một lần

`baseline-on-migrate` mặc định **false**. Backend chủ động dừng nếu gặp schema chưa được Flyway quản lý; không tự suy đoán đó là database đúng. Chỉ baseline sau khi đã kiểm tra và sao lưu.

1. Ghi nhận đúng server/database/role đang dùng; kiểm kê quyền lợi và dữ liệu hiện hữu. Không thực hiện trên production bằng các thông số database thử nghiệm.
2. Sao lưu bằng `pg_dump` và kiểm tra phục hồi trên database staging riêng. Bản sao lưu có dữ liệu cá nhân/token phải được bảo vệ, không commit vào Git. Với dữ liệu đang vận hành, dừng ghi trong cửa sổ chuyển đổi cần thiết và tránh chạy backend cũ dùng `ddl-auto: update` song song lúc chuyển schema.
3. Chạy `preflight.sql` ở chế độ chỉ đọc; so schema-only dump và các entity với V1. Script kiểm tra các bảng bắt buộc và một số sai lệch đã biết, không thay thế việc so sánh đầy đủ cột/kiểu/constraint. Nếu schema khác, điều tra và chuẩn bị migration phù hợp; không baseline để bỏ qua lỗi.
4. Trên bản staging đã phục hồi, đặt biến môi trường cho Maven rồi lần lượt chạy baseline và migrate. Maven plugin **không tự đọc** `backend/.env`; lấy credentials từ môi trường/secret manager của phiên làm việc, không đưa mật khẩu vào đối số hay commit.

Chạy trong thư mục `backend` sau khi các biến môi trường đã trỏ đúng database:

```text
mvn flyway:info
mvn flyway:baseline
mvn flyway:migrate
mvn flyway:validate
mvn flyway:info
```

Plugin được cấu hình baseline version **1**, schema `public` và `cleanDisabled=true`. Lệnh `baseline` chỉ tạo lịch sử mốc V1, không chạy lại V1; `migrate` sau đó áp dụng các migration tiếp theo. Với database mới, chỉ cần `migrate`, không `baseline` một schema trống ở V1.

5. Đối chiếu số lượng bản ghi và dữ liệu quan trọng trước/sau: ID, email, hash mật khẩu, role, timestamp, JSON bài viết, token mã hóa, lịch đăng và các bảng kinh doanh khác đang có. Xác nhận tài khoản cũ là `ACTIVE`, metadata khóa trống. Không chỉ kiểm tra số dòng vì số dòng giống nhau vẫn có thể bị sửa nội dung.
6. Kiểm tra backend khởi động với Hibernate validation, đăng nhập và lịch đăng trên staging. Sau khi đạt các kiểm tra, áp dụng cùng quy trình cho database vận hành trong kế hoạch phát hành. Thay đổi mã nguồn trong workspace không tự chạy migration trên database vận hành.

Nếu đã có `flyway_schema_history`, kiểm tra `info` và `validate`; không baseline lại hoặc chỉnh/xóa lịch sử để che lỗi checksum. Chỉ thêm migration V6 trở đi; không sửa V1–V5 sau khi đã được triển khai. `clean` bị tắt; không bật để xử lý sự cố. Nếu migration gặp lock quá 5 giây, migration dừng thay vì chờ vô hạn; tìm giao dịch giữ lock rồi thử lại trong cửa sổ phù hợp.

Rollback ứng dụng không cần xóa các cột mới. V2–V9 là thay đổi bổ sung; ưu tiên bản sửa tiến về trước. Không rollback về code chưa kiểm tra MFA sau khi đã bật MFA cho admin. Bản frontend gọi AI phải tương thích header `Idempotency-Key` của backend V5. V8/V9 chỉ được áp dụng khi backend khởi động hoặc chạy `flyway:migrate`; thay đổi source code trong workspace không tự nâng database đang dùng. Nếu cần phục hồi database, dùng bản sao lưu/quy trình đã diễn tập và đối soát mọi ghi phát sinh sau thời điểm sao lưu.

## Lỗi khởi động: non-empty schema nhưng chưa có history

Nếu nguyên nhân cuối cùng trong log là `Found non-empty schema(s) "public" but no schema history table`, database đã có các bảng cũ nhưng chưa được baseline vào Flyway. Các lỗi `flywayInitializer`, `entityManagerFactory`, `userRepository` hoặc `jwtAuthenticationFilter` phía trên là lỗi phụ do khởi tạo database thất bại.

Thực hiện quy trình sao lưu, phục hồi thử, đối chiếu V1, baseline V1 rồi migrate ở trên. Giữ `baseline-on-migrate: false` và `ddl-auto: validate`; việc chuyển database cũ sang Flyway là thao tác rõ ràng một lần.

Ngày 16/10/2026, database **local** cấu hình trong `backend/.env` đã được xử lý theo quy trình này: phục hồi backup sang database thử riêng, đối chiếu 144 cột, baseline V1 và áp dụng V2. Dấu kiểm dữ liệu của cả 15 bảng cũ khớp trước/sau nâng cấp. Backup trước nâng cấp nằm tại `database/backups/postiva_before_flyway_20261016_095556.dump`; thư mục backup được giới hạn quyền tài khoản Windows và loại khỏi Git. Kết quả này chỉ áp dụng cho database local đã kiểm tra, không xác nhận database trên môi trường khác đã migrate.

V3 cũng đã được áp dụng cho database local ngày 16/10/2026 sau khi phục hồi và kiểm tra trên database thử riêng. Backup V2 trước nâng cấp là `database/backups/postiva_before_v3_20261016_143906.dump`. Dữ liệu của 16 bảng có trước V3 khớp trước/sau; `users.auth_version` của các tài khoản cũ bắt đầu từ 0, mật khẩu không bị thay đổi. Backend đang chạy trước thời điểm này cần khởi động lại để nạp mã xác thực mới.

V4 đã được áp dụng cho database local ngày 16/10/2026 sau khi phục hồi backup và khởi động thử với schema mới. Backup V3 là `database/backups/postiva_before_v4_20261016_150852.dump`. Dữ liệu của 17 bảng hiện hữu khớp trước/sau; 1 ADMIN và 5 USER giữ nguyên thông tin, chưa tài khoản nào được tự bật MFA. Khóa mã hóa MFA ngẫu nhiên riêng đã được thêm vào `backend/.env` local và không in ra log. Khởi động lại backend, sau đó admin tự thiết lập ứng dụng tại `/app/admin/security` theo [hướng dẫn MFA](../docs/ADMIN_MFA.md).

V5 được áp dụng cho database local ngày 17/10/2026 lúc 07:07 +07:00. Backup trước nâng cấp: `database/backups/postiva_before_v5_20261017_070644.dump`. Đã restore backup vào database thử, migrate/validate V5 và đối chiếu fingerprint toàn bộ dữ liệu cũ trước khi nâng cấp bản local. Dữ liệu của **18 bảng có trước V5** giữ nguyên (so sánh các cột cũ, không tính cột failed_attempts mới); bảng quota chưa tự cấp hoặc thay đổi quyền lợi tài khoản. Database thử phục hồi đã được dọn sau khi đối chiếu thành công. Khởi động lại backend và dùng frontend mới để áp dụng [login/OTP và quota AI](../docs/USER_SECURITY_AND_AI_USAGE.md).

V6 bổ sung claim/lease cho lịch đăng và chuyển thời điểm lịch sang `TIMESTAMPTZ`. `POSTIVA_LEGACY_SCHEDULE_TIME_ZONE` diễn giải lịch cũ; mặc định UTC phù hợp với ISO UTC frontend đã gửi trước đây. `POSTIVA_LEGACY_TIMESTAMP_TIME_ZONE` diễn giải các cột `created_at`/`updated_at`/`published_at`, vốn có thể theo múi giờ JVM; dùng `UTC` cho ứng dụng cũ chạy trong Docker mặc định, hoặc `Asia/Ho_Chi_Minh` nếu ứng dụng cũ chạy trên host đặt múi giờ Việt Nam. Xác định nơi chạy backend cũ và kiểm tra trên bản backup restore trước khi migrate. Các dòng `PUBLISHING` cũ được chuyển thành `UNKNOWN` để người vận hành đối soát trên nền tảng, tránh gửi lại ngoài ý muốn. Mã nguồn hiện đã có V6; việc sửa mã không tự áp dụng migration lên database đang chạy. Xem [hướng dẫn vận hành scheduler](../docs/SCHEDULER_RELIABILITY.md).

V7 bổ sung token khôi phục mật khẩu. V8 bổ sung theo dõi token/chi phí AI, giữ trước ngân sách ngày/tháng, công tắc runtime có audit và đồng bộ chi phí OpenAI theo project. V9 thêm số token ghi cache và đơn giá tương ứng mà không đổi dữ liệu chi phí đã có. V8/V9 không xóa hoặc sửa dữ liệu người dùng/bài đăng; tuy nhiên vẫn cần backup và kiểm tra migration trước khi triển khai. Cấu hình chi tiết, cách đặt ngưỡng và giới hạn đối soát được mô tả tại [chính sách Free/Pro và chi phí AI](../docs/AI_PLANS_AND_COST_CONTROL.md). V8/V9 chưa được xác minh trên database production hoặc database local đang vận hành.

Chạy backend từ thư mục `backend` để Spring đọc đúng `backend/.env`, dùng JDK 21 theo `pom.xml`:

```powershell
Set-Location backend
mvn spring-boot:run
```

Khi dùng IntelliJ, chọn JDK 21 cho `PostivaApplication` và Working directory là thư mục `backend`. Nếu terminal báo lỗi compiler với JDK khác, đặt `JAVA_HOME` trỏ tới JDK 21 trước khi gọi Maven.

## Trạng thái tài khoản và nhật ký

- `users.status`: `ACTIVE` hoặc `LOCKED`.
- Khi khóa: bắt buộc có `locked_at` (UTC), `locked_by` và `lock_reason` từ 1–1000 ký tự. Khi mở: xóa metadata khóa hiện tại, giữ sự kiện lịch sử.
- `UserAccountStatusService` kiểm tra admin, khóa hàng người dùng khi chuyển trạng thái, lưu thay đổi và audit cùng transaction. Gửi lại yêu cầu khi trạng thái đã đúng không ghi trùng sự kiện hoặc đổi lý do gốc.
- `admin_audit_logs` lưu actor, target, hành động, trạng thái trước/sau, lý do và thời điểm. Không cấp phương thức sửa/xóa qua repository; database chặn UPDATE/DELETE/TRUNCATE, không cascade xóa log theo tài khoản.
- Tài khoản khóa bị chặn ở đăng nhập/JWT và CurrentUserService. Lịch chưa gửi bị hủy với `ACCOUNT_LOCKED`, scheduler cũng kiểm tra trước khi xuất bản. Mở khóa không tự đăng bù. Yêu cầu đã gửi sang nền tảng trước khi khóa vẫn có thể hoàn tất.
- API và giao diện admin đã được nối vào service; xem [hướng dẫn quản trị](../docs/ADMIN_USER_MANAGEMENT.md). Không khóa/mở bằng SQL vận hành thường xuyên vì sẽ bỏ qua audit và hủy lịch.

## Kiểm tra tự động

Unit/security tests chạy bằng `mvn test`. Kiểm tra migration thật được bật khi có `POSTIVA_TEST_DB_URL`, chỉ chấp nhận database **local** tên `postiva_migration_test`; không đọc `.env` của ứng dụng. Mỗi bài kiểm tra tạo schema riêng và xóa đúng schema thử đó sau khi xong.

```powershell
$env:POSTIVA_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:16543/postiva_migration_test'
$env:POSTIVA_TEST_DB_USERNAME = 'postiva_migration_test'
# POSTIVA_TEST_DB_PASSWORD được cung cấp qua môi trường nếu server thử yêu cầu.
mvn test
```

Các trường hợp được kiểm tra: database mới; schema có dữ liệu nhưng không có history bị từ chối; baseline rõ ràng rồi nâng cấp; giữ nguyên dữ liệu; chạy lại không reset tài khoản khóa; Hibernate validate toàn bộ entity; constraint và tính bất biến của audit; đăng nhập/JWT và scheduler với tài khoản khóa. Kiểm thử scheduler nhiều worker và phục hồi lease dùng cùng biến môi trường, chỉ trên database test chuyên dụng.

Stack hiện giữ Flyway 10.10.0 do Spring Boot 3.3.5 quản lý và PostgreSQL 16 theo Docker Compose. Bộ kiểm tra local có thể chạy PostgreSQL 18 nhưng Flyway này cảnh báo PostgreSQL mới hơn phạm vi hỗ trợ đã công bố; đối chiếu phiên bản server production và kế hoạch nâng dependency trước phát hành nếu dùng PostgreSQL mới hơn 16.

Tham chiếu: [Flyway baseline](https://documentation.red-gate.com/flyway/reference/commands/baseline), [baseline-on-migrate và lưu ý an toàn](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting), [Spring Boot 3.3 và module PostgreSQL của Flyway](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.3-Release-Notes).
