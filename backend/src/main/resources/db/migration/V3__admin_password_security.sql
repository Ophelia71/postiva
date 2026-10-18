-- Preserve existing users and tokens at version 0; rotation invalidates old JWTs.
SET LOCAL lock_timeout = '5s';

ALTER TABLE users
    ADD COLUMN auth_version INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_users_auth_version CHECK (auth_version >= 0);

CREATE TABLE admin_security_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(40) NOT NULL CHECK (action = 'PASSWORD_CHANGED'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_security_events_admin_created
    ON admin_security_events(admin_user_id, created_at DESC);

CREATE TRIGGER trg_admin_security_events_append_only
    BEFORE UPDATE OR DELETE ON admin_security_events
    FOR EACH ROW EXECUTE FUNCTION prevent_admin_audit_mutation();

CREATE TRIGGER trg_admin_security_events_no_truncate
    BEFORE TRUNCATE ON admin_security_events
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_admin_audit_mutation();
