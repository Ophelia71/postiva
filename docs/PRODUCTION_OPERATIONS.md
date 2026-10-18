# Chuẩn bị vận hành production

Tài liệu này mô tả những gì repository đã chuẩn bị và các bước còn cần làm trên hạ tầng thật. Chưa có quyền truy cập máy chủ, DNS, Docker Hub, email provider hay tài khoản monitoring trong phiên làm việc này; vì vậy TLS đang cấp, backup từ xa và cảnh báo thật chưa được xác nhận.

## Môi trường và cấu hình

Backend có ba cấu hình Spring: `dev` được chọn mặc định khi chạy local; `staging` và `prod` phải được bật tường minh bằng `SPRING_PROFILES_ACTIVE`. Compose production đặt `prod`. Hai profile triển khai yêu cầu khai báo DB, origin, URL media/Meta, mail và các khóa; ứng dụng dừng ngay khi khóa ngắn hơn 32 byte, origin không dùng HTTPS, thiếu proxy tin cậy, bật AI nhưng thiếu OpenAI key, hoặc khai báo CORS wildcard.

Tạo `.env` trên máy triển khai từ [`ops/production.env.example`](../ops/production.env.example), điền giá trị thật bằng secret manager hoặc file quyền `0600`, rồi đặt `POSTIVA_IMAGE_TAG` khi chạy Compose thủ công. Không đưa `.env` vào Git, image, log, ticket hay lệnh được lưu vào shell history. Jenkins giữ Docker Hub credential và SSH key trong Jenkins Credentials; cần tạo credential `dockerhub-credentials` và file credential `postiva-production-ssh-key` trước khi chạy pipeline.

Giữ các khóa ổn định qua lần deploy và sao lưu chúng riêng, được mã hóa, ngoài máy chủ:

- `POSTIVA_TOKEN_ENCRYPTION_KEY` giải mã token Page đã lưu; mất khóa thì token phải được nhập lại.
- `POSTIVA_MFA_ENCRYPTION_KEY` giải mã MFA admin; mất khóa thì cần quy trình khôi phục MFA.
- `POSTIVA_MEDIA_SIGNING_SECRET` ký link ảnh đã gửi Meta; đổi khóa làm link cũ mất hiệu lực.
- `POSTIVA_JWT_SECRET` làm phiên JWT hiện tại mất hiệu lực nếu thay.

Không dùng chung bốn khóa. Việc đổi khóa phải có lịch chuyển đổi, kiểm tra giải mã và phương án phục hồi; code hiện chưa có key rotation tự động. Mật khẩu DB, OpenAI, Meta, PayOS và SMTP cũng phải nằm trong secret store, không nằm trong backup code hay image.

Staging phải có database, domain, callback, email sender và token riêng; tuyệt đối không dùng dữ liệu người dùng production. Chạy migration và toàn bộ luồng đăng nhập, khôi phục mật khẩu, upload, lập lịch, đăng thử, thanh toán (nếu bật) trên staging trước.

## CI, phát hành, health check và rollback

Jenkinsfile đã thêm gate theo thứ tự: `mvn verify`, `npm ci`, `npm run lint`, `npm run build`, build image, push, deploy backend, đợi readiness, deploy frontend, rồi đợi health. Tag image có dạng `<Jenkins build>-<12 ký tự commit>`; pipeline không đẩy hoặc triển khai `latest`. Không dọn image cũ sau deploy vì cần cho rollback.

Backend readiness là `/actuator/health/readiness` (chỉ bật health endpoint, không lộ chi tiết); proxy công khai đường dẫn tối giản `/_ops/ready`. `/health` kiểm tra Nginx. Health check Docker kiểm tra backend/frontend định kỳ; Compose xoay log container ở 10 MB × 5 file. Khi smoke check lỗi, pipeline thử quay lại tag backend đang chạy trước đó cho cả hai image. Nếu đây là deploy đầu tiên, không có tag trước để tự rollback.

Flyway chạy khi backend khởi động. Rollback code chỉ an toàn với migration cộng thêm, tương thích ngược; xem [`database/README.md`](../database/README.md). Không dùng rollback image để che một migration phá dữ liệu. Mỗi bản phát hành vẫn cần thử trên staging và backup trước deploy. Nếu deploy thủ công, dùng đúng cùng một tag cho backend/frontend:

```bash
POSTIVA_IMAGE_TAG='<build>-<commit>' docker compose pull backend frontend
POSTIVA_IMAGE_TAG='<build>-<commit>' docker compose up -d backend frontend
curl -fsS https://postiva.store/_ops/ready
```

## Backup database và media

[`scripts/backup-production.sh`](../scripts/backup-production.sh) dùng `pg_dump` và tar media volume `/app/uploads`, mã hóa cả hai bằng `age`, lưu manifest SHA-256 và xóa bản mã hóa quá hạn. Backup mặc định nằm ngoài repository tại `/var/backups/postiva`; script không ghi credentials vào file backup hoặc log. Cài `age` CLI trên backup host. Recipient public đặt trong `/etc/postiva/backup.env`; private identity chỉ lưu trong kho khôi phục ngoại tuyến có kiểm soát.

Thiết lập systemd timer chạy hằng ngày:

```bash
sudo install -d -m 0750 /etc/postiva
sudo install -o root -g ubuntu -m 0640 ops/postiva-backup.env.example /etc/postiva/backup.env
# Sửa backup.env, thay age recipient mẫu; xác nhận POSTIVA_BACKUP_DIR ở đĩa riêng.
sudo install -m 0644 ops/postiva-backup.service /etc/systemd/system/
sudo install -m 0644 ops/postiva-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now postiva-backup.timer
sudo systemctl start postiva-backup.service
systemctl list-timers postiva-backup.timer
journalctl -u postiva-backup.service --since today
```

File mẫu unit dùng user/group `ubuntu`/`docker` và `/home/ubuntu/postiva`, khớp đường dẫn hiện tại trong Jenkinsfile. Nếu máy triển khai khác, sửa unit trước khi enable. Sao chép các file `.age` sang object storage hoặc máy khác; bản chỉ nằm trên cùng host không chống được mất host/ransomware. Bảo vệ bucket bằng quyền tối thiểu, lifecycle phù hợp và bật versioning/object lock nếu nhà cung cấp hỗ trợ.

[`scripts/restore-drill.sh`](../scripts/restore-drill.sh) giải mã một backup, kiểm tra archive, restore vào database tên riêng, truy vấn các bảng users/schedules/media, kiểm tra số file media và tự xóa database thử khi kết thúc. Chạy hàng quý trên staging hoặc restore host riêng, dùng đúng private identity lấy từ kho recovery; không chạy drill đầu tiên ngay trên production. Ngoài script, định kỳ boot backend staging trỏ vào bản restore để kiểm tra Flyway/JPA, đăng nhập thử và mở một vài file media. Lưu ngày, backup id, thời gian phục hồi, kết quả và người thực hiện; đặt mục tiêu RPO/RTO trước khi mở rộng pilot.

## Log, monitoring và cảnh báo

Backend đã ghi cảnh báo retry/đăng lỗi/đăng trạng thái chưa rõ; billing ghi link thanh toán cần đối soát, lỗi webhook grant và retry quyền lợi; AI ghi khi chi phí chạm ngưỡng cấu hình hoặc kết quả provider chưa rõ. Chi tiết lỗi bài đăng có thể xem trong publish log/admin; không đưa token vào log. Những log này cần được chuyển ra khỏi host bằng CloudWatch, Grafana Loki hoặc dịch vụ tương đương có kiểm soát truy cập và thời hạn lưu.

Tạo monitor HTTPS từ bên ngoài cho `https://postiva.store/health` và `https://postiva.store/_ops/ready`, đồng thời cảnh báo từ Docker health status, lỗi deploy, timer backup thất bại, tuổi backup cuối, dung lượng đĩa, lỗi publish `FAILED/UNKNOWN`, đơn thanh toán cần review/quyền lợi chưa cấp, và chi phí AI ngày/tháng. Ngưỡng khởi điểm: backup cũ hơn 26 giờ = cảnh báo; đĩa trên 80% = cảnh báo, 90% = nghiêm trọng; AI đạt ngưỡng `POSTIVA_AI_BUDGET_WARNING_PERCENT` = cảnh báo; bất kỳ `UNKNOWN` hoặc entitlement lỗi nào = cần xử lý. Chọn ít nhất một kênh nhận cảnh báo có người trực và thử gửi alert trong staging.

Repository hiện chưa cấu hình vendor log/monitoring, notification destination, object storage, hay quy tắc alert từ hệ thống ngoài. Đây là phần vận hành phải hoàn tất trên tài khoản hạ tầng trước khi pilot; health endpoint và log một mình không tự gửi cảnh báo.

## Domain, TLS, proxy, email và tải lên

Nginx hiện dùng domain canonical `postiva.store`/`www.postiva.store`, ép HTTP sang HTTPS và đọc certificate từ `./certbot/conf`. Trên host thật, xác nhận DNS A/AAAA trỏ đúng, cấp/renew certificate tự động, kiểm tra chuỗi chứng thư và ngày hết hạn; không mount private key certificate vào frontend image. Endpoint API/backend không được mở port công khai ngoài Nginx.

Đặt `POSTIVA_FRONTEND_ORIGIN` đúng origin HTTPS, để `POSTIVA_CORS_ALLOWED_ORIGIN_PATTERNS` trống nếu không cần origin thứ hai; profile prod từ chối wildcard. Nginx ghi đè `X-Real-IP` và forwarded headers. Compose gán địa chỉ IP tĩnh cho Nginx và tự tạo `POSTIVA_TRUSTED_PROXY_ADDRESSES` từ IP đó; kiểm tra subnet mẫu `172.28.240.0/24` không trùng route/VPC trên host trước khi dùng. Xác nhận backend nhìn thấy đúng peer IP của Nginx. Nếu đặt CDN/load balancer phía trước, cấu hình thêm real-IP/proxy chain tại Nginx và chỉ tin các dải proxy do nhà cung cấp công bố. Không đặt `0.0.0.0/0`; giữ backend trên network nội bộ Compose. Việc chuyển network trên máy đã chạy có thể làm container khởi động lại, nên làm trong cửa sổ deploy sau khi backup.

Nginx giới hạn body 12 MB; Spring giới hạn file 10 MB và request 11 MB. Đồng bộ giới hạn ở load balancer/CDN, thử upload sát giới hạn và kiểm tra dọn file mồ côi. Email production cần SMTP thật với TLS, tài khoản gửi chuyên dụng và kiểm thử OTP/đặt lại mật khẩu; địa chỉ sender phải được xác minh với provider.

## Trước khi mở pilot

- Staging đã qua CI, migrations, luồng đăng nhập/reset, upload, lập lịch, đăng Meta bằng Page token và thanh toán end-to-end nếu gói Pro được mở.
- Đã xác nhận DNS/TLS/renew, CORS origin, IP proxy tin cậy, SMTP thật và không lộ port backend.
- Production secret đã có trong secret manager/file quyền chặt; backup age recipient/identity đã diễn tập khôi phục.
- Backup tự chạy hằng ngày và được sao chép ra host/provider độc lập; restore drill đạt mục tiêu RPO/RTO.
- Health/log monitor và kênh cảnh báo đã được cấu hình, cảnh báo thử đã tới đúng người trực.
- Đã ghi nhận tag deploy, migration, backup gần nhất, người on-call và lệnh rollback.

Chỉ thay đổi mã trong workspace đã được kiểm tra. Chưa xác minh máy chủ production, domain/certificate, SMTP thật, Docker Hub credential, backup ngoài máy, hay alert destination.
