package com.postiva.social.sync;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Last engagement read for each of a user's published posts, keyed by scheduled post id. */
@Component
public class PostMetricsReader {
    private final JdbcTemplate jdbc;

    public PostMetricsReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, Map<String, Object>> forUser(UUID userId) {
        Map<UUID, Map<String, Object>> metrics = new HashMap<>();
        jdbc.query("""
                SELECT metric.* FROM post_metrics metric
                JOIN scheduled_posts scheduled ON scheduled.id = metric.scheduled_post_id
                JOIN social_accounts account ON account.id = scheduled.social_account_id
                WHERE account.user_id = ?
                """, rs -> {
            Map<String, Object> values = new LinkedHashMap<>();
            // Absent keys mean "not available", so the screen can tell 0 from "no permission".
            for (String column : new String[] {"likes", "comments", "shares", "views"}) {
                int value = rs.getInt(column);
                if (!rs.wasNull()) values.put(column, value);
            }
            Instant fetchedAt = rs.getTimestamp("fetched_at").toInstant();
            values.put("fetchedAt", fetchedAt.toString());
            metrics.put(rs.getObject("scheduled_post_id", UUID.class), values);
        }, userId);
        return metrics;
    }
}
