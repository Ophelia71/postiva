-- Existing scheduler timestamps were written as local timestamps by the JVM.
-- Convert them using the legacy application timezone (UTC for the current Docker image).
ALTER TABLE scheduled_posts
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN scheduled_time TYPE TIMESTAMPTZ
        USING scheduled_time AT TIME ZONE '${legacyScheduleTimeZone}',
    ALTER COLUMN published_at TYPE TIMESTAMPTZ
        USING published_at AT TIME ZONE '${legacyTimestampTimeZone}',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ
        USING created_at AT TIME ZONE '${legacyTimestampTimeZone}',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ
        USING updated_at AT TIME ZONE '${legacyTimestampTimeZone}';

ALTER TABLE scheduled_posts
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE publish_logs
    ALTER COLUMN published_at TYPE TIMESTAMPTZ
        USING published_at AT TIME ZONE '${legacyTimestampTimeZone}';

ALTER TABLE scheduled_posts
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN claim_token UUID,
    ADD COLUMN lease_until TIMESTAMPTZ;

ALTER TABLE scheduled_posts
    DROP CONSTRAINT IF EXISTS scheduled_posts_status_check,
    DROP CONSTRAINT IF EXISTS chk_scheduled_posts_status;

ALTER TABLE scheduled_posts
    ADD CONSTRAINT chk_scheduled_posts_status
        CHECK (status IN ('SCHEDULED', 'PUBLISHING', 'PUBLISHED', 'FAILED', 'UNKNOWN', 'CANCELLED'));

-- A row left in PUBLISHING by the old worker has no durable claim or outcome.
-- Never put it back in the due queue automatically because Meta may have accepted it.
WITH recovered AS (
    UPDATE scheduled_posts
    SET status = 'UNKNOWN',
        last_error = concat_ws(' | ', NULLIF(last_error, ''),
            'Kết quả đăng chưa xác định sau khi nâng cấp scheduler; hãy kiểm tra bài trên nền tảng trước khi thử lại.')
    WHERE status = 'PUBLISHING'
    RETURNING id, platform, last_error
)
INSERT INTO publish_logs (scheduled_post_id, provider, status, error_message)
SELECT id, platform, 'UNKNOWN', last_error
FROM recovered;

DROP INDEX IF EXISTS idx_scheduled_posts_due;
CREATE INDEX idx_scheduled_posts_due
    ON scheduled_posts(status, scheduled_time, next_attempt_at);
