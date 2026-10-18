-- Additive upgrade. Existing accounts retain access; no user/content rows deleted.
SET LOCAL lock_timeout = '5s';

ALTER TABLE users
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN locked_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN locked_by UUID,
    ADD COLUMN lock_reason TEXT,
    ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'LOCKED')),
    ADD CONSTRAINT fk_users_locked_by FOREIGN KEY (locked_by) REFERENCES users(id),
    ADD CONSTRAINT chk_users_lock_metadata CHECK (
        (status = 'ACTIVE' AND locked_at IS NULL AND locked_by IS NULL AND lock_reason IS NULL)
        OR (status = 'LOCKED' AND locked_at IS NOT NULL AND locked_by IS NOT NULL
            AND lock_reason IS NOT NULL AND length(btrim(lock_reason)) BETWEEN 1 AND 1000)
    );

CREATE INDEX idx_users_status ON users(status);

CREATE TABLE admin_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID NOT NULL REFERENCES users(id),
    target_user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(30) NOT NULL,
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason TEXT NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_admin_audit_target CHECK (actor_user_id <> target_user_id),
    CONSTRAINT chk_admin_audit_transition CHECK (
        (action = 'LOCK_USER' AND previous_status = 'ACTIVE' AND new_status = 'LOCKED')
        OR (action = 'UNLOCK_USER' AND previous_status = 'LOCKED' AND new_status = 'ACTIVE')
    )
);

CREATE INDEX idx_admin_audit_target_created ON admin_audit_logs(target_user_id, created_at DESC);
CREATE INDEX idx_admin_audit_actor_created ON admin_audit_logs(actor_user_id, created_at DESC);

-- Operational audit records are append-only; account deletion must not cascade.
CREATE FUNCTION prevent_admin_audit_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Admin audit records are append-only' USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_admin_audit_append_only
    BEFORE UPDATE OR DELETE ON admin_audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_admin_audit_mutation();

CREATE TRIGGER trg_admin_audit_no_truncate
    BEFORE TRUNCATE ON admin_audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_admin_audit_mutation();
