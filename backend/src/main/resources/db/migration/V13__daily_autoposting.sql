-- Daily auto-posting. Automation only creates ordinary scheduled_posts rows; the existing
-- scheduler claims, publishes, retries and reconciles them. Nothing here talks to a provider.

CREATE TABLE automation_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    social_account_id UUID REFERENCES social_accounts(id) ON DELETE SET NULL,
    platform VARCHAR(20) NOT NULL CHECK (platform IN ('FACEBOOK', 'INSTAGRAM', 'THREADS')),
    name VARCHAR(120) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED')),
    paused_reason VARCHAR(40),
    paused_at TIMESTAMPTZ,
    -- IANA zone id and local wall-clock time; instants are derived per day so DST stays correct.
    timezone VARCHAR(64) NOT NULL,
    time_of_day TIME NOT NULL,
    -- Bit n set = day n allowed, with Monday = bit 0 .. Sunday = bit 6 (127 = every day).
    days_of_week SMALLINT NOT NULL DEFAULT 127 CHECK (days_of_week BETWEEN 1 AND 127),
    approval_mode VARCHAR(10) NOT NULL DEFAULT 'REVIEW' CHECK (approval_mode IN ('REVIEW', 'AUTO')),
    lead_minutes INTEGER NOT NULL DEFAULT 720 CHECK (lead_minutes BETWEEN 60 AND 1440),
    -- The GeneratePostRequest fields (product, audience, highlights, goal, tone), minus platforms.
    brief JSONB NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_automation_rules_user ON automation_rules (user_id);
CREATE INDEX idx_automation_rules_active ON automation_rules (status) WHERE status = 'ACTIVE';
CREATE INDEX idx_automation_rules_account ON automation_rules (social_account_id);

CREATE TABLE automation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
    -- Local calendar date in the rule's zone. UNIQUE (rule_id, run_date) is the once-per-day guard
    -- that makes concurrent planners and restarts safe.
    run_date DATE NOT NULL,
    planned_for TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('GENERATING', 'PENDING_REVIEW', 'SCHEDULED', 'SKIPPED', 'FAILED')),
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    -- Deterministic per (rule, day, rule version): a retry reuses the AI request instead of paying twice.
    idempotency_key UUID NOT NULL,
    post_id UUID REFERENCES generated_posts(id) ON DELETE SET NULL,
    scheduled_post_id UUID REFERENCES scheduled_posts(id) ON DELETE SET NULL,
    error VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_automation_runs_day UNIQUE (rule_id, run_date)
);

CREATE UNIQUE INDEX uq_automation_runs_scheduled ON automation_runs (scheduled_post_id)
    WHERE scheduled_post_id IS NOT NULL;
CREATE INDEX idx_automation_runs_status ON automation_runs (status, planned_for);

-- Structured reason for a failed publish so automation can pause on token problems without
-- parsing localized error text. Additive and nullable; the scheduler state machine is unchanged.
ALTER TABLE scheduled_posts ADD COLUMN failure_code VARCHAR(40);
