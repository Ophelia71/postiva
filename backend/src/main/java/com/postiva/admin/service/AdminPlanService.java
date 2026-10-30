package com.postiva.admin.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.usage.UsageProperties;
import com.postiva.usage.VerifiedSubscriptionPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pro given by an admin: support cases, gifts, or a payment received outside payOS.
 * The period is an ordinary Pro period (source ADMIN:<grant id>), so quotas work exactly as for a paid one.
 */
@Service
public class AdminPlanService {
    private static final String SOURCE_PREFIX = "ADMIN:";
    private final JdbcTemplate jdbc;
    private final VerifiedSubscriptionPlanService plans;
    private final UsageProperties usage;
    private final CurrentUserService current;

    public AdminPlanService(JdbcTemplate jdbc, VerifiedSubscriptionPlanService plans,
                            UsageProperties usage, CurrentUserService current) {
        this.jdbc = jdbc; this.plans = plans; this.usage = usage; this.current = current;
    }

    @Transactional(readOnly = true)
    public Plan plan(UUID userId) {
        if (jdbc.queryForList("SELECT 1 FROM users WHERE id = ? AND role = 'USER'", userId).isEmpty()) {
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng.");
        }
        Instant now = now();
        var active = jdbc.query("""
                SELECT plan_code, ends_at, ai_limit, ai_used + ai_reserved AS used FROM subscription_periods
                WHERE user_id = ? AND voided_at IS NULL AND starts_at <= ? AND ends_at > ?
                ORDER BY starts_at DESC LIMIT 1
                """, (rs, row) -> new Active(rs.getString("plan_code"), rs.getTimestamp("ends_at").toInstant(),
                rs.getInt("ai_limit"), rs.getInt("used")), userId, Timestamp.from(now), Timestamp.from(now));
        Instant proUntil = proUntil(userId, now);
        Integer adminGranted = jdbc.queryForObject("""
                SELECT count(*) FROM subscription_periods WHERE user_id = ? AND plan_code = 'PRO'
                AND voided_at IS NULL AND ends_at > ? AND source_reference LIKE 'ADMIN:%'
                """, Integer.class, userId, Timestamp.from(now));
        List<Grant> history = jdbc.query("""
                SELECT g.id, g.action, g.days, g.reason, g.created_at, coalesce(u.full_name, u.email) AS actor_name,
                       p.starts_at, p.ends_at, p.voided_at
                FROM admin_plan_grants g
                JOIN users u ON u.id = g.actor_user_id
                LEFT JOIN subscription_periods p ON p.id = g.period_id
                WHERE g.target_user_id = ? ORDER BY g.created_at DESC LIMIT 20
                """, (rs, row) -> new Grant(rs.getObject("id", UUID.class), rs.getString("action"),
                (Integer) rs.getObject("days"), rs.getString("reason"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("actor_name"), instant(rs.getTimestamp("starts_at")), instant(rs.getTimestamp("ends_at")),
                rs.getTimestamp("voided_at") != null), userId);
        Active period = active.isEmpty() ? null : active.getFirst();
        return new Plan(period == null ? "FREE" : period.plan(), proUntil,
                period == null ? null : period.limit(), period == null ? null : period.used(),
                adminGranted != null && adminGranted > 0, usage.getProAiLimit(), history);
    }

    @Transactional
    public Plan grantPro(UUID userId, int days, String reason) {
        if (days < 1 || days > 366) throw error(HttpStatus.BAD_REQUEST, "Số ngày Pro phải từ 1 đến 366.");
        String clean = reason(reason);
        UUID actor = current.requireCurrentUserId();
        requireUser(userId, true);
        Instant now = now();
        // An active Pro (paid or given) is extended, never overlapped.
        Instant until = proUntil(userId, now);
        Instant start = until == null ? now : until;
        UUID grantId = UUID.randomUUID();
        UUID periodId = plans.activateProFromVerifiedPayment(userId, start, start.plus(Duration.ofDays(days)),
                SOURCE_PREFIX + grantId, usage.getProAiLimit());
        jdbc.update("""
                INSERT INTO admin_plan_grants (id, actor_user_id, target_user_id, action, period_id, days, reason)
                VALUES (?, ?, ?, 'GRANT_PRO', ?, ?, ?)
                """, grantId, actor, userId, periodId, days, clean);
        return plan(userId);
    }

    /** Takes back only what admins gave; a paid period goes through the refund flow instead. */
    @Transactional
    public Plan revokePro(UUID userId, String reason) {
        String clean = reason(reason);
        UUID actor = current.requireCurrentUserId();
        requireUser(userId, false);
        int voided = jdbc.update("""
                UPDATE subscription_periods SET voided_at = clock_timestamp(), void_reason = 'ADMIN_REVOKED'
                WHERE user_id = ? AND plan_code = 'PRO' AND voided_at IS NULL AND ends_at > clock_timestamp()
                AND source_reference LIKE 'ADMIN:%'
                """, userId);
        if (voided == 0) throw error(HttpStatus.CONFLICT, "Người dùng không có Pro do quản trị viên cấp.");
        jdbc.update("""
                INSERT INTO admin_plan_grants (id, actor_user_id, target_user_id, action, reason)
                VALUES (?, ?, ?, 'REVOKE_PRO', ?)
                """, UUID.randomUUID(), actor, userId, clean);
        return plan(userId);
    }

    private void requireUser(UUID userId, boolean mustBeActive) {
        var rows = jdbc.queryForList("SELECT role, status FROM users WHERE id = ? FOR UPDATE", userId);
        if (rows.isEmpty() || !"USER".equals(rows.getFirst().get("role"))) {
            throw error(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng.");
        }
        if (mustBeActive && !"ACTIVE".equals(rows.getFirst().get("status"))) {
            throw error(HttpStatus.CONFLICT, "Tài khoản đang bị khóa. Mở khóa trước khi cấp Pro.");
        }
    }

    private Instant proUntil(UUID userId, Instant now) {
        Timestamp until = jdbc.queryForObject("""
                SELECT max(ends_at) FROM subscription_periods
                WHERE user_id = ? AND plan_code = 'PRO' AND voided_at IS NULL AND ends_at > ?
                """, Timestamp.class, userId, Timestamp.from(now));
        return instant(until);
    }

    private Instant now() { return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant(); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    private static String reason(String reason) {
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty() || clean.length() > 1000) throw error(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do (tối đa 1000 ký tự).");
        return clean;
    }

    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }

    private record Active(String plan, Instant endsAt, int limit, int used) { }

    public record Plan(String currentPlan, Instant proUntil, Integer aiLimit, Integer aiUsed,
                       boolean hasAdminGrantedPro, int proAiLimit, List<Grant> history) { }

    public record Grant(UUID id, String action, Integer days, String reason, Instant createdAt, String actorName,
                        Instant startsAt, Instant endsAt, boolean revoked) { }
}
