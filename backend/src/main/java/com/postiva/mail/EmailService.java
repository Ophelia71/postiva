package com.postiva.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailService {

    private static final Logger log =
            LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String username;
    private final String password;
    private final String from;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${postiva.mail.enabled:false}") boolean enabled,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${postiva.mail.from}") String from
    ) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.from = from;
    }

    public void sendRegistrationSuccess(
            String recipient,
            String fullName
    ) {
        String content = """
                Xin chào %s,

                Tài khoản Postiva của bạn đã được tạo thành công.

                Bạn có thể đăng nhập và bắt đầu sử dụng Postiva.

                Trân trọng,
                Postiva
                """.formatted(fullName);

        sendEmail(
                recipient,
                "Chào mừng bạn đến với Postiva",
                content
        );
    }

    public void sendRegistrationOtp(
            String recipient,
            String fullName,
            String code,
            long expiresInMinutes
    ) {
        if (!enabled || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "Email chưa được cấu hình. Hãy bật POSTIVA_MAIL_ENABLED và cấu hình MAIL_USERNAME/MAIL_PASSWORD."
            );
        }

        String content = """
                Xin chào %s,

                Mã xác thực đăng ký Postiva của bạn là: %s

                Mã này có hiệu lực trong %d phút. Nếu bạn không yêu cầu đăng ký tài khoản, vui lòng bỏ qua email này.

                Trân trọng,
                Postiva
                """.formatted(fullName, code, expiresInMinutes);

        sendEmail(
                recipient,
                "Mã xác thực đăng ký Postiva",
                content
        );
    }

    public void sendPasswordResetLink(String recipient, String resetUrl, long expiresInMinutes) {
        requireConfiguredMail();
        String content = """
                Bạn vừa yêu cầu đặt lại mật khẩu Postiva.

                Mở liên kết sau để tạo mật khẩu mới:
                %s

                Liên kết có hiệu lực trong %d phút và chỉ sử dụng được một lần. Nếu bạn không yêu cầu, hãy bỏ qua email này.

                Trân trọng,
                Postiva
                """.formatted(resetUrl, expiresInMinutes);
        sendEmail(recipient, "Hướng dẫn đặt lại mật khẩu Postiva", content);
    }

    /** Tells the owner that a daily draft is waiting for review. Sent only when mail is configured. */
    public void sendDailyDraftReady(String recipient, String fullName, String ruleName, String plannedAt, String reviewUrl) {
        String link = StringUtils.hasText(reviewUrl) ? "\n\nMở Daily để xem và duyệt: " + reviewUrl : "";
        String content = """
                Xin chào %s,

                Bản nháp hằng ngày của rule "%s" đã sẵn sàng và đang chờ bạn duyệt. Bài sẽ được đăng lúc %s.

                Nếu bạn không duyệt trước giờ đăng, ngày hôm nay sẽ được bỏ qua.%s

                Trân trọng,
                Postiva
                """.formatted(fullName, ruleName, plannedAt, link);
        sendEmail(recipient, "Bản nháp Daily đang chờ bạn duyệt", content);
    }

    private void requireConfiguredMail() {
        if (!enabled || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException("Dịch vụ email khôi phục chưa được cấu hình");
        }
    }

    public void sendLoginNotification(
            String recipient,
            String fullName
    ) {
        String content = """
                Xin chào %s,

                Tài khoản Postiva của bạn vừa được đăng nhập.

                Nếu đây không phải là bạn, hãy thay đổi mật khẩu ngay.

                Trân trọng,
                Postiva
                """.formatted(fullName);

        sendEmail(
                recipient,
                "Thông báo đăng nhập Postiva",
                content
        );
    }

    private void sendEmail(
            String recipient,
            String subject,
            String content
    ) {
        if (!enabled) {
            log.debug(
                    "Bỏ qua gửi email tới {} vì postiva.mail.enabled=false",
                    recipient
            );
            return;
        }

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn(
                    "Bỏ qua gửi email tới {} vì thiếu MAIL_USERNAME hoặc MAIL_PASSWORD",
                    recipient
            );
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(content);

        mailSender.send(message);
    }
}
