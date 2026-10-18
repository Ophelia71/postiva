SET LOCAL lock_timeout = '5s';

CREATE TABLE request_rate_limits (
    bucket_key VARCHAR(160) PRIMARY KEY,
    attempts INTEGER NOT NULL CHECK (attempts > 0),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_rate_limits_expiry ON request_rate_limits (expires_at);
ALTER TABLE pending_registrations ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0);

CREATE TABLE subscription_periods (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    plan_code VARCHAR(10) NOT NULL CHECK (plan_code IN ('FREE', 'PRO')),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    ai_limit INTEGER NOT NULL CHECK (ai_limit >= 0),
    ai_used INTEGER NOT NULL DEFAULT 0 CHECK (ai_used >= 0),
    ai_reserved INTEGER NOT NULL DEFAULT 0 CHECK (ai_reserved >= 0),
    source_reference VARCHAR(160) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (ends_at > starts_at),
    CHECK (ai_used + ai_reserved <= ai_limit),
    CHECK (plan_code = 'FREE' OR source_reference IS NOT NULL),
    UNIQUE (user_id, starts_at)
);
CREATE INDEX idx_subscription_periods_user_end ON subscription_periods (user_id, ends_at DESC);

CREATE TABLE ai_usage_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    period_id UUID NOT NULL REFERENCES subscription_periods(id),
    idempotency_key UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    post_id UUID REFERENCES generated_posts(id) ON DELETE SET NULL,
    model VARCHAR(100) NOT NULL,
    failure_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ,
    UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_ai_usage_user_created ON ai_usage_requests (user_id, created_at DESC, id DESC);
CREATE INDEX idx_ai_usage_pending ON ai_usage_requests (created_at) WHERE status = 'RESERVED';

-- Includes failed/unknown provider attempts; refunds must not reset the cost guard.
CREATE TABLE ai_daily_budgets (
    usage_day DATE PRIMARY KEY,
    attempts INTEGER NOT NULL CHECK (attempts > 0)
);
