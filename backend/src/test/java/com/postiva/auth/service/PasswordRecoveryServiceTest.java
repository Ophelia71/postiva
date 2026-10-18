package com.postiva.auth.service;

import com.postiva.auth.entity.PasswordResetToken;
import com.postiva.auth.repository.PasswordResetTokenRepository;
import com.postiva.auth.security.AuthRequestGuard;
import com.postiva.auth.security.PersistentRateLimiter;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

class PasswordRecoveryServiceTest {
    private UserRepository users;
    private PasswordResetTokenRepository resetTokens;
    private BCryptPasswordEncoder passwords;
    private PasswordResetEmailDispatcher emailDispatcher;
    private AuthRequestGuard guard;
    private CurrentUserService currentUser;
    private PasswordRecoveryService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        resetTokens = mock(PasswordResetTokenRepository.class);
        passwords = new BCryptPasswordEncoder();
        emailDispatcher = mock(PasswordResetEmailDispatcher.class);
        guard = mock(AuthRequestGuard.class);
        currentUser = mock(CurrentUserService.class);
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new PasswordRecoveryService(users, resetTokens, passwords, emailDispatcher, guard, currentUser, tx,
                "https://postiva.example/");
        user = activeUser();
    }

    @Test
    void unknownEmailGetsSameFlowWithoutCreatingTokenOrSendingMail() {
        when(users.findByEmailIgnoreCase("missing@example.test")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.requestPasswordReset(" Missing@Example.Test "));

        verify(guard).passwordResetRequest("missing@example.test");
        verify(resetTokens, never()).save(any());
        verify(emailDispatcher, never()).send(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void resetLinkIsSentOnceAndOnlyItsHashIsStored() {
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(resetTokens.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestPasswordReset(user.getEmail());

        var saved = org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(saved.capture());
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        var dispatchedHash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailDispatcher).send(eq(user.getEmail()), link.capture(), dispatchedHash.capture(), eq(30L));
        String rawToken = link.getValue().substring(link.getValue().indexOf("#token=") + 7);
        assertTrue(link.getValue().startsWith("https://postiva.example/reset-password#token="));
        assertEquals(PersistentRateLimiter.sha256(rawToken), saved.getValue().getTokenHash());
        assertEquals(saved.getValue().getTokenHash(), dispatchedHash.getValue());
        assertNotEquals(rawToken, saved.getValue().getTokenHash());
        assertTrue(saved.getValue().getExpiresAt().isAfter(Instant.now().plusSeconds(29 * 60L)));
    }

    @Test
    void successfulResetConsumesTokenChangesPasswordAndRevokesJwtVersion() {
        String rawToken = "single-use-secret";
        String tokenHash = PersistentRateLimiter.sha256(rawToken);
        PasswordResetToken token = new PasswordResetToken(user.getId(), tokenHash, Instant.now().plusSeconds(600));
        when(resetTokens.findUserIdByTokenHash(tokenHash)).thenReturn(Optional.of(user.getId()));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(resetTokens.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(resetTokens.invalidateActiveForUser(eq(user.getId()), any(Instant.class))).thenAnswer(invocation -> {
            token.markUsed(invocation.getArgument(1));
            return 1;
        });

        service.resetPassword(rawToken, "new secure password");

        assertEquals(1, user.getAuthVersion());
        assertTrue(passwords.matches("new secure password", user.getPasswordHash()));
        assertNotNull(token.getUsedAt());
        ResponseStatusException reused = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword(rawToken, "another secure password"));
        assertEquals(HttpStatus.BAD_REQUEST, reused.getStatusCode());
    }

    @Test
    void expiredResetTokenIsRejectedWithoutChangingPassword() {
        String rawToken = "expired-secret";
        String tokenHash = PersistentRateLimiter.sha256(rawToken);
        PasswordResetToken token = new PasswordResetToken(user.getId(), tokenHash, Instant.now().minusSeconds(1));
        String oldHash = user.getPasswordHash();
        when(resetTokens.findUserIdByTokenHash(tokenHash)).thenReturn(Optional.of(user.getId()));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(resetTokens.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword(rawToken, "new secure password"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(oldHash, user.getPasswordHash());
        assertEquals(0, user.getAuthVersion());
    }

    @Test
    void changePasswordVerifiesCurrentPasswordAndInvalidatesEverySession() {
        when(currentUser.requireCurrentUser()).thenReturn(user);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        service.changePassword("old secure password", "brand new password");

        assertEquals(1, user.getAuthVersion());
        assertTrue(passwords.matches("brand new password", user.getPasswordHash()));
        verify(resetTokens).invalidateActiveForUser(eq(user.getId()), any(Instant.class));
    }

    @Test
    void wrongCurrentPasswordCannotChangeAccount() {
        when(currentUser.requireCurrentUser()).thenReturn(user);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.changePassword("wrong password", "brand new password"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(0, user.getAuthVersion());
        verify(resetTokens, never()).invalidateActiveForUser(any(), any());
    }

    @Test
    void revokingSessionsRequiresCurrentPasswordAndIncrementsAuthVersion() {
        when(currentUser.requireCurrentUser()).thenReturn(user);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        service.revokeSessions("old secure password");

        assertEquals(1, user.getAuthVersion());
    }

    private User activeUser() {
        User account = new User();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        account.setEmail("user@example.test");
        account.setFullName("Test user");
        account.setRole(User.Role.USER);
        account.setPasswordHash(passwords.encode("old secure password"));
        return account;
    }
}
