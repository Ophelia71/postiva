SET LOCAL lock_timeout = '5s';

ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT chk_users_mfa_role CHECK (NOT mfa_enabled OR role = 'ADMIN');

CREATE TABLE admin_mfa (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    secret_encrypted TEXT,
    setup_expires_at TIMESTAMP WITH TIME ZONE,
    last_used_step BIGINT NOT NULL DEFAULT -1,
    recovery_hashes JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(recovery_hashes) = 'array'),
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    blocked_until TIMESTAMP WITH TIME ZONE
);

ALTER TABLE admin_security_events DROP CONSTRAINT admin_security_events_action_check,
    ADD CONSTRAINT admin_security_events_action_check CHECK (action IN (
        'PASSWORD_CHANGED', 'MFA_ENABLED', 'MFA_DISABLED', 'MFA_RECOVERY_USED',
        'MFA_RECOVERY_REGENERATED', 'MFA_FAILED'
    ));
