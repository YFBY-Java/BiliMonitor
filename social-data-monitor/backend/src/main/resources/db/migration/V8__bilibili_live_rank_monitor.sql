CREATE TABLE IF NOT EXISTS bilibili_live_rank_snapshot (
    id BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    room_id BIGINT NOT NULL,
    ruid BIGINT NOT NULL,
    rank_family VARCHAR(32) NOT NULL,
    rank_type VARCHAR(64) NOT NULL,
    rank_switch VARCHAR(64),
    period_scope VARCHAR(32),
    page_no INTEGER NOT NULL,
    page_size INTEGER NOT NULL,
    total_count BIGINT,
    count_text VARCHAR(64),
    value_text VARCHAR(64),
    remind_msg VARCHAR(512),
    source_endpoint VARCHAR(256) NOT NULL,
    signed_required BOOLEAN NOT NULL DEFAULT FALSE,
    captured_at TIMESTAMPTZ NOT NULL,
    captured_bucket TIMESTAMPTZ NOT NULL,
    raw_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bilibili_live_rank_snapshot_bucket
    ON bilibili_live_rank_snapshot (
        monitor_id,
        rank_family,
        rank_type,
        (COALESCE(rank_switch, '')),
        (COALESCE(period_scope, '')),
        captured_bucket,
        page_no
    );

CREATE INDEX IF NOT EXISTS idx_bilibili_live_rank_snapshot_room_latest
    ON bilibili_live_rank_snapshot (monitor_id, rank_family, rank_type, captured_at DESC);

CREATE TABLE IF NOT EXISTS bilibili_live_rank_entry (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES bilibili_live_rank_snapshot(id) ON DELETE CASCADE,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    room_id BIGINT NOT NULL,
    ruid BIGINT NOT NULL,
    user_uid BIGINT,
    rank_no INTEGER,
    entry_kind VARCHAR(32) NOT NULL,
    display_name VARCHAR(512),
    face_url TEXT,
    score BIGINT,
    guard_level INTEGER,
    wealth_level INTEGER,
    medal_name VARCHAR(128),
    medal_level INTEGER,
    medal_ruid BIGINT,
    medal_is_light INTEGER,
    guard_expired_text VARCHAR(128),
    accompany_days INTEGER,
    raw_entry_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_rank_entry_snapshot
    ON bilibili_live_rank_entry (snapshot_id, rank_no NULLS LAST);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_rank_entry_user
    ON bilibili_live_rank_entry (user_uid, room_id, created_at DESC);
