-- Weekly content plans ("Daily"): one AI-planned week per social account, reviewed once, then scheduled.
-- Builds on content_calendars/calendar_items; the old /api/calendars generator keeps working with the defaults below.

ALTER TABLE content_calendars
    ADD COLUMN social_account_id UUID REFERENCES social_accounts(id) ON DELETE SET NULL,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'READY',
    ADD COLUMN brief JSONB,
    ADD COLUMN performance JSONB,
    ADD COLUMN strategy TEXT,
    ADD COLUMN timezone VARCHAR(64),
    ADD COLUMN error TEXT,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_content_calendars_status CHECK (status IN ('GENERATING', 'READY', 'APPROVED', 'FAILED'));

ALTER TABLE calendar_items
    ADD COLUMN scheduled_time TIMESTAMP WITH TIME ZONE,
    ADD COLUMN reason TEXT,
    ADD COLUMN scheduled_post_id UUID REFERENCES scheduled_posts(id) ON DELETE SET NULL,
    ADD COLUMN error TEXT;

-- Databases created from the old reset script and from V1 name the status check differently; drop whichever exists.
DO $$
DECLARE
    item_check RECORD;
BEGIN
    FOR item_check IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'calendar_items'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE calendar_items DROP CONSTRAINT %I', item_check.conname);
    END LOOP;
END $$;

ALTER TABLE calendar_items ADD CONSTRAINT chk_calendar_items_status
    CHECK (status IN ('PLANNED', 'GENERATING', 'DRAFT', 'SKIPPED', 'SCHEDULED', 'PUBLISHED', 'FAILED'));

CREATE INDEX idx_content_calendars_user_created ON content_calendars (user_id, created_at DESC);
CREATE INDEX idx_content_calendars_generating ON content_calendars (updated_at) WHERE status = 'GENERATING';
