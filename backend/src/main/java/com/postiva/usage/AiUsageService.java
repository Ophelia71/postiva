package com.postiva.usage;

import com.postiva.ai.dto.ProviderTokenUsage;
import com.postiva.common.RateLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AiUsageService {
    private final JdbcTemplate jdbc;
    private final UsageProperties properties;
    private final AiCostService costs;

    public AiUsageService(JdbcTemplate jdbc, UsageProperties properties, AiCostService costs) {
        this.jdbc = jdbc; this.properties = properties; this.costs = costs;
    }

    @Transactional
    public Summary summary(UUID userId) {
        lockUser(userId, false);
        Instant now = databaseNow();
        recoverExpired(userId, now);
        return currentPeriod(userId, now);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Reservation reserve(UUID userId, UUID key, String hash, String model) {
        lockUser(userId, true);
        Instant now = databaseNow();
        recoverExpired(userId, now);
        List<Usage> previous = jdbc.query("SELECT * FROM ai_usage_requests WHERE user_id = ? AND idempotency_key = ?",
                (rs, row) -> usage(rs), userId, key);
        if (!previous.isEmpty()) {
            Usage item = previous.getFirst();
            String storedHash = jdbc.queryForObject("SELECT request_hash FROM ai_usage_requests WHERE id = ?", String.class, item.id());
            if (!hash.equals(storedHash)) throw error(HttpStatus.CONFLICT, "Mã yêu cầu đã dùng với nội dung khác");
            if ("SUCCEEDED".equals(item.status())) {
                if (item.postId() == null) throw error(HttpStatus.GONE, "Bài viết của yêu cầu này đã bị xóa");
                return new Reservation(item.id(), item.postId());
            }
            if ("RESERVED".equals(item.status())) throw new AiRequestConflictException(item.status(), "Yêu cầu AI đang xử lý. Vui lòng chờ rồi thử lại.");
            throw new AiRequestConflictException(item.status(), "Yêu cầu trước đã kết thúc mà không tạo bài. Bấm tạo bài lần nữa để bắt đầu yêu cầu mới.");
        }
        costs.ensureEnabled();
        Summary period = currentPeriod(userId, now);
        Integer running = jdbc.queryForObject("SELECT count(*) FROM ai_usage_requests WHERE user_id = ? AND status = 'RESERVED'", Integer.class, userId);
        if (running != null && running > 0) throw new RateLimitException("Một yêu cầu AI khác đang xử lý. Vui lòng chờ.", 5);
        if (period.remaining() <= 0) throw new RateLimitException("Bạn đã dùng hết lượt AI trong kỳ hiện tại.", Duration.between(now, period.endsAt()).toSeconds());
        LocalDate day = now.atOffset(ZoneOffset.UTC).toLocalDate();
        Instant midnight = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        long untilTomorrow = Duration.between(now, midnight.plusSeconds(86400)).toSeconds();
        Integer attempts = jdbc.queryForObject("SELECT count(*) FROM ai_usage_requests WHERE user_id = ? AND created_at >= ?",
                Integer.class, userId, Timestamp.from(midnight));
        if (attempts != null && attempts >= properties.getUserDailyAttempts()) {
            throw new RateLimitException("Bạn đã đạt giới hạn yêu cầu AI trong ngày. Vui lòng thử lại vào ngày mai.", untilTomorrow);
        }
        if (properties.getGlobalDailyAttempts() == 0) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Tạo nội dung AI đang tạm dừng.");
        List<Integer> budget = jdbc.queryForList("""
                INSERT INTO ai_daily_budgets AS budget (usage_day, attempts) VALUES (?, 1)
                ON CONFLICT (usage_day) DO UPDATE SET attempts = budget.attempts + 1
                WHERE budget.attempts < ? RETURNING attempts
                """, Integer.class, day, properties.getGlobalDailyAttempts());
        if (budget.isEmpty()) throw new RateLimitException("Dịch vụ AI đã đạt giới hạn sử dụng hôm nay. Vui lòng thử lại vào ngày mai.", untilTomorrow);
        UUID id = UUID.randomUUID();
        var reservedCost = costs.reserveBudget(now);
        jdbc.update("UPDATE subscription_periods SET ai_reserved = ai_reserved + 1 WHERE id = ?", period.periodId());
        jdbc.update("""
                INSERT INTO ai_usage_requests (id, user_id, period_id, idempotency_key, request_hash, status, model, created_at)
                VALUES (?, ?, ?, ?, ?, 'RESERVED', ?, ?)
                """, id, userId, period.periodId(), key, hash, model, Timestamp.from(now));
        costs.createReservation(id, now, model, reservedCost);
        return new Reservation(id, null);
    }

    // Must commit with the generated post: never charge a successful quota unit without a saved result.
    @Transactional(propagation = Propagation.MANDATORY)
    public void succeed(UUID userId, UUID requestId, UUID postId) {
        lockUser(userId, true);
        Usage item = requireUsage(userId, requestId);
        if (!"RESERVED".equals(item.status())) throw error(HttpStatus.CONFLICT, "Yêu cầu AI đã kết thúc; hãy kiểm tra lịch sử sử dụng.");
        jdbc.update("UPDATE subscription_periods SET ai_reserved = ai_reserved - 1, ai_used = ai_used + 1 WHERE id = ?", item.periodId());
        jdbc.update("UPDATE ai_usage_requests SET status = 'SUCCEEDED', post_id = ?, finished_at = clock_timestamp() WHERE id = ?", postId, requestId);
    }

    @Transactional
    public void fail(UUID userId, UUID requestId, boolean uncertain, String code) {
        lockUser(userId, false);
        costs.finishFailure(requestId, uncertain);
        Usage item = requireUsage(userId, requestId);
        // A lost commit response may hide a successful transaction. Never refund it a second time.
        if (!"RESERVED".equals(item.status())) return;
        release(item, uncertain ? "UNKNOWN" : "FAILED", code);
    }

    @Transactional
    public void recordProviderUsage(UUID requestId, ProviderTokenUsage providerUsage) {
        costs.recordUsage(requestId, providerUsage);
    }

    @Transactional(readOnly = true)
    public History history(UUID userId, int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 50) throw error(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ");
        Long total = jdbc.queryForObject("SELECT count(*) FROM ai_usage_requests WHERE user_id = ?", Long.class, userId);
        List<Usage> items = jdbc.query("SELECT * FROM ai_usage_requests WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, row) -> usage(rs), userId, size, page * size);
        return new History(items, page, size, total == null ? 0 : total);
    }

    private Summary currentPeriod(UUID userId, Instant now) {
        var periods = jdbc.query("""
                SELECT * FROM subscription_periods WHERE user_id = ? AND voided_at IS NULL AND starts_at <= ? AND ends_at > ?
                ORDER BY starts_at DESC LIMIT 1
                """, (rs, row) -> summary(rs), userId, Timestamp.from(now), Timestamp.from(now));
        if (!periods.isEmpty()) return periods.getFirst();
        Timestamp lastEnd = jdbc.queryForObject("SELECT max(ends_at) FROM subscription_periods WHERE user_id = ? AND voided_at IS NULL AND ends_at <= ?",
                Timestamp.class, userId, Timestamp.from(now));
        long seconds = Duration.ofDays(properties.getPeriodDays()).toSeconds();
        Integer refundedCurrent = jdbc.queryForObject("""
                SELECT count(*) FROM subscription_periods WHERE user_id = ? AND plan_code = 'PRO'
                    AND voided_at IS NOT NULL AND starts_at <= ? AND ends_at > ?
                """, Integer.class, userId, Timestamp.from(now), Timestamp.from(now));
        Instant start = lastEnd == null || (refundedCurrent != null && refundedCurrent > 0) ? now : lastEnd.toInstant();
        if (start.isBefore(now)) start = start.plusSeconds((Duration.between(start, now).toSeconds() / seconds) * seconds);
        Instant end = start.plusSeconds(seconds);
        Timestamp nextStart = jdbc.queryForObject("SELECT min(starts_at) FROM subscription_periods WHERE user_id = ? AND voided_at IS NULL AND starts_at > ?",
                Timestamp.class, userId, Timestamp.from(now));
        if (nextStart != null && nextStart.toInstant().isBefore(end)) end = nextStart.toInstant();
        UUID id = UUID.randomUUID();
        // Free is a one-time trial: each new Free period only carries what is left of it, never a fresh allowance.
        Integer usedOnFree = jdbc.queryForObject("""
                SELECT coalesce(sum(ai_used), 0) FROM subscription_periods WHERE user_id = ? AND plan_code = 'FREE'
                """, Integer.class, userId);
        int allowance = Math.max(0, properties.getFreeAiLimit() - (usedOnFree == null ? 0 : usedOnFree));
        jdbc.update("INSERT INTO subscription_periods (id, user_id, plan_code, starts_at, ends_at, ai_limit) VALUES (?, ?, 'FREE', ?, ?, ?)",
                id, userId, Timestamp.from(start), Timestamp.from(end), allowance);
        return new Summary(id, "FREE", start, end, allowance, 0, 0, allowance);
    }

    private void recoverExpired(UUID userId, Instant now) {
        // Never call the provider again for an abandoned key, even after returning the user's quota.
        List<Usage> abandoned = jdbc.query("SELECT * FROM ai_usage_requests WHERE user_id = ? AND status = 'RESERVED' AND created_at < ?",
                (rs, row) -> usage(rs), userId, Timestamp.from(now.minusSeconds(300)));
        for (Usage item : abandoned) release(item, "UNKNOWN", "PROCESS_INTERRUPTED");
    }

    private void release(Usage item, String status, String code) {
        jdbc.update("UPDATE subscription_periods SET ai_reserved = ai_reserved - 1 WHERE id = ?", item.periodId());
        jdbc.update("UPDATE ai_usage_requests SET status = ?, failure_code = ?, finished_at = clock_timestamp() WHERE id = ?", status, code, item.id());
    }

    private void lockUser(UUID userId, boolean requireActive) {
        var users = jdbc.queryForList("SELECT role, status FROM users WHERE id = ? FOR UPDATE", userId);
        if (users.isEmpty() || !"USER".equals(users.getFirst().get("role"))) throw error(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng");
        if (requireActive && !"ACTIVE".equals(users.getFirst().get("status"))) throw error(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa");
    }

    private Usage requireUsage(UUID userId, UUID id) {
        return jdbc.query("SELECT * FROM ai_usage_requests WHERE id = ? AND user_id = ?", (rs, row) -> usage(rs), id, userId)
                .stream().findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu AI"));
    }

    private Instant databaseNow() { return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant(); }
    private Summary summary(ResultSet rs) throws SQLException {
        int limit = rs.getInt("ai_limit"), used = rs.getInt("ai_used"), reserved = rs.getInt("ai_reserved");
        return new Summary(rs.getObject("id", UUID.class), rs.getString("plan_code"), rs.getTimestamp("starts_at").toInstant(),
                rs.getTimestamp("ends_at").toInstant(), limit, used, reserved, limit - used - reserved);
    }
    private Usage usage(ResultSet rs) throws SQLException {
        Timestamp finished = rs.getTimestamp("finished_at");
        return new Usage(rs.getObject("id", UUID.class), rs.getObject("period_id", UUID.class), rs.getObject("idempotency_key", UUID.class),
                rs.getString("status"), rs.getObject("post_id", UUID.class), rs.getString("model"), rs.getString("failure_code"),
                rs.getTimestamp("created_at").toInstant(), finished == null ? null : finished.toInstant());
    }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    public record Summary(UUID periodId, String plan, Instant startsAt, Instant endsAt, int limit, int used, int reserved, int remaining) { }
    public record Reservation(UUID requestId, UUID existingPostId) { }
    public record Usage(UUID id, UUID periodId, UUID idempotencyKey, String status, UUID postId, String model, String failureCode, Instant createdAt, Instant finishedAt) { }
    public record History(List<Usage> items, int page, int size, long total) { }
}
