-- Engagement of published posts as last read from Facebook, Instagram or Threads.
-- One row per published post (scheduled_posts row); it goes away with the post or its channel.
-- A NULL count means the platform did not return that metric (missing permission), not zero.

CREATE TABLE post_metrics (
    scheduled_post_id UUID PRIMARY KEY REFERENCES scheduled_posts(id) ON DELETE CASCADE,
    likes INTEGER CHECK (likes >= 0),
    comments INTEGER CHECK (comments >= 0),
    shares INTEGER CHECK (shares >= 0),
    views INTEGER CHECK (views >= 0),
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
