CREATE TABLE IF NOT EXISTS bilibili_live_room_monitor (
    id BIGSERIAL PRIMARY KEY,
    uid BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    short_id BIGINT,
    uname VARCHAR(160) NOT NULL,
    face_url TEXT,
    title TEXT,
    cover_url TEXT,
    keyframe_url TEXT,
    area_id BIGINT,
    area_name VARCHAR(120),
    parent_area_id BIGINT,
    parent_area_name VARCHAR(120),
    live_status SMALLINT NOT NULL DEFAULT 0,
    live_time TIMESTAMPTZ,
    online_count BIGINT,
    attention_count BIGINT,
    monitor_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    interval_seconds INTEGER NOT NULL DEFAULT 300,
    next_collect_at TIMESTAMPTZ,
    last_snapshot_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_error_at TIMESTAMPTZ,
    last_error_type VARCHAR(80),
    last_error_message TEXT,
    backoff_until TIMESTAMPTZ,
    source_endpoint VARCHAR(160),
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (uid),
    UNIQUE (room_id),
    CHECK (uid > 0),
    CHECK (room_id > 0),
    CHECK (interval_seconds BETWEEN 1 AND 2592000)
);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_room_monitor_due
    ON bilibili_live_room_monitor (monitor_status, next_collect_at);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_room_monitor_status
    ON bilibili_live_room_monitor (live_status);

CREATE TABLE IF NOT EXISTS bilibili_live_room_snapshot (
    id BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    uid BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    live_status SMALLINT NOT NULL,
    title TEXT,
    area_id BIGINT,
    area_name VARCHAR(120),
    parent_area_id BIGINT,
    parent_area_name VARCHAR(120),
    online_count BIGINT,
    attention_count BIGINT,
    live_time TIMESTAMPTZ,
    source_endpoint VARCHAR(160) NOT NULL,
    raw_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    captured_bucket TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (monitor_id, captured_bucket)
);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_room_snapshot_room_time
    ON bilibili_live_room_snapshot (room_id, captured_at DESC);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_room_snapshot_monitor_time
    ON bilibili_live_room_snapshot (monitor_id, captured_at DESC);

CREATE TABLE IF NOT EXISTS bilibili_live_status_event (
    id BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    uid BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    from_live_status SMALLINT,
    to_live_status SMALLINT,
    title_before TEXT,
    title_after TEXT,
    online_count BIGINT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    extension_json JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_status_event_time
    ON bilibili_live_status_event (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_status_event_room_time
    ON bilibili_live_status_event (room_id, occurred_at DESC);
