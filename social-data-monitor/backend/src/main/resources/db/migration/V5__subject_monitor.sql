CREATE TABLE IF NOT EXISTS monitored_subject (
    id BIGSERIAL PRIMARY KEY,
    display_name VARCHAR(160) NOT NULL,
    avatar_url TEXT,
    remark TEXT,
    tags_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    monitor_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    health_score NUMERIC(6, 2),
    last_success_at TIMESTAMPTZ,
    last_event_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (monitor_status IN ('ACTIVE', 'PAUSED'))
);

CREATE INDEX IF NOT EXISTS idx_monitored_subject_status
    ON monitored_subject (monitor_status);

CREATE TABLE IF NOT EXISTS subject_bilibili_binding (
    id BIGSERIAL PRIMARY KEY,
    subject_id BIGINT NOT NULL REFERENCES monitored_subject(id) ON DELETE CASCADE,
    bilibili_user_monitor_id BIGINT REFERENCES bilibili_monitored_user(id) ON DELETE SET NULL,
    bilibili_live_room_monitor_id BIGINT REFERENCES bilibili_live_room_monitor(id) ON DELETE SET NULL,
    mid BIGINT,
    room_id BIGINT,
    enabled_capabilities_json JSONB NOT NULL DEFAULT '["follower", "live_heat"]'::jsonb,
    danmu_enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (subject_id),
    CHECK (mid IS NULL OR mid > 0),
    CHECK (room_id IS NULL OR room_id > 0)
);

CREATE INDEX IF NOT EXISTS idx_subject_bilibili_binding_user
    ON subject_bilibili_binding (bilibili_user_monitor_id);

CREATE INDEX IF NOT EXISTS idx_subject_bilibili_binding_live
    ON subject_bilibili_binding (bilibili_live_room_monitor_id);

CREATE TABLE IF NOT EXISTS subject_widget_layout (
    id BIGSERIAL PRIMARY KEY,
    subject_id BIGINT NOT NULL REFERENCES monitored_subject(id) ON DELETE CASCADE,
    widget_key VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    position_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    settings_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (subject_id, widget_key)
);

CREATE INDEX IF NOT EXISTS idx_subject_widget_layout_subject
    ON subject_widget_layout (subject_id);
