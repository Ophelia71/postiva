package com.postiva.auth.service;

import com.postiva.auth.repository.PasswordResetTokenRepository;
import com.postiva.mail.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PasswordResetEmailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailDispatcher.class);
    private final EmailService email;
    private final PasswordResetTokenRepository resetTokens;

    public PasswordResetEmailDispatcher(EmailService email, PasswordResetTokenRepository resetTokens) {
        this.email = email;
        this.resetTokens = resetTokens;
    }

    @Async("securityEmailExecutor")
    public void send(String recipient, String resetUrl, String tokenHash, long expiresInMinutes) {
        try {
            email.sendPasswordResetLink(recipient, resetUrl, expiresInMinutes);
        } catch (Exception exception) {
            try {
                resetTokens.invalidateTokenHash(tokenHash, Instant.now());
                // Do not log recipient, reset URL, token hash, or mail exception details.
                log.warn("Password recovery email delivery failed; the new link was invalidated");
            } catch (Exception ignored) {
                // Do not let async exception handling log method arguments, which contain the reset URL.
                log.warn("Password recovery failed and the reset token could not be invalidated");
            }
        }
    }
}
