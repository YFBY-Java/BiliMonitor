CREATE TABLE IF NOT EXISTS bilibili_monitored_user (
    id BIGSERIAL PRIMARY KEY,
    mid BIGINT NOT NULL UNIQUE,
    nickname VARCHAR(160) NOT NULL,
    avatar_url TEXT,
    profile_url TEXT NOT NULL,
    current_follower_count BIGINT,
    following_count BIGINT,
    monitor_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    interval_seconds INTEGER NOT NULL DEFAULT 3600,
    next_collect_at TIMESTAMPTZ,
    last_snapshot_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_error_at TIMESTAMPTZ,
    last_error_type VARCHAR(80),
    last_error_message TEXT,
    source_endpoint VARCHAR(160),
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (mid > 0),
    CHECK (interval_seconds >= 300)
);

CREATE INDEX IF NOT EXISTS idx_bilibili_monitored_user_due
    ON bilibili_monitored_user (monitor_status, next_collect_at);

CREATE TABLE IF NOT EXISTS bilibili_follower_snapshot (
    id BIGSERIAL PRIMARY KEY,
    monitored_user_id BIGINT NOT NULL REFERENCES bilibili_monitored_user(id) ON DELETE CASCADE,
    mid BIGINT NOT NULL,
    follower_count BIGINT NOT NULL,
    following_count BIGINT,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    captured_bucket TIMESTAMPTZ NOT NULL,
    source_endpoint VARCHAR(160) NOT NULL,
    raw_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (monitored_user_id, captured_bucket)
);

CREATE INDEX IF NOT EXISTS idx_bilibili_follower_snapshot_lookup
    ON bilibili_follower_snapshot (monitored_user_id, captured_at);

CREATE INDEX IF NOT EXISTS idx_bilibili_follower_snapshot_mid_time
    ON bilibili_follower_snapshot (mid, captured_at);
