-- First-party, aggregate-oriented website analytics. No IP, user agent or full URL is stored.
SET LOCAL lock_timeout = '5s';

CREATE TABLE web_analytics_sessions (
    id UUID PRIMARY KEY,
    visitor_id UUID NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    landing_path VARCHAR(200) NOT NULL,
    source VARCHAR(20) NOT NULL CHECK (source IN
        ('DIRECT', 'ORGANIC', 'SOCIAL', 'REFERRAL', 'PAID', 'EMAIL', 'OTHER')),
    is_new_visitor BOOLEAN NOT NULL,
    page_views INTEGER NOT NULL DEFAULT 0 CHECK (page_views BETWEEN 0 AND 100),
    engaged BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_web_analytics_sessions_started ON web_analytics_sessions (started_at);
CREATE INDEX idx_web_analytics_sessions_visitor ON web_analytics_sessions (visitor_id, started_at);
CREATE INDEX idx_web_analytics_sessions_user ON web_analytics_sessions (user_id);

CREATE TABLE web_analytics_page_views (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES web_analytics_sessions(id) ON DELETE CASCADE,
    path VARCHAR(200) NOT NULL,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    max_scroll SMALLINT NOT NULL DEFAULT 0 CHECK (max_scroll BETWEEN 0 AND 100)
);
CREATE INDEX idx_web_analytics_page_views_session ON web_analytics_page_views (session_id);
