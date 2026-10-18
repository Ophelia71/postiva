CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT chk_password_reset_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_password_reset_token_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_password_reset_tokens_expiry
    ON password_reset_tokens(expires_at)
    WHERE used_at IS NULL;

CREATE UNIQUE INDEX idx_password_reset_tokens_one_active_per_user
    ON password_reset_tokens(user_id)
    WHERE used_at IS NULL;
