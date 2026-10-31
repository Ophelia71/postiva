package com.postiva.mail;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EmailServiceTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);

    @Test
    void localLogModeAcceptsCodesWithoutSendingMail() {
        EmailService service = new EmailService(sender, false, "", "", "no-reply@example.test", true);

        assertDoesNotThrow(() -> service.sendRegistrationOtp("user@example.test", "User", "123456", 10));
        assertDoesNotThrow(() -> service.sendPasswordResetLink("user@example.test", "http://localhost/reset", 30));
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void logModeIsIgnoredWhileRealMailIsEnabled() {
        EmailService service = new EmailService(sender, true, "sender@example.test", "app-password", "no-reply@example.test", true);

        service.sendRegistrationOtp("user@example.test", "User", "123456", 10);

        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    void withoutLogModeDisabledMailStillRefusesToIssueCodes() {
        EmailService service = new EmailService(sender, false, "", "", "no-reply@example.test", false);

        assertThrows(IllegalStateException.class, () -> service.sendRegistrationOtp("user@example.test", "User", "123456", 10));
        assertThrows(IllegalStateException.class, () -> service.sendPasswordResetLink("user@example.test", "http://localhost/reset", 30));
    }
}
