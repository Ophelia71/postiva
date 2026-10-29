package com.postiva.user.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.file.storage.LocalFileStorage;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * A user deleting their own account. Everything they made or connected is removed: channels and tokens, posts,
 * schedules, engagement, weekly plans, uploaded images, the shop profile and AI prompts. Payment orders and AI usage
 * counts stay for accounting, pointing at an anonymized user row that can never sign in again.
 */
@Service
public class AccountDeletionService {
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CurrentUserService currentUser;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final LocalFileStorage storage;
    private final TransactionTemplate transactions;

    public AccountDeletionService(CurrentUserService currentUser,
                                  UserRepository users,
                                  PasswordEncoder passwords,
                                  JdbcTemplate jdbc,
                                  LocalFileStorage storage,
                                  PlatformTransactionManager transactionManager) {
        this.currentUser = currentUser;
        this.users = users;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void deleteCurrentAccount(String currentPassword) {
        User authenticated = currentUser.requireCurrentUser();
        if (authenticated.getRole() != User.Role.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản quản trị không thể tự xóa");
        }
        List<String> storedFiles = transactions.execute(status -> deleteInTransaction(authenticated.getId(), currentPassword));
        // Image files go only after the rows are gone, so a rollback never leaves posts pointing at missing files.
        for (String storedName : storedFiles) {
            try {
                storage.delete(storedName);
            } catch (RuntimeException exception) {
                log.warn("Could not remove an uploaded file of a deleted account. file={}", storedName, exception);
            }
        }
    }

    private List<String> deleteInTransaction(UUID userId, String currentPassword) {
        User account = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại"));
        if (account.getRole() != User.Role.USER || !account.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không thể xóa");
        }
        if (currentPassword == null || !passwords.matches(currentPassword, account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không chính xác");
        }
        refuseWhileBusy(userId);

        String posts = "SELECT id FROM generated_posts WHERE user_id = ?";
        String briefs = "SELECT id FROM product_briefs WHERE user_id = ?";
        String files = "uploaded_files WHERE user_id = ? OR post_id IN (" + posts + ") OR brief_id IN (" + briefs + ")";
        List<String> storedFiles = jdbc.queryForList(
                "SELECT public_id FROM " + files + " AND storage_provider = 'LOCAL' AND public_id IS NOT NULL",
                String.class, userId, userId, userId);
        jdbc.update("DELETE FROM " + files, userId, userId, userId);
        // Weekly plans take their items; schedules take their publish logs and engagement (ON DELETE CASCADE).
        jdbc.update("DELETE FROM content_calendars WHERE user_id = ?", userId);
        jdbc.update("""
                DELETE FROM scheduled_posts WHERE post_id IN (SELECT id FROM generated_posts WHERE user_id = ?)
                OR social_account_id IN (SELECT id FROM social_accounts WHERE user_id = ?)
                """, userId, userId);
        jdbc.update("DELETE FROM ai_generation_logs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM generated_posts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM product_briefs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM social_accounts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM business_profiles WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM pending_registrations WHERE lower(email) = lower(?)", account.getEmail());
        jdbc.update("UPDATE web_analytics_sessions SET user_id = NULL WHERE user_id = ?", userId);

        account.anonymizeAfterDeletion(passwords.encode(randomSecret()));
        users.saveAndFlush(account);
        return storedFiles;
    }

    private void refuseWhileBusy(UUID userId) {
        if (count("""
                SELECT count(*) FROM scheduled_posts WHERE status = 'PUBLISHING'
                AND post_id IN (SELECT id FROM generated_posts WHERE user_id = ?)
                """, userId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Postiva đang đăng bài cho bạn. Hãy thử xóa tài khoản lại sau ít phút.");
        }
        if (count("SELECT count(*) FROM payment_orders WHERE user_id = ? AND status IN ('CREATING', 'PENDING', 'REVIEW')", userId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn có đơn thanh toán đang xử lý. Hãy chờ đơn hoàn tất hoặc hết hạn rồi xóa tài khoản.");
        }
        if (count("""
                SELECT count(*) FROM payment_refunds refund JOIN payment_orders payment ON payment.id = refund.order_id
                WHERE payment.user_id = ? AND refund.status IN ('REQUESTED', 'APPROVED', 'PROCESSING')
                """, userId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn có yêu cầu hoàn tiền đang xử lý. Hãy chờ hoàn tiền xong rồi xóa tài khoản.");
        }
    }

    private int count(String sql, UUID userId) {
        Integer value = jdbc.queryForObject(sql, Integer.class, userId);
        return value == null ? 0 : value;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
