package com.postiva.auth.service;

import com.postiva.auth.repository.PasswordResetTokenRepository;
import com.postiva.mail.EmailService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetEmailDispatcherTest {
    @Test
    void failedDeliveryInvalidatesOnlyTheMatchingResetToken() {
        EmailService email = mock(EmailService.class);
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        doThrow(new IllegalStateException("mail failure")).when(email)
                .sendPasswordResetLink(anyString(), anyString(), anyLong());
        PasswordResetEmailDispatcher dispatcher = new PasswordResetEmailDispatcher(email, tokens);

        assertDoesNotThrow(() -> dispatcher.send("user@example.test", "https://example.test/reset#token=secret", "hash-only", 30));

        verify(tokens).invalidateTokenHash(eq("hash-only"), any());
    }
}
