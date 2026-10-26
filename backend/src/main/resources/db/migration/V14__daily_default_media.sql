-- Default media for daily rules (required for Instagram, optional for Facebook and Threads).
-- Each generated post attaches these files through FileService.attachToPost, which copies a file
-- row when it already belongs to another post, so the same images can be reused every day.
ALTER TABLE automation_rules ADD COLUMN default_media_ids JSONB NOT NULL DEFAULT '[]'::jsonb;
