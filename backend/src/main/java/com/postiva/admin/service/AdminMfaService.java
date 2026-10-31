package com.postiva.admin.service;

import com.postiva.admin.entity.AdminMfaState;
import com.postiva.admin.entity.AdminSecurityEvent;
import com.postiva.admin.entity.AdminSecurityEvent.Action;
import com.postiva.admin.repository.AdminMfaRepository;
import com.postiva.admin.repository.AdminSecurityEventRepository;
import com.postiva.admin.security.MfaSecretCipher;
import com.postiva.admin.security.Totp;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.RateLimitException;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminMfaService {
    private final UserRepository users;
    private final AdminMfaRepository states;
    private final AdminSecurityEventRepository events;
    private final CurrentUserService current;
    private final PasswordEncoder passwords;
    private final MfaSecretCipher cipher;
    private final Totp totp;
    /** Name shown in the authenticator app. Give each environment its own so codes are not taken from the wrong entry. */
    private final String issuer;
    private final SecureRandom random = new SecureRandom();

    public AdminMfaService(UserRepository users, AdminMfaRepository states,
                           AdminSecurityEventRepository events, CurrentUserService current,
                           PasswordEncoder passwords, MfaSecretCipher cipher, Totp totp,
                           @Value("${postiva.security.mfa-issuer:Postiva}") String issuer) {
        this.users = users; this.states = states; this.events = events; this.current = current;
        this.passwords = passwords; this.cipher = cipher; this.totp = totp;
        this.issuer = issuer == null || issuer.isBlank() ? "Postiva" : issuer.trim();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Status status() {
        User admin = current.requireCurrentUser();
        requireAdmin(admin);
        AdminMfaState state = states.findById(admin.getId()).orElse(null);
        return new Status(admin.isMfaEnabled(), cipher.isConfigured(),
                state == null ? 0 : state.getRecoveryHashes().size());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    @PreAuthorize("hasRole('ADMIN')")
    public Setup setup(String password) {
        User admin = lockedCurrent();
        AdminMfaState state = state(admin);
        checkPassword(admin, state, password);
        if (admin.isMfaEnabled()) throw error(HttpStatus.CONFLICT, "MFA đã được bật");
        String secret = totp.newSecret();
        state.setSecretEncrypted(cipher.encrypt(admin.getId(), secret));
        state.setSetupExpiresAt(Instant.now().plusSeconds(600));
        state.setLastUsedStep(-1);
        resetFailures(state);
        String label = uriEncode(issuer + ":" + admin.getEmail());
        return new Setup(secret, "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=" + uriEncode(issuer) + "&algorithm=SHA1&digits=6&period=30", state.getSetupExpiresAt());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    @PreAuthorize("hasRole('ADMIN')")
    public RecoveryCodes confirm(String code) {
        User admin = lockedCurrent();
        AdminMfaState state = state(admin);
        checkBlocked(state);
        if (admin.isMfaEnabled()) throw error(HttpStatus.CONFLICT, "MFA đã được bật");
        if (state.getSecretEncrypted() == null || state.getSetupExpiresAt() == null
                || !state.getSetupExpiresAt().isAfter(Instant.now())) {
            throw error(HttpStatus.CONFLICT, "Thiết lập đã hết hạn; tạo khóa mới để tiếp tục");
        }
        verify(admin, state, code, false, HttpStatus.UNPROCESSABLE_ENTITY);
        admin.setMfaEnabled(true);
        admin.incrementAuthVersion();
        state.setSetupExpiresAt(null);
        RecoveryCodes codes = newRecoveryCodes(state);
        event(admin, Action.MFA_ENABLED);
        return codes;
    }

    /** Called only after password authentication. Own transaction commits failed attempt counters. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public VerifiedLogin verifyLogin(UUID userId, String password, String code) {
        User admin = users.findByIdForUpdate(userId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại"));
        if (!admin.isActive() || admin.getRole() != User.Role.ADMIN) {
            throw error(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác");
        }
        AdminMfaState state = state(admin);
        checkLoginBlocked(state);
        if (password == null || !passwords.matches(password, admin.getPasswordHash())) {
            if (recordFailedAttempt(admin, state)) throw loginBlocked(state);
            throw error(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác");
        }
        if (!admin.isMfaEnabled()) { resetFailures(state); return new VerifiedLogin(admin, false); }
        if (code == null || code.isBlank()) throw error(HttpStatus.PRECONDITION_REQUIRED, "Nhập mã từ ứng dụng xác thực hoặc mã khôi phục");
        verify(admin, state, code, true, HttpStatus.UNAUTHORIZED);
        return new VerifiedLogin(admin, true);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    @PreAuthorize("hasRole('ADMIN')")
    public RecoveryCodes regenerate(String password, String code) {
        User admin = lockedCurrent();
        AdminMfaState state = state(admin);
        checkPassword(admin, state, password);
        if (!admin.isMfaEnabled()) throw error(HttpStatus.CONFLICT, "MFA chưa được bật");
        verify(admin, state, code, true, HttpStatus.UNPROCESSABLE_ENTITY);
        RecoveryCodes codes = newRecoveryCodes(state);
        admin.incrementAuthVersion();
        event(admin, Action.MFA_RECOVERY_REGENERATED);
        return codes;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    @PreAuthorize("hasRole('ADMIN')")
    public void disable(String password, String code) {
        User admin = lockedCurrent();
        AdminMfaState state = state(admin);
        checkPassword(admin, state, password);
        if (!admin.isMfaEnabled()) throw error(HttpStatus.CONFLICT, "MFA chưa được bật");
        verify(admin, state, code, true, HttpStatus.UNPROCESSABLE_ENTITY);
        admin.setMfaEnabled(false);
        admin.incrementAuthVersion();
        state.setSecretEncrypted(null);
        state.setSetupExpiresAt(null);
        state.setLastUsedStep(-1);
        state.setRecoveryHashes(List.of());
        event(admin, Action.MFA_DISABLED);
    }

    private void verify(User admin, AdminMfaState state, String supplied, boolean allowRecovery, HttpStatus invalidStatus) {
        if (invalidStatus == HttpStatus.UNAUTHORIZED) checkLoginBlocked(state);
        else checkBlocked(state);
        String code = supplied == null ? "" : supplied.trim();
        if (allowRecovery && code.matches("(?i)[0-9a-f\\-]{32,39}")) {
            String hash = recoveryHash(code);
            List<String> hashes = new ArrayList<>(state.getRecoveryHashes());
            int index = -1;
            for (int i = 0; i < hashes.size(); i++) {
                if (MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),
                        hashes.get(i).getBytes(StandardCharsets.US_ASCII))) index = i;
            }
            if (index >= 0) {
                hashes.remove(index);
                state.setRecoveryHashes(hashes);
                resetFailures(state);
                admin.incrementAuthVersion();
                event(admin, Action.MFA_RECOVERY_USED);
                return;
            }
        } else if (code.matches("[0-9]{6}")) {
            if (state.getSecretEncrypted() == null) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Dữ liệu MFA không khả dụng; liên hệ người vận hành");
            long step = totp.match(cipher.decrypt(admin.getId(), state.getSecretEncrypted()), code,
                    state.getLastUsedStep(), Instant.now());
            if (step >= 0) { state.setLastUsedStep(step); resetFailures(state); return; }
        }
        boolean blocked = recordFailedAttempt(admin, state);
        if (blocked && invalidStatus == HttpStatus.UNAUTHORIZED) throw loginBlocked(state);
        if (blocked) throw error(HttpStatus.TOO_MANY_REQUESTS, "Đã nhập sai nhiều lần; thử lại sau 5 phút");
        throw error(invalidStatus, "Mã không đúng. Hãy lấy mã mới từ đúng mục " + issuer
                + " trong ứng dụng xác thực, hoặc dùng một mã khôi phục.");
    }

    private User lockedCurrent() {
        User actor = current.requireCurrentUser();
        requireAdmin(actor);
        User locked = users.findByIdForUpdate(actor.getId()).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại"));
        requireAdmin(locked);
        return locked;
    }

    private void requireAdmin(User user) {
        if (!user.isActive() || user.getRole() != User.Role.ADMIN) throw error(HttpStatus.FORBIDDEN, "Bạn không có quyền quản trị");
    }

    private AdminMfaState state(User admin) {
        // Every write holds the users row lock, including lazy initialization.
        return states.findById(admin.getId()).orElseGet(() -> states.save(new AdminMfaState(admin.getId())));
    }

    private void checkPassword(User admin, AdminMfaState state, String password) {
        checkBlocked(state);
        if (password == null || !passwords.matches(password, admin.getPasswordHash())) {
            failed(admin, state);
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Mật khẩu hiện tại không đúng");
        }
    }

    private void checkBlocked(AdminMfaState state) {
        if (state.getBlockedUntil() != null) {
            if (state.getBlockedUntil().isAfter(Instant.now())) throw error(HttpStatus.TOO_MANY_REQUESTS, "Đã nhập sai nhiều lần; thử lại sau 5 phút");
            resetFailures(state);
        }
    }

    private void checkLoginBlocked(AdminMfaState state) {
        if (state.getBlockedUntil() != null) {
            if (state.getBlockedUntil().isAfter(Instant.now())) throw loginBlocked(state);
            resetFailures(state);
        }
    }

    /** Says plainly that sign-in is paused; otherwise a correct code looks wrong while the block lasts. */
    private RateLimitException loginBlocked(AdminMfaState state) {
        long seconds = Duration.between(Instant.now(), state.getBlockedUntil()).toSeconds() + 1;
        return new RateLimitException("Bạn đã nhập sai nhiều lần nên đăng nhập tạm bị khóa. Thử lại sau "
                + Math.max(1, (seconds + 59) / 60) + " phút.", seconds);
    }

    private static String uriEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void failed(User admin, AdminMfaState state) {
        if (recordFailedAttempt(admin, state)) {
            throw error(HttpStatus.TOO_MANY_REQUESTS, "Đã nhập sai nhiều lần; thử lại sau 5 phút");
        }
    }

    private boolean recordFailedAttempt(User admin, AdminMfaState state) {
        state.setFailedAttempts(state.getFailedAttempts() + 1);
        event(admin, Action.MFA_FAILED);
        if (state.getFailedAttempts() >= 5) {
            state.setBlockedUntil(Instant.now().plusSeconds(300));
            return true;
        }
        return false;
    }

    private void resetFailures(AdminMfaState state) { state.setFailedAttempts(0); state.setBlockedUntil(null); }

    private RecoveryCodes newRecoveryCodes(AdminMfaState state) {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[16]; random.nextBytes(bytes);
            String hex = HexFormat.of().withUpperCase().formatHex(bytes);
            codes.add(String.join("-", hex.substring(0, 8), hex.substring(8, 16), hex.substring(16, 24), hex.substring(24)));
        }
        state.setRecoveryHashes(codes.stream().map(this::recoveryHash).toList());
        return new RecoveryCodes(codes);
    }

    private String recoveryHash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    code.replace("-", "").toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) { throw new IllegalStateException("Không thể xử lý mã khôi phục"); }
    }

    private void event(User admin, Action action) { events.save(new AdminSecurityEvent(admin.getId(), action)); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    public record Status(boolean enabled, boolean configured, int recoveryCodesRemaining) { }
    public record Setup(String secret, String otpauthUri, Instant expiresAt) { }
    public record RecoveryCodes(List<String> recoveryCodes) { }
    public record VerifiedLogin(User user, boolean verified) { }
}
