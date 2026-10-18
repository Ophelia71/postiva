package com.postiva.auth.security;

import com.postiva.common.RateLimitException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class PersistentRateLimiter {
    private final JdbcTemplate jdbc;
    public PersistentRateLimiter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Authentication/business rollbacks must not undo attempts.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consume(String scope, String subject, int limit, int windowSeconds) {
        if (limit < 1 || windowSeconds < 1) throw new IllegalArgumentException("Rate limit phải lớn hơn 0");
        String key = scope + ":" + sha256(subject);
        var accepted = jdbc.queryForList("""
                INSERT INTO request_rate_limits AS bucket (bucket_key, attempts, expires_at)
                VALUES (?, 1, clock_timestamp() + (? * INTERVAL '1 second'))
                ON CONFLICT (bucket_key) DO UPDATE SET
                    attempts = CASE WHEN bucket.expires_at <= clock_timestamp() THEN 1 ELSE bucket.attempts + 1 END,
                    expires_at = CASE WHEN bucket.expires_at <= clock_timestamp() THEN EXCLUDED.expires_at ELSE bucket.expires_at END
                WHERE bucket.expires_at <= clock_timestamp() OR bucket.attempts < ?
                RETURNING attempts
                """, Integer.class, key, windowSeconds, limit);
        if (accepted.isEmpty()) {
            Long wait = jdbc.queryForObject("""
                    SELECT GREATEST(1, CEIL(EXTRACT(EPOCH FROM expires_at - clock_timestamp())))::bigint
                    FROM request_rate_limits WHERE bucket_key = ?
                    """, Long.class, key);
            throw new RateLimitException("Quá nhiều yêu cầu. Vui lòng chờ rồi thử lại.", wait == null ? windowSeconds : wait);
        }
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void purgeExpired() {
        jdbc.update("DELETE FROM request_rate_limits WHERE expires_at < clock_timestamp() - INTERVAL '1 day'");
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
