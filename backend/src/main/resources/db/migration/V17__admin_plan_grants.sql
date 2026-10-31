SET LOCAL lock_timeout = '5s';

-- Pro given or taken back by an admin (support, gifts, payments made outside payOS).
-- Append-only like the other admin audit records; the period itself lives in subscription_periods.
CREATE TABLE admin_plan_grants (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    target_user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(20) NOT NULL CHECK (action IN ('GRANT_PRO', 'REVOKE_PRO')),
    period_id UUID UNIQUE REFERENCES subscription_periods(id),
    days INTEGER CHECK (days BETWEEN 1 AND 366),
    reason TEXT NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (actor_user_id <> target_user_id),
    CHECK ((action = 'GRANT_PRO' AND period_id IS NOT NULL AND days IS NOT NULL)
        OR (action = 'REVOKE_PRO' AND period_id IS NULL AND days IS NULL))
);
CREATE INDEX idx_admin_plan_grants_target_created ON admin_plan_grants (target_user_id, created_at DESC);

CREATE TRIGGER trg_admin_plan_grants_append_only
    BEFORE UPDATE OR DELETE ON admin_plan_grants
    FOR EACH ROW EXECUTE FUNCTION prevent_admin_audit_mutation();
