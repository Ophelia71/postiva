package com.postiva.auth.service;

import com.postiva.auth.entity.PasswordResetToken;
import com.postiva.auth.repository.PasswordResetTokenRepository;
import com.postiva.auth.security.AuthRequestGuard;
import com.postiva.auth.security.PersistentRateLimiter;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class PasswordRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID_LINK_MESSAGE = "Liên kết khôi phục không hợp lệ hoặc đã hết hạn";

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwords;
    private final PasswordResetEmailDispatcher emailDispatcher;
    private final AuthRequestGuard guard;
    private final CurrentUserService currentUser;
    private final TransactionTemplate transactions;
    private final String frontendOrigin;

    public PasswordRecoveryService(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwords,
            PasswordResetEmailDispatcher emailDispatcher,
            AuthRequestGuard guard,
            CurrentUserService currentUser,
            PlatformTransactionManager transactionManager,
            @Value("${postiva.frontend-origin}") String frontendOrigin
    ) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwords = passwords;
        this.emailDispatcher = emailDispatcher;
        this.guard = guard;
        this.currentUser = currentUser;
        this.transactions = new TransactionTemplate(transactionManager);
        this.frontendOrigin = frontendOrigin.replaceAll("/+$", "");
    }

    /** Always returns the same public response; unknown, locked, and admin accounts receive no mail. */
    public void requestPasswordReset(String emailAddress) {
        String normalizedEmail = normalizeEmail(emailAddress);
        guard.passwordResetRequest(normalizedEmail);
        String rawToken = newToken();
        String tokenHash = PersistentRateLimiter.sha256(rawToken);

        ResetDelivery delivery;
        try {
            delivery = transactions.execute(status -> {
                User found = users.findByEmailIgnoreCase(normalizedEmail).orElse(null);
                if (found == null || found.getRole() != User.Role.USER || !found.isActive()) return null;

                User locked = users.findByIdForUpdate(found.getId()).orElse(null);
                if (locked == null || locked.getRole() != User.Role.USER || !locked.isActive()) return null;

                Instant now = Instant.now();
                resetTokens.invalidateActiveForUser(locked.getId(), now);
                resetTokens.save(new PasswordResetToken(locked.getId(), tokenHash, now.plus(TOKEN_TTL)));
                return new ResetDelivery(locked.getEmail());
            });
        } catch (RuntimeException exception) {
            // Keep storage failures indistinguishable from missing accounts at the public endpoint.
            log.warn("Password recovery request could not be prepared");
            return;
        }

        if (delivery == null) return;
        String resetUrl = frontendOrigin + "/reset-password#token=" + rawToken;
        try {
            emailDispatcher.send(delivery.email(), resetUrl, tokenHash, TOKEN_TTL.toMinutes());
        } catch (Exception exception) {
            try {
                resetTokens.invalidateTokenHash(tokenHash, Instant.now());
            } catch (Exception ignored) {
                // Keep the public response identical even if the mail queue and token cleanup are unavailable.
            }
            // Do not log the address, token, or exception details.
            log.warn("Password recovery email could not be queued");
        }
    }

    public void resetPassword(String rawToken, String newPassword) {
        validatePassword(newPassword);
        if (rawToken == null || rawToken.isBlank()) throw invalidLink();
        String tokenHash = PersistentRateLimiter.sha256(rawToken);
        guard.passwordResetAttempt(tokenHash);
        UUID userId = resetTokens.findUserIdByTokenHash(tokenHash).orElseThrow(this::invalidLink);

        transactions.executeWithoutResult(status -> {
            User account = users.findByIdForUpdate(userId).orElseThrow(this::invalidLink);
            PasswordResetToken resetToken = resetTokens.findByTokenHash(tokenHash).orElseThrow(this::invalidLink);
            Instant now = Instant.now();
            if (!resetToken.getUserId().equals(userId)
                    || resetToken.getUsedAt() != null
                    || !resetToken.getExpiresAt().isAfter(now)
                    || account.getRole() != User.Role.USER
                    || !account.isActive()) {
                throw invalidLink();
            }
            if (passwords.matches(newPassword, account.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại");
            }
            account.setPasswordHash(passwords.encode(newPassword));
            account.incrementAuthVersion();
            resetTokens.invalidateActiveForUser(userId, now);
        });
    }

    public void changePassword(String currentPassword, String newPassword) {
        validatePassword(newPassword);
        User authenticated = currentUser.requireCurrentUser();
        if (authenticated.getRole() != User.Role.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quản trị viên đổi mật khẩu tại mục Bảo mật quản trị");
        }

        transactions.executeWithoutResult(status -> {
            User account = users.findByIdForUpdate(authenticated.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại"));
            if (account.getRole() != User.Role.USER || !account.isActive()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không thể đổi mật khẩu");
            }
            if (currentPassword == null || !passwords.matches(currentPassword, account.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không chính xác");
            }
            if (passwords.matches(newPassword, account.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại");
            }
            account.setPasswordHash(passwords.encode(newPassword));
            account.incrementAuthVersion();
            resetTokens.invalidateActiveForUser(account.getId(), Instant.now());
        });
    }

    public void revokeSessions(String currentPassword) {
        User authenticated = currentUser.requireCurrentUser();
        if (authenticated.getRole() != User.Role.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quản trị viên quản lý phiên tại mục Bảo mật quản trị");
        }
        transactions.executeWithoutResult(status -> {
            User account = users.findByIdForUpdate(authenticated.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại"));
            if (account.getRole() != User.Role.USER || !account.isActive()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không thể thu hồi phiên");
            }
            if (currentPassword == null || !passwords.matches(currentPassword, account.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không chính xác");
            }
            account.incrementAuthVersion();
        });
    }

    @Scheduled(fixedDelay = 86_400_000, initialDelay = 86_400_000)
    public void purgeOldResetTokens() {
        resetTokens.purgeOldTokens(Instant.now().minus(Duration.ofDays(7)));
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Mật khẩu cần ít nhất 8 ký tự và không quá dài.");
        }
    }

    private String normalizeEmail(String emailAddress) {
        return emailAddress == null ? "" : emailAddress.trim().toLowerCase(Locale.ROOT);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ResponseStatusException invalidLink() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_LINK_MESSAGE);
    }

    private record ResetDelivery(String email) { }

}
