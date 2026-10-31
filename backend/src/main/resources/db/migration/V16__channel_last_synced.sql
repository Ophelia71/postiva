-- When Postiva last read a channel's posts and engagement back; drives the automatic refresh.
ALTER TABLE social_accounts ADD COLUMN last_synced_at TIMESTAMPTZ;
