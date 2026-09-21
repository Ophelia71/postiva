package com.postiva.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${postiva.mail.from}") String from
    ) {
        this.mailSender = mailSender;
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
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(content);

        mailSender.send(message);
    }
}
