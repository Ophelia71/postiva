package com.postiva.analytics;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class WebAnalyticsService {
    private final JdbcTemplate jdbc;

    public WebAnalyticsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void record(Event event, UUID authenticatedUserId) {
        if (event == null || event.type() == null || event.sessionId() == null || event.visitorId() == null) {
            throw badRequest();
        }
        if (event.type() == Type.EXCLUDE) throw badRequest();
        if (event.type() == Type.PAGE_VIEW && (event.viewId() == null || !validPath(event.path()) || event.source() == null)) {
            throw badRequest();
        }
        if (event.type() == Type.SCROLL && (event.viewId() == null || event.scrollDepth() == null
                || !List.of(25, 50, 75, 100).contains(event.scrollDepth()))) {
            throw badRequest();
        }
        // Product milestones (onboarding) are sent as page views under /virtual/. They are recorded, but never start a
        // session and never count toward a session's page views, so bounce rate and page-view totals stay honest.
        boolean virtual = event.type() == Type.PAGE_VIEW && isVirtualPath(event.path());
        if (event.type() == Type.PAGE_VIEW && !virtual) {
            // A repeated delivery keeps the same IDs, so it cannot increase the page-view count.
            jdbc.update("""
                    INSERT INTO web_analytics_sessions (id, visitor_id, user_id, landing_path, source, is_new_visitor)
                    VALUES (?, ?, ?, ?, ?, NOT EXISTS
                        (SELECT 1 FROM web_analytics_sessions WHERE visitor_id = ?))
                    ON CONFLICT (id) DO NOTHING
                    """, event.sessionId(), event.visitorId(), authenticatedUserId,
                    event.path(), event.source().name(), event.visitorId());
        }

        var rows = jdbc.query("""
                SELECT visitor_id, last_seen_at, page_views FROM web_analytics_sessions WHERE id = ? FOR UPDATE
                """, (rs, row) -> new Session(rs.getObject("visitor_id", UUID.class),
                rs.getTimestamp("last_seen_at").toInstant(), rs.getInt("page_views")), event.sessionId());
        if (rows.isEmpty()) return; // Delayed event after retention cleanup.
        Session session = rows.getFirst();
        if (!session.visitorId().equals(event.visitorId())) throw badRequest();
        Instant now = Instant.now();
        if (session.lastSeenAt().isBefore(now.minus(30, ChronoUnit.MINUTES))) return;

        if (event.type() == Type.PAGE_VIEW) {
            if (session.pageViews() >= 100 && !virtual) return;
            int inserted = jdbc.update("""
                    INSERT INTO web_analytics_page_views (id, session_id, path)
                    VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING
                    """, event.viewId(), event.sessionId(), event.path());
            if (inserted == 1 && virtual) {
                jdbc.update("UPDATE web_analytics_sessions SET last_seen_at = clock_timestamp(), user_id = COALESCE(?, user_id) WHERE id = ?",
                        authenticatedUserId, event.sessionId());
            } else if (inserted == 1) {
                jdbc.update("""
                        UPDATE web_analytics_sessions SET page_views = page_views + 1,
                            last_seen_at = clock_timestamp(), user_id = COALESCE(?, user_id)
                        WHERE id = ?
                        """, authenticatedUserId, event.sessionId());
            }
        } else if (event.type() == Type.SCROLL) {
            int updated = jdbc.update("""
                    UPDATE web_analytics_page_views SET max_scroll = GREATEST(max_scroll, ?)
                    WHERE id = ? AND session_id = ? AND max_scroll < ?
                    """, event.scrollDepth(), event.viewId(), event.sessionId(), event.scrollDepth());
            if (updated == 1) {
                jdbc.update("""
                        UPDATE web_analytics_sessions SET engaged = TRUE, last_seen_at = clock_timestamp(),
                            user_id = COALESCE(?, user_id) WHERE id = ?
                        """, authenticatedUserId, event.sessionId());
            }
        } else if (event.type() == Type.ENGAGED) {
            jdbc.update("""
                    UPDATE web_analytics_sessions SET engaged = TRUE, last_seen_at = clock_timestamp(),
                        user_id = COALESCE(?, user_id) WHERE id = ?
                    """, authenticatedUserId, event.sessionId());
        } else if (event.type() == Type.HEARTBEAT) {
            jdbc.update("""
                    UPDATE web_analytics_sessions SET last_seen_at = clock_timestamp(), user_id = COALESCE(?, user_id)
                    WHERE id = ? AND last_seen_at < clock_timestamp() - INTERVAL '10 seconds'
                    """, authenticatedUserId, event.sessionId());
        } else if (event.type() == Type.IDENTIFY && authenticatedUserId != null) {
            jdbc.update("UPDATE web_analytics_sessions SET user_id = ?, last_seen_at = clock_timestamp() WHERE id = ?",
                    authenticatedUserId, event.sessionId());
        }
    }

    public void excludeAdminSession(Event event) {
        if (event == null || event.sessionId() == null || event.visitorId() == null) throw badRequest();
        jdbc.update("DELETE FROM web_analytics_sessions WHERE id = ? AND visitor_id = ?",
                event.sessionId(), event.visitorId());
    }

    @Scheduled(cron = "0 23 3 * * *", zone = "UTC")
    @Transactional
    public void removeExpired() {
        jdbc.update("DELETE FROM web_analytics_sessions WHERE started_at < clock_timestamp() - INTERVAL '180 days'");
    }

    static boolean isVirtualPath(String path) {
        return path != null && path.startsWith("/virtual/");
    }

    private boolean validPath(String path) {
        return path != null && path.length() <= 200 && path.startsWith("/")
                && !path.contains("?") && !path.contains("#") && !path.contains("\\");
    }

    private ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sự kiện analytics không hợp lệ");
    }

    private record Session(UUID visitorId, Instant lastSeenAt, int pageViews) { }
    public enum Type { PAGE_VIEW, SCROLL, ENGAGED, HEARTBEAT, IDENTIFY, EXCLUDE }
    public enum Source { DIRECT, ORGANIC, SOCIAL, REFERRAL, PAID, EMAIL, OTHER }
    public record Event(Type type, UUID visitorId, UUID sessionId, UUID viewId,
                        String path, Source source, Integer scrollDepth) { }
}
