package com.postiva.automation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.automation.AutomationDtos.Brief;
import com.postiva.automation.AutomationDtos.Draft;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC access for automation rules and runs. Every state change that must happen exactly once is a
 * single conditional UPDATE or INSERT ... ON CONFLICT, so concurrent planners, approvals and
 * restarts cannot create a second run or a second scheduled post.
 */
@Repository
public class AutomationStore {
    public record Rule(UUID id, UUID userId, UUID accountId, String platform, String name, String status,
                       String pausedReason, String timezone, LocalTime timeOfDay, int daysMask, String approvalMode,
                       int leadMinutes, Brief brief, int version, Instant createdAt, Instant updatedAt) { }

    public record Run(UUID id, UUID ruleId, LocalDate runDate, Instant plannedFor, String status, UUID claimToken,
                      int attemptCount, UUID idempotencyKey, UUID postId, UUID scheduledPostId, String error) { }

    public record RunView(Run run, String ruleName, Draft draft) { }

    public record AccountState(boolean disconnected, boolean tokenMissing, LocalDateTime tokenExpiresAt, String pageName) { }

    public record LastPublish(String status, String failureCode, Instant updatedAt) { }

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AutomationStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // ---------------------------------------------------------------- rules

    public UUID insertRule(UUID userId, UUID accountId, String platform, String name, String timezone,
                           LocalTime timeOfDay, int daysMask, String approvalMode, int leadMinutes, Brief brief) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO automation_rules (id, user_id, social_account_id, platform, name, timezone, time_of_day,
                    days_of_week, approval_mode, lead_minutes, brief)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, id, userId, accountId, platform, name, timezone, timeOfDay, daysMask, approvalMode,
                leadMinutes, write(brief));
        return id;
    }

    public boolean updateRule(UUID userId, UUID ruleId, UUID accountId, String name, String timezone,
                              LocalTime timeOfDay, int daysMask, String approvalMode, int leadMinutes, Brief brief) {
        return jdbc.update("""
                UPDATE automation_rules
                SET social_account_id = ?, name = ?, timezone = ?, time_of_day = ?, days_of_week = ?,
                    approval_mode = ?, lead_minutes = ?, brief = ?::jsonb,
                    version = version + 1, updated_at = clock_timestamp()
                WHERE id = ? AND user_id = ?
                """, accountId, name, timezone, timeOfDay, daysMask, approvalMode, leadMinutes, write(brief),
                ruleId, userId) > 0;
    }

    public Optional<Rule> findRule(UUID userId, UUID ruleId) {
        return jdbc.query("SELECT * FROM automation_rules WHERE id = ? AND user_id = ?", rule(), ruleId, userId)
                .stream().findFirst();
    }

    public Optional<Rule> findRuleById(UUID ruleId) {
        return jdbc.query("SELECT * FROM automation_rules WHERE id = ?", rule(), ruleId).stream().findFirst();
    }

    public List<Rule> listRules(UUID userId) {
        return jdbc.query("SELECT * FROM automation_rules WHERE user_id = ? ORDER BY created_at, id", rule(), userId);
    }

    public int countRules(UUID userId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM automation_rules WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    public boolean deleteRule(UUID userId, UUID ruleId) {
        return jdbc.update("DELETE FROM automation_rules WHERE id = ? AND user_id = ?", ruleId, userId) > 0;
    }

    /** Active rules whose owner can still act; locked or admin owners are never planned. */
    public List<Rule> activeRules() {
        return jdbc.query("""
                SELECT r.* FROM automation_rules r JOIN users u ON u.id = r.user_id
                WHERE r.status = 'ACTIVE' AND u.status = 'ACTIVE' AND u.role = 'USER'
                ORDER BY r.created_at, r.id
                """, rule());
    }

    public boolean resumeRule(UUID userId, UUID ruleId) {
        return jdbc.update("""
                UPDATE automation_rules SET status = 'ACTIVE', paused_reason = NULL, paused_at = NULL,
                    updated_at = clock_timestamp()
                WHERE id = ? AND user_id = ? AND status = 'PAUSED'
                """, ruleId, userId) > 0;
    }

    // ----------------------------------------------------------------- runs

    /** Inserts the day's run only if no run exists yet; the winner owns it until {@code leaseUntil}. */
    public Optional<Run> tryCreateRun(UUID ruleId, LocalDate runDate, Instant plannedFor, UUID idempotencyKey,
                                      UUID claimToken, Instant leaseUntil) {
        return jdbc.query("""
                INSERT INTO automation_runs (id, rule_id, run_date, planned_for, status, claim_token, lease_until,
                    attempt_count, idempotency_key)
                VALUES (?, ?, ?, ?, 'GENERATING', ?, ?, 1, ?)
                ON CONFLICT ON CONSTRAINT uq_automation_runs_day DO NOTHING
                RETURNING *
                """, run(), UUID.randomUUID(), ruleId, runDate, Timestamp.from(plannedFor), claimToken,
                Timestamp.from(leaseUntil), idempotencyKey).stream().findFirst();
    }

    /** Takes over runs whose generation was interrupted or deferred; each takeover counts as an attempt. */
    public List<Run> claimDueGenerations(Instant now, UUID claimToken, Instant leaseUntil, int maxAttempts, int limit) {
        return jdbc.query("""
                WITH due AS (
                    SELECT id FROM automation_runs
                    WHERE status = 'GENERATING' AND (lease_until IS NULL OR lease_until <= ?)
                      AND attempt_count < ? AND planned_for > ?
                    ORDER BY planned_for, id LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE automation_runs r
                SET claim_token = ?, lease_until = ?, attempt_count = r.attempt_count + 1, updated_at = ?
                FROM due WHERE r.id = due.id
                RETURNING r.*
                """, run(), Timestamp.from(now), maxAttempts, Timestamp.from(now), limit,
                claimToken, Timestamp.from(leaseUntil), Timestamp.from(now));
    }

    /** Generations that ran out of attempts or whose slot has passed are given up on. */
    public int skipExhaustedGenerations(Instant now, int maxAttempts) {
        return jdbc.update("""
                UPDATE automation_runs
                SET status = 'SKIPPED', error = CASE WHEN planned_for <= ? THEN 'MISSED' ELSE 'GENERATION_EXHAUSTED' END,
                    claim_token = NULL, lease_until = NULL, updated_at = ?
                WHERE status = 'GENERATING' AND (lease_until IS NULL OR lease_until <= ?)
                  AND (attempt_count >= ? OR planned_for <= ?)
                """, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), maxAttempts, Timestamp.from(now));
    }

    /** Records the generated post while the run is still being processed (needed before AUTO scheduling). */
    public boolean attachPost(UUID runId, UUID claimToken, UUID postId) {
        return jdbc.update("""
                UPDATE automation_runs SET post_id = ?, updated_at = clock_timestamp()
                WHERE id = ? AND claim_token = ? AND status = 'GENERATING'
                """, postId, runId, claimToken) > 0;
    }

    public boolean markPendingReview(UUID runId, UUID claimToken, UUID postId) {
        return jdbc.update("""
                UPDATE automation_runs SET status = 'PENDING_REVIEW', post_id = ?, error = NULL,
                    claim_token = NULL, lease_until = NULL, updated_at = clock_timestamp()
                WHERE id = ? AND claim_token = ? AND status = 'GENERATING'
                """, postId, runId, claimToken) > 0;
    }

    /** Keeps the run in GENERATING with a later lease, so the next sweep retries it with the same AI key. */
    public boolean releaseForRetry(UUID runId, UUID claimToken, Instant retryAt, String error) {
        return jdbc.update("""
                UPDATE automation_runs SET claim_token = NULL, lease_until = ?, error = ?, updated_at = clock_timestamp()
                WHERE id = ? AND claim_token = ? AND status = 'GENERATING'
                """, Timestamp.from(retryAt), error, runId, claimToken) > 0;
    }

    public boolean finishRun(UUID runId, UUID claimToken, String status, String error) {
        return jdbc.update("""
                UPDATE automation_runs SET status = ?, error = ?, claim_token = NULL, lease_until = NULL,
                    updated_at = clock_timestamp()
                WHERE id = ? AND claim_token = ? AND status = 'GENERATING'
                """, status, error, runId, claimToken) > 0;
    }

    /**
     * The exactly-once guard for approval: only a PENDING_REVIEW run of this user, whose slot is
     * still in the future, can become SCHEDULED, and only once.
     */
    public Optional<Run> claimForScheduling(UUID userId, UUID runId) {
        return jdbc.query("""
                UPDATE automation_runs run SET status = 'SCHEDULED', error = NULL, updated_at = clock_timestamp()
                FROM automation_rules r
                WHERE run.id = ? AND run.rule_id = r.id AND r.user_id = ?
                  AND run.status = 'PENDING_REVIEW' AND run.planned_for > clock_timestamp()
                RETURNING run.*
                """, run(), runId, userId).stream().findFirst();
    }

    /** Same guard for runs the planner finishes itself in AUTO mode (no user session). */
    public Optional<Run> claimOwnGenerationForScheduling(UUID runId, UUID claimToken) {
        return jdbc.query("""
                UPDATE automation_runs SET status = 'SCHEDULED', error = NULL, claim_token = NULL, lease_until = NULL,
                    updated_at = clock_timestamp()
                WHERE id = ? AND claim_token = ? AND status = 'GENERATING' AND planned_for > clock_timestamp()
                RETURNING *
                """, run(), runId, claimToken).stream().findFirst();
    }

    public void setScheduledPost(UUID runId, UUID scheduledPostId) {
        jdbc.update("UPDATE automation_runs SET scheduled_post_id = ?, updated_at = clock_timestamp() WHERE id = ?",
                scheduledPostId, runId);
    }

    public boolean skipRun(UUID userId, UUID runId) {
        return jdbc.update("""
                UPDATE automation_runs run SET status = 'SKIPPED', error = 'USER_SKIPPED', updated_at = clock_timestamp()
                FROM automation_rules r
                WHERE run.id = ? AND run.rule_id = r.id AND r.user_id = ? AND run.status = 'PENDING_REVIEW'
                """, runId, userId) > 0;
    }

    public int expireReview(Instant now) {
        return jdbc.update("""
                UPDATE automation_runs SET status = 'SKIPPED', error = 'EXPIRED', updated_at = ?
                WHERE status = 'PENDING_REVIEW' AND planned_for <= ?
                """, Timestamp.from(now), Timestamp.from(now));
    }

    public Optional<RunView> findRunView(UUID userId, UUID runId) {
        return jdbc.query(viewSql("AND run.id = ?", ""), runView(), userId, runId).stream().findFirst();
    }

    public List<RunView> listRunViews(UUID userId, String status, int limit) {
        if (status == null || status.isBlank()) {
            return jdbc.query(viewSql("", "LIMIT ?"), runView(), userId, limit);
        }
        return jdbc.query(viewSql("AND run.status = ?", "LIMIT ?"), runView(), userId, status, limit);
    }

    private String viewSql(String extraWhere, String limit) {
        return """
                SELECT run.*, r.name AS rule_name,
                       p.platform_contents -> 'FACEBOOK' ->> 'content' AS draft_content,
                       p.platform_contents ->> '_title' AS draft_title,
                       p.hashtags::text AS draft_hashtags, p.cta AS draft_cta
                FROM automation_runs run
                JOIN automation_rules r ON r.id = run.rule_id
                LEFT JOIN generated_posts p ON p.id = run.post_id
                WHERE r.user_id = ?
                """ + extraWhere + " ORDER BY run.planned_for DESC, run.id " + limit;
    }

    // ------------------------------------------------------------ pre-checks

    public Optional<AccountState> accountState(UUID accountId) {
        if (accountId == null) return Optional.empty();
        return jdbc.query("""
                SELECT disconnected_at IS NOT NULL AS disconnected,
                       coalesce(access_token_encrypted, '') = '' AS token_missing,
                       token_expires_at, page_name
                FROM social_accounts WHERE id = ?
                """, (rs, row) -> new AccountState(rs.getBoolean("disconnected"), rs.getBoolean("token_missing"),
                rs.getObject("token_expires_at", LocalDateTime.class), rs.getString("page_name")), accountId)
                .stream().findFirst();
    }

    /** The scheduled post created by the rule's most recent run, if any. */
    public Optional<LastPublish> lastPublish(UUID ruleId) {
        return jdbc.query("""
                SELECT sp.status, sp.failure_code, sp.updated_at
                FROM automation_runs run JOIN scheduled_posts sp ON sp.id = run.scheduled_post_id
                WHERE run.rule_id = ? ORDER BY run.run_date DESC, run.created_at DESC LIMIT 1
                """, (rs, row) -> new LastPublish(rs.getString("status"), rs.getString("failure_code"),
                rs.getTimestamp("updated_at").toInstant()), ruleId).stream().findFirst();
    }

    /** Titles of the rule's latest generated posts, used to steer the AI away from repeating itself. */
    public List<String> recentTitles(UUID ruleId, LocalDate before, int limit) {
        return jdbc.queryForList("""
                SELECT p.platform_contents ->> '_title'
                FROM automation_runs run JOIN generated_posts p ON p.id = run.post_id
                WHERE run.rule_id = ? AND run.run_date < ? AND p.platform_contents ->> '_title' IS NOT NULL
                ORDER BY run.run_date DESC LIMIT ?
                """, String.class, ruleId, before, limit);
    }

    // --------------------------------------------------------------- mappers

    private RowMapper<Rule> rule() {
        return (rs, row) -> new Rule(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("social_account_id", UUID.class), rs.getString("platform"), rs.getString("name"),
                rs.getString("status"), rs.getString("paused_reason"), rs.getString("timezone"),
                rs.getObject("time_of_day", LocalTime.class), rs.getInt("days_of_week"),
                rs.getString("approval_mode"), rs.getInt("lead_minutes"), readBrief(rs.getString("brief")),
                rs.getInt("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private RowMapper<Run> run() {
        return (rs, row) -> toRun(rs);
    }

    private Run toRun(ResultSet rs) throws SQLException {
        return new Run(rs.getObject("id", UUID.class), rs.getObject("rule_id", UUID.class),
                rs.getObject("run_date", LocalDate.class), rs.getTimestamp("planned_for").toInstant(),
                rs.getString("status"), rs.getObject("claim_token", UUID.class), rs.getInt("attempt_count"),
                rs.getObject("idempotency_key", UUID.class), rs.getObject("post_id", UUID.class),
                rs.getObject("scheduled_post_id", UUID.class), rs.getString("error"));
    }

    private RowMapper<RunView> runView() {
        return (rs, row) -> {
            String content = rs.getString("draft_content");
            Draft draft = content == null ? null
                    : new Draft(rs.getString("draft_title"), content, readStrings(rs.getString("draft_hashtags")),
                    rs.getString("draft_cta"));
            return new RunView(toRun(rs), rs.getString("rule_name"), draft);
        };
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Dữ liệu không hợp lệ", e);
        }
    }

    private Brief readBrief(String value) {
        try {
            return json.readValue(value, Brief.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Nội dung rule không đọc được", e);
        }
    }

    private List<String> readStrings(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, STRINGS);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

}
