package com.postiva.billing;

import com.postiva.usage.VerifiedSubscriptionPlanService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Entitlement is committed separately from the receipt, so a transient grant failure never erases paid money. */
@Service
public class BillingGrantService {
    private final JdbcTemplate jdbc;
    private final VerifiedSubscriptionPlanService plans;

    public BillingGrantService(JdbcTemplate jdbc, VerifiedSubscriptionPlanService plans) {
        this.jdbc = jdbc; this.plans = plans;
    }

    @Transactional
    public void grant(UUID orderId) {
        var users = jdbc.queryForList("SELECT user_id FROM payment_orders WHERE id = ?", orderId);
        if (users.isEmpty()) return;
        UUID userId = (UUID) users.getFirst().get("user_id");
        jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", userId);
        var orders = jdbc.queryForList("SELECT * FROM payment_orders WHERE id = ? FOR UPDATE", orderId);
        if (orders.isEmpty()) return;
        var order = orders.getFirst();
        if (!"PAID".equals(order.get("status")) || "GRANTED".equals(order.get("grant_status"))) return;
        Integer refunded = jdbc.queryForObject("SELECT count(*) FROM payment_refunds WHERE order_id = ? AND status = 'SUCCEEDED'", Integer.class, orderId);
        if (refunded != null && refunded > 0) {
            jdbc.update("UPDATE payment_orders SET grant_status = 'NOT_APPLICABLE' WHERE id = ?", orderId);
            return;
        }
        String source = "PAYOS:" + order.get("order_code");
        var existing = jdbc.query("SELECT id FROM subscription_periods WHERE source_reference = ?",
                (rs, row) -> rs.getObject("id", UUID.class), source);
        UUID periodId;
        if (!existing.isEmpty()) periodId = existing.getFirst();
        else {
            Instant now = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
            Timestamp lastPro = jdbc.queryForObject("""
                    SELECT max(ends_at) FROM subscription_periods
                    WHERE user_id = ? AND plan_code = 'PRO' AND voided_at IS NULL AND ends_at > ?
                    """, Timestamp.class, userId, Timestamp.from(now));
            Instant start = lastPro == null ? now : lastPro.toInstant();
            Instant end = start.plus(Duration.ofDays(((Number) order.get("period_days")).longValue()));
            periodId = plans.activateProFromVerifiedPayment(userId, start, end, source,
                    ((Number) order.get("ai_limit")).intValue());
        }
        jdbc.update("""
                UPDATE payment_orders SET grant_status = 'GRANTED', granted_period_id = ?, updated_at = clock_timestamp()
                WHERE id = ? AND status = 'PAID'
                """, periodId, orderId);
    }
}
