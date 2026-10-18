SET LOCAL lock_timeout = '5s';

-- Every request reserves a worst-case amount before provider I/O. Actual response
-- token usage settles that reservation; timeouts remain reserved for reconciliation.
CREATE TABLE ai_cost_budget_windows (
    window_type VARCHAR(10) NOT NULL CHECK (window_type IN ('DAILY', 'MONTHLY')),
    window_start DATE NOT NULL,
    budget_usd NUMERIC(16,10) NOT NULL CHECK (budget_usd > 0),
    spent_usd NUMERIC(16,10) NOT NULL DEFAULT 0 CHECK (spent_usd >= 0),
    actual_provider_usd NUMERIC(16,10) NOT NULL DEFAULT 0 CHECK (actual_provider_usd >= 0),
    reserved_usd NUMERIC(16,10) NOT NULL DEFAULT 0 CHECK (reserved_usd >= 0),
    warning_sent_at TIMESTAMPTZ,
    actual_reconciled_at TIMESTAMPTZ,
    PRIMARY KEY (window_type, window_start)
);

CREATE TABLE ai_provider_costs (
    request_id UUID PRIMARY KEY REFERENCES ai_usage_requests(id),
    usage_day DATE NOT NULL,
    usage_month DATE NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_rate_usd_per_million NUMERIC(16,10) NOT NULL CHECK (input_rate_usd_per_million > 0),
    cached_input_rate_usd_per_million NUMERIC(16,10) NOT NULL CHECK (cached_input_rate_usd_per_million > 0),
    output_rate_usd_per_million NUMERIC(16,10) NOT NULL CHECK (output_rate_usd_per_million > 0),
    provider_model VARCHAR(100),
    provider_request_id VARCHAR(160),
    input_tokens INTEGER CHECK (input_tokens >= 0),
    cached_input_tokens INTEGER CHECK (cached_input_tokens >= 0),
    output_tokens INTEGER CHECK (output_tokens >= 0),
    estimated_cost_usd NUMERIC(16,10) CHECK (estimated_cost_usd >= 0),
    reserved_usd NUMERIC(16,10) NOT NULL CHECK (reserved_usd >= 0),
    status VARCHAR(12) NOT NULL CHECK (status IN ('RESERVED', 'SETTLED', 'UNKNOWN', 'RELEASED', 'RECONCILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    settled_at TIMESTAMPTZ,
    CHECK (cached_input_tokens IS NULL OR input_tokens IS NULL OR cached_input_tokens <= input_tokens)
);
CREATE INDEX idx_ai_provider_costs_status ON ai_provider_costs(status, created_at DESC);

CREATE TABLE ai_runtime_control (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by UUID REFERENCES users(id),
    reason TEXT NOT NULL DEFAULT 'Initial state'
);
INSERT INTO ai_runtime_control (id, enabled, reason) VALUES (1, TRUE, 'Initial state');

CREATE TABLE ai_runtime_control_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id UUID NOT NULL REFERENCES users(id),
    enabled BOOLEAN NOT NULL,
    reason TEXT NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ai_runtime_control_audit_created ON ai_runtime_control_audit(created_at DESC);
