package com.postiva.analytics;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AdminWebAnalyticsService {
    private final JdbcTemplate jdbc;

    public AdminWebAnalyticsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Dashboard dashboard(int days) {
        if (days < 1 || days > 90) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng thời gian phải từ 1 đến 90 ngày");
        Instant end = Instant.now();
        Instant start = end.minus(days, ChronoUnit.DAYS);
        Timestamp from = Timestamp.from(start);
        Timestamp to = Timestamp.from(end);

        SessionTotals sessions = jdbc.queryForObject("""
                SELECT COUNT(*) AS sessions, COUNT(DISTINCT visitor_id) AS visitors,
                       COALESCE(SUM(page_views),0) AS page_views,
                       COUNT(*) FILTER (WHERE page_views <= 1 AND NOT engaged) AS bounces,
                       COUNT(*) FILTER (WHERE is_new_visitor) AS new_sessions,
                       COUNT(*) FILTER (WHERE NOT is_new_visitor) AS returning_sessions,
                       COALESCE(SUM(EXTRACT(EPOCH FROM last_seen_at - started_at)),0) AS duration_seconds,
                       COUNT(DISTINCT user_id) AS identified_users
                FROM web_analytics_sessions WHERE started_at >= ? AND started_at < ?
                """, (rs, row) -> new SessionTotals(rs.getLong("sessions"), rs.getLong("visitors"),
                rs.getLong("page_views"), rs.getLong("bounces"), rs.getLong("new_sessions"),
                rs.getLong("returning_sessions"), rs.getDouble("duration_seconds"),
                rs.getLong("identified_users")), from, to);

        List<SourceCount> sources = jdbc.query("""
                SELECT source, COUNT(*) AS sessions FROM web_analytics_sessions
                WHERE started_at >= ? AND started_at < ? GROUP BY source ORDER BY sessions DESC, source
                """, (rs, row) -> new SourceCount(rs.getString("source"), rs.getLong("sessions")), from, to);

        VisitorMix visitorMix = jdbc.queryForObject("""
                WITH period_visitors AS (
                    SELECT DISTINCT visitor_id FROM web_analytics_sessions
                    WHERE started_at >= ? AND started_at < ?
                )
                SELECT COUNT(*) FILTER (WHERE first_seen >= ?) AS new_visitors,
                       COUNT(*) FILTER (WHERE first_seen < ?) AS returning_visitors
                FROM (
                    SELECT MIN(s.started_at) AS first_seen FROM period_visitors v
                    JOIN web_analytics_sessions s ON s.visitor_id = v.visitor_id
                    GROUP BY v.visitor_id
                ) first_visits
                """, (rs, row) -> new VisitorMix(rs.getLong("new_visitors"),
                rs.getLong("returning_visitors")), from, to, from, from);

        ScrollTotals scroll = jdbc.queryForObject("""
                SELECT COUNT(*) AS views,
                       COUNT(*) FILTER (WHERE max_scroll >= 25) AS depth_25,
                       COUNT(*) FILTER (WHERE max_scroll >= 50) AS depth_50,
                       COUNT(*) FILTER (WHERE max_scroll >= 75) AS depth_75,
                       COUNT(*) FILTER (WHERE max_scroll >= 100) AS depth_100
                FROM web_analytics_page_views WHERE viewed_at >= ? AND viewed_at < ?
                """, (rs, row) -> new ScrollTotals(rs.getLong("views"), rs.getLong("depth_25"),
                rs.getLong("depth_50"), rs.getLong("depth_75"), rs.getLong("depth_100")), from, to);

        // users.created_at predates timezone-aware migrations. Match the JVM timezone used by User.beforeInsert().
        LocalDateTime userFrom = LocalDateTime.ofInstant(start, ZoneId.systemDefault());
        LocalDateTime userTo = LocalDateTime.ofInstant(end, ZoneId.systemDefault());
        long registrations = count("""
                SELECT COUNT(*) FROM users WHERE role = 'USER' AND created_at >= ? AND created_at < ?
                """, userFrom, userTo);
        long attributedRegistrations = count("""
                SELECT COUNT(DISTINCT s.visitor_id) FROM web_analytics_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.started_at >= ? AND s.started_at < ? AND u.role = 'USER'
                  AND u.created_at >= ? AND u.created_at < ?
                """, from, to, userFrom, userTo);

        PaymentTotals payments = jdbc.queryForObject("""
                SELECT COUNT(*) AS orders, COUNT(DISTINCT user_id) AS buyers,
                       COALESCE(SUM(amount_vnd),0) AS gross_vnd
                FROM payment_orders WHERE status = 'PAID' AND paid_at >= ? AND paid_at < ?
                """, (rs, row) -> new PaymentTotals(rs.getLong("orders"), rs.getLong("buyers"),
                rs.getLong("gross_vnd")), from, to);
        long refunds = count("""
                SELECT COALESCE(SUM(amount_vnd),0) FROM payment_refunds
                WHERE status = 'SUCCEEDED' AND settled_at >= ? AND settled_at < ?
                """, from, to);
        long attributedBuyers = count("""
                SELECT COUNT(DISTINCT s.visitor_id) FROM web_analytics_sessions s
                WHERE s.started_at >= ? AND s.started_at < ? AND s.user_id IN (
                    SELECT user_id FROM payment_orders WHERE status = 'PAID' AND paid_at >= ? AND paid_at < ?
                )
                """, from, to, from, to);

        Timestamp first = jdbc.queryForObject("SELECT MIN(started_at) FROM web_analytics_sessions", Timestamp.class);
        return new Dashboard(start, end, first == null ? null : first.toInstant(), sessions,
                ratio(sessions.bounces(), sessions.sessions()),
                sessions.sessions() == 0 ? null : sessions.durationSeconds() / sessions.sessions(),
                sessions.sessions() == 0 ? null : (double) sessions.pageViews() / sessions.sessions(),
                visitorMix, sources, scroll, registrations, attributedRegistrations,
                ratio(attributedRegistrations, sessions.visitors()), payments, refunds,
                attributedBuyers, ratio(attributedBuyers, sessions.visitors()),
                payments.orders() == 0 ? null : (double) payments.grossVnd() / payments.orders());
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : numerator * 100.0 / denominator;
    }

    public record SessionTotals(long sessions, long visitors, long pageViews, long bounces,
                                long newSessions, long returningSessions, double durationSeconds,
                                long identifiedUsers) { }
    public record SourceCount(String source, long sessions) { }
    public record VisitorMix(long newVisitors, long returningVisitors) { }
    public record ScrollTotals(long views, long depth25, long depth50, long depth75, long depth100) { }
    public record PaymentTotals(long orders, long buyers, long grossVnd) { }
    public record Dashboard(Instant from, Instant to, Instant trackingStartedAt,
                            SessionTotals sessions, Double bounceRatePercent,
                            Double averageSessionDurationSeconds, Double pagesPerSession,
                            VisitorMix visitorMix, List<SourceCount> sources, ScrollTotals scroll,
                            long registrations, long attributedRegistrations, Double signUpRatePercent,
                            PaymentTotals payments, long refundsVnd,
                            long attributedBuyers, Double purchaseRatePercent, Double averageOrderValueVnd) { }
}
