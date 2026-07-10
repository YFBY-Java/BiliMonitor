CREATE TABLE IF NOT EXISTS bilibili_live_danmaku_session (
    id BIGSERIAL PRIMARY KEY,
    live_room_monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    room_id BIGINT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    connect_host VARCHAR(200),
    reconnect_count INTEGER NOT NULL DEFAULT 0,
    last_heartbeat_at TIMESTAMPTZ,
    last_error_at TIMESTAMPTZ,
    last_error_type VARCHAR(80),
    last_error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bilibili_danmaku_session_room_time
    ON bilibili_live_danmaku_session (live_room_monitor_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_bilibili_danmaku_session_status
    ON bilibili_live_danmaku_session (status);

CREATE TABLE IF NOT EXISTS bilibili_live_danmaku_metric_bucket (
    id BIGSERIAL PRIMARY KEY,
    live_room_monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    session_id BIGINT REFERENCES bilibili_live_danmaku_session(id) ON DELETE SET NULL,
    room_id BIGINT NOT NULL,
    bucket_start TIMESTAMPTZ NOT NULL,
    bucket_seconds INTEGER NOT NULL DEFAULT 60,
    danmu_count INTEGER NOT NULL DEFAULT 0,
    like_count BIGINT,
    like_increment BIGINT NOT NULL DEFAULT 0,
    watched_count BIGINT,
    heartbeat_popularity BIGINT,
    gift_count INTEGER NOT NULL DEFAULT 0,
    super_chat_count INTEGER NOT NULL DEFAULT 0,
    raw_event_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (live_room_monitor_id, bucket_start, bucket_seconds)
);

CREATE INDEX IF NOT EXISTS idx_bilibili_danmaku_bucket_room_time
    ON bilibili_live_danmaku_metric_bucket (live_room_monitor_id, bucket_start DESC);

CREATE TABLE IF NOT EXISTS bilibili_live_danmaku_recent (
    id BIGSERIAL PRIMARY KEY,
    live_room_monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    room_id BIGINT NOT NULL,
    message_text TEXT NOT NULL,
    display_name VARCHAR(160),
    medal_name VARCHAR(80),
    sent_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bilibili_danmaku_recent_room_time
    ON bilibili_live_danmaku_recent (live_room_monitor_id, sent_at DESC);
