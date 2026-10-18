package com.postiva.scheduler.service;

import com.postiva.scheduler.service.ScheduledPostStateService.Claim;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScheduledPostClaimService {
    private final JdbcTemplate jdbc;
    private final ScheduledPostStateService states;

    public ScheduledPostClaimService(JdbcTemplate jdbc, ScheduledPostStateService states) {
        this.jdbc = jdbc;
        this.states = states;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Claim> claimDue(Instant now, int batchSize, Duration lease) {
        states.recoverExpiredClaims(now);
        UUID claimToken = UUID.randomUUID();
        Instant leaseUntil = now.plus(lease);
        return jdbc.query("""
                WITH candidates AS (
                    SELECT id
                    FROM scheduled_posts
                    WHERE status = 'SCHEDULED'
                      AND scheduled_time <= ?
                      AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                    ORDER BY COALESCE(next_attempt_at, scheduled_time), id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE scheduled_posts AS scheduled
                SET status = 'PUBLISHING',
                    attempt_count = COALESCE(scheduled.attempt_count, 0) + 1,
                    next_attempt_at = NULL,
                    claim_token = ?,
                    lease_until = ?,
                    updated_at = ?
                FROM candidates
                WHERE scheduled.id = candidates.id
                RETURNING scheduled.id
                """, (rs, row) -> new Claim(rs.getObject(1, UUID.class), claimToken),
                Timestamp.from(now), Timestamp.from(now), batchSize,
                claimToken, Timestamp.from(leaseUntil), Timestamp.from(now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Claim> claimSpecific(UUID scheduledPostId, Instant now, Duration lease) {
        UUID claimToken = UUID.randomUUID();
        Instant leaseUntil = now.plus(lease);
        List<Claim> result = jdbc.query("""
                UPDATE scheduled_posts
                SET status = 'PUBLISHING',
                    attempt_count = COALESCE(attempt_count, 0) + 1,
                    next_attempt_at = NULL,
                    claim_token = ?,
                    lease_until = ?,
                    updated_at = ?
                WHERE id = ? AND status = 'SCHEDULED'
                RETURNING id
                """, (rs, row) -> new Claim(rs.getObject(1, UUID.class), claimToken),
                claimToken, Timestamp.from(leaseUntil), Timestamp.from(now), scheduledPostId);
        return result.stream().findFirst();
    }
}
