package com.postiva.automation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Pauses rules from places that must not depend on the rest of the automation module
 * (account locking, channel disconnect). JDBC only, so there is no bean cycle.
 * A paused rule is only ever resumed by its owner.
 */
@Component
public class AutomationPauser {
    public static final String REASON_USER = "USER";
    public static final String REASON_USER_LOCKED = "USER_LOCKED";
    public static final String REASON_CHANNEL_DISCONNECTED = "CHANNEL_DISCONNECTED";
    public static final String REASON_TOKEN = "TOKEN";
    public static final String REASON_UNKNOWN_RESULT = "UNKNOWN_RESULT";
    public static final String REASON_AI_QUOTA = "AI_QUOTA_EXHAUSTED";
    public static final String REASON_MEDIA_MISSING = "MEDIA_MISSING";

    private final JdbcTemplate jdbc;

    public AutomationPauser(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public int pauseAllForUser(UUID userId, String reason) {
        int paused = jdbc.update("""
                UPDATE automation_rules
                SET status = 'PAUSED', paused_reason = ?, paused_at = clock_timestamp(),
                    updated_at = clock_timestamp(), version = version + 1
                WHERE user_id = ? AND status = 'ACTIVE'
                """, reason, userId);
        skipOpenRuns("r.user_id = ?", userId, reason);
        return paused;
    }

    @Transactional
    public int pauseForAccount(UUID socialAccountId, String reason) {
        int paused = jdbc.update("""
                UPDATE automation_rules
                SET status = 'PAUSED', paused_reason = ?, paused_at = clock_timestamp(),
                    updated_at = clock_timestamp(), version = version + 1
                WHERE social_account_id = ? AND status = 'ACTIVE'
                """, reason, socialAccountId);
        skipOpenRuns("r.social_account_id = ?", socialAccountId, reason);
        return paused;
    }

    @Transactional
    public boolean pauseRule(UUID ruleId, String reason) {
        int changed = jdbc.update("""
                UPDATE automation_rules
                SET status = 'PAUSED', paused_reason = ?, paused_at = clock_timestamp(),
                    updated_at = clock_timestamp(), version = version + 1
                WHERE id = ? AND status = 'ACTIVE'
                """, reason, ruleId);
        if (changed > 0) skipOpenRuns("r.id = ?", ruleId, reason);
        return changed > 0;
    }

    /**
     * Generation in flight is always dropped. Drafts already waiting for review are dropped only for
     * automatic pauses; when the owner pauses on purpose they keep the drafts they already paid for.
     */
    private void skipOpenRuns(String ruleFilter, UUID value, String reason) {
        String statuses = REASON_USER.equals(reason) ? "'GENERATING'" : "'GENERATING', 'PENDING_REVIEW'";
        jdbc.update("""
                UPDATE automation_runs run
                SET status = 'SKIPPED', error = 'PAUSED', claim_token = NULL, lease_until = NULL,
                    updated_at = clock_timestamp()
                FROM automation_rules r
                WHERE run.rule_id = r.id AND run.status IN (""" + statuses + ") AND " + ruleFilter, value);
    }
}