package com.postiva.usage;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Internal provisioning entry point for a payment webhook after payment verification. */
@Service
public class VerifiedSubscriptionPlanService {
    private final JdbcTemplate jdbc;
    private final UsageProperties properties;

    public VerifiedSubscriptionPlanService(JdbcTemplate jdbc, UsageProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional
    public UUID activateProFromVerifiedPayment(UUID userId, Instant startsAt, Instant endsAt, String paymentReference) {
        return activateProFromVerifiedPayment(userId, startsAt, endsAt, paymentReference, properties.getProAiLimit());
    }

    @Transactional
    public UUID activateProFromVerifiedPayment(UUID userId, Instant startsAt, Instant endsAt,
                                               String paymentReference, int aiLimit) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)
                || paymentReference == null || paymentReference.isBlank() || paymentReference.length() > 160 || aiLimit < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kỳ Pro hoặc mã thanh toán đã xác minh không hợp lệ.");
        }
        List<java.util.Map<String, Object>> users = jdbc.queryForList(
                "SELECT role, status FROM users WHERE id = ? FOR UPDATE", userId);
        if (users.isEmpty() || !"USER".equals(users.getFirst().get("role"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng.");
        }
        // A payment made before an account lock is still owed its purchased period.
        List<ExistingPeriod> existing = jdbc.query("SELECT id, user_id, starts_at, ends_at FROM subscription_periods WHERE source_reference = ?",
                (rs, row) -> new ExistingPeriod(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant()), paymentReference);
        if (!existing.isEmpty()) {
            ExistingPeriod period = existing.getFirst();
            if (period.userId().equals(userId)
                    && Math.abs(Duration.between(period.startsAt(), startsAt).toNanos()) < 1_000
                    && Math.abs(Duration.between(period.endsAt(), endsAt).toNanos()) < 1_000) return period.id();
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã thanh toán đã được dùng cho kỳ đăng ký khác.");
        }

        Integer overlaps = jdbc.queryForObject("""
                SELECT count(*) FROM subscription_periods
                WHERE user_id = ? AND plan_code = 'PRO' AND voided_at IS NULL AND starts_at < ? AND ends_at > ?
                """, Integer.class, userId, Timestamp.from(endsAt), Timestamp.from(startsAt));
        if (overlaps != null && overlaps > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kỳ Pro đã tồn tại trong khoảng thời gian này.");
        }
        // Preserve usage history while ensuring an existing Free period cannot outlive a newly paid Pro period.
        jdbc.update("""
                UPDATE subscription_periods SET ends_at = ? WHERE user_id = ? AND plan_code = 'FREE'
                AND voided_at IS NULL AND starts_at < ? AND ends_at > ?
                """, Timestamp.from(startsAt), userId, Timestamp.from(startsAt), Timestamp.from(startsAt));
        UUID periodId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO subscription_periods (id, user_id, plan_code, starts_at, ends_at, ai_limit, source_reference)
                VALUES (?, ?, 'PRO', ?, ?, ?, ?)
                """, periodId, userId, Timestamp.from(startsAt), Timestamp.from(endsAt), aiLimit, paymentReference);
        return periodId;
    }

    private record ExistingPeriod(UUID id, UUID userId, Instant startsAt, Instant endsAt) { }
}
