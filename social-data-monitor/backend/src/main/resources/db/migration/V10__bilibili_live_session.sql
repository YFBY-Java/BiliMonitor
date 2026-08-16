ALTER TABLE bilibili_live_danmaku_session
    ADD COLUMN IF NOT EXISTS connected_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS bilibili_live_session (
    id BIGSERIAL PRIMARY KEY,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    uid BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    platform_live_time TIMESTAMPTZ,
    live_key VARCHAR(200) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    start_detected_at TIMESTAMPTZ NOT NULL,
    start_source VARCHAR(64) NOT NULL,
    end_signal_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    end_detected_at TIMESTAMPTZ,
    end_source VARCHAR(64),
    last_live_observed_at TIMESTAMPTZ,
    last_observed_at TIMESTAMPTZ NOT NULL,
    title_at_start TEXT,
    title_at_end TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (uid > 0),
    CHECK (room_id > 0),
    CHECK (state IN ('OPEN', 'END_PENDING', 'CLOSED', 'INCOMPLETE')),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bilibili_live_session_active
    ON bilibili_live_session (monitor_id)
    WHERE state IN ('OPEN', 'END_PENDING');

CREATE UNIQUE INDEX IF NOT EXISTS ux_bilibili_live_session_platform_time
    ON bilibili_live_session (monitor_id, platform_live_time)
    WHERE platform_live_time IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_bilibili_live_session_live_key
    ON bilibili_live_session (monitor_id, live_key);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_session_monitor_started
    ON bilibili_live_session (monitor_id, started_at DESC);

CREATE TABLE IF NOT EXISTS bilibili_live_session_event (
    id BIGSERIAL PRIMARY KEY,
    live_session_id BIGINT NOT NULL REFERENCES bilibili_live_session(id) ON DELETE CASCADE,
    transport_session_id BIGINT REFERENCES bilibili_live_danmaku_session(id) ON DELETE SET NULL,
    receipt_ordinal BIGINT NOT NULL,
    monitor_id BIGINT NOT NULL REFERENCES bilibili_live_room_monitor(id) ON DELETE CASCADE,
    room_id BIGINT NOT NULL,
    event_key VARCHAR(240) NOT NULL,
    source_event_id VARCHAR(240),
    event_kind VARCHAR(64) NOT NULL,
    command VARCHAR(128),
    protocol_version INTEGER,
    sender_uid BIGINT,
    sender_name VARCHAR(200),
    medal_name VARCHAR(160),
    message_text TEXT,
    gift_id BIGINT,
    gift_name VARCHAR(200),
    gift_count BIGINT,
    coin_type VARCHAR(32),
    unit_price_milli_yuan BIGINT,
    paid_amount_milli_yuan BIGINT,
    paid BOOLEAN NOT NULL DEFAULT FALSE,
    guard_level INTEGER,
    amount_source VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    raw_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (live_session_id, event_key),
    CHECK (room_id > 0),
    CHECK (protocol_version IS NULL OR protocol_version >= 0),
    CHECK (receipt_ordinal IS NULL OR receipt_ordinal > 0),
    CHECK (sender_uid IS NULL OR sender_uid >= 0),
    CHECK (gift_id IS NULL OR gift_id >= 0),
    CHECK (gift_count IS NULL OR gift_count >= 0),
    CHECK (unit_price_milli_yuan IS NULL OR unit_price_milli_yuan >= 0),
    CHECK (paid_amount_milli_yuan IS NULL OR paid_amount_milli_yuan >= 0),
    CHECK (guard_level IS NULL OR guard_level >= 0)
);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_session_event_session_time
    ON bilibili_live_session_event (live_session_id, occurred_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_session_event_session_kind_time
    ON bilibili_live_session_event (live_session_id, event_kind, occurred_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_session_event_transport_time
    ON bilibili_live_session_event (transport_session_id, received_at ASC, id ASC)
    WHERE transport_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bilibili_live_session_event_monitor_kind_time
    ON bilibili_live_session_event (monitor_id, event_kind, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_bilibili_live_session_event_sender_time
    ON bilibili_live_session_event (sender_uid, occurred_at DESC)
    WHERE sender_uid IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bilibili_live_session_event_source
    ON bilibili_live_session_event (monitor_id, source_event_id)
    WHERE source_event_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_bilibili_live_session_event_strong_source
    ON bilibili_live_session_event (monitor_id, event_kind, source_event_id)
    WHERE source_event_id IS NOT NULL
      AND source_event_id NOT LIKE 'semantic:%';

WITH ordered_starts AS (
    SELECT
        start_event.*,
        LEAD(start_event.occurred_at) OVER (
            PARTITION BY start_event.monitor_id
            ORDER BY start_event.occurred_at, start_event.id
        ) AS next_started_at
    FROM bilibili_live_status_event start_event
    WHERE start_event.event_type = 'LIVE_STARTED'
), paired_boundaries AS (
    SELECT
        start_event.id AS start_event_id,
        start_event.monitor_id,
        start_event.uid,
        start_event.room_id,
        start_event.occurred_at AS started_at,
        start_event.title_after AS title_at_start,
        end_event.occurred_at AS ended_at,
        end_event.title_after AS title_at_end
    FROM ordered_starts start_event
    JOIN LATERAL (
        SELECT end_event.*
        FROM bilibili_live_status_event end_event
        WHERE end_event.monitor_id = start_event.monitor_id
          AND end_event.event_type = 'LIVE_ENDED'
          AND end_event.occurred_at >= start_event.occurred_at
          AND (
              start_event.next_started_at IS NULL
              OR end_event.occurred_at < start_event.next_started_at
          )
        ORDER BY end_event.occurred_at, end_event.id
        LIMIT 1
    ) end_event ON TRUE
)
INSERT INTO bilibili_live_session (
    monitor_id, uid, room_id, state, platform_live_time, live_key,
    started_at, start_detected_at, start_source,
    end_signal_at, ended_at, end_detected_at, end_source,
    last_live_observed_at, last_observed_at,
    title_at_start, title_at_end
)
SELECT
    paired.monitor_id,
    paired.uid,
    paired.room_id,
    'CLOSED',
    NULL,
    'status-event:' || paired.start_event_id,
    paired.started_at,
    paired.started_at,
    'STATUS_EVENT_BACKFILL',
    paired.ended_at,
    paired.ended_at,
    paired.ended_at,
    'STATUS_EVENT_BACKFILL',
    paired.started_at,
    paired.ended_at,
    paired.title_at_start,
    paired.title_at_end
FROM paired_boundaries paired
ON CONFLICT DO NOTHING;

WITH ordered_starts AS (
    SELECT
        start_event.*,
        LEAD(start_event.occurred_at) OVER (
            PARTITION BY start_event.monitor_id
            ORDER BY start_event.occurred_at, start_event.id
        ) AS next_started_at
    FROM bilibili_live_status_event start_event
    WHERE start_event.event_type = 'LIVE_STARTED'
), unpaired_starts AS (
    SELECT start_event.*
    FROM ordered_starts start_event
    WHERE NOT EXISTS (
        SELECT 1
        FROM bilibili_live_status_event end_event
        WHERE end_event.monitor_id = start_event.monitor_id
          AND end_event.event_type = 'LIVE_ENDED'
          AND end_event.occurred_at >= start_event.occurred_at
          AND (
              start_event.next_started_at IS NULL
              OR end_event.occurred_at < start_event.next_started_at
          )
    )
      AND NOT (
          start_event.next_started_at IS NULL
          AND EXISTS (
              SELECT 1
              FROM bilibili_live_room_monitor current_room
              WHERE current_room.id = start_event.monitor_id
                AND current_room.live_status = 1
          )
      )
)
INSERT INTO bilibili_live_session (
    monitor_id, uid, room_id, state, platform_live_time, live_key,
    started_at, start_detected_at, start_source,
    end_signal_at, ended_at, end_detected_at, end_source,
    last_live_observed_at, last_observed_at,
    title_at_start, title_at_end
)
SELECT
    unpaired.monitor_id,
    unpaired.uid,
    unpaired.room_id,
    'INCOMPLETE',
    NULL,
    'status-event:' || unpaired.id,
    unpaired.occurred_at,
    unpaired.occurred_at,
    'STATUS_EVENT_BACKFILL_INCOMPLETE',
    NULL,
    NULL,
    NULL,
    'UNKNOWN',
    unpaired.occurred_at,
    unpaired.occurred_at,
    unpaired.title_after,
    NULL
FROM unpaired_starts unpaired
ON CONFLICT DO NOTHING;

INSERT INTO bilibili_live_session (
    monitor_id, uid, room_id, state, platform_live_time, live_key,
    started_at, start_detected_at, start_source,
    last_live_observed_at, last_observed_at,
    title_at_start
)
SELECT
    room.id,
    room.uid,
    room.room_id,
    'OPEN',
    room.live_time,
    CASE
        WHEN room.live_time IS NOT NULL
            THEN 'platform:' || EXTRACT(EPOCH FROM room.live_time)::bigint
        ELSE 'migration-current:' || room.id
    END,
    COALESCE(room.live_time, room.last_success_at, room.last_snapshot_at, room.updated_at, now()),
    COALESCE(room.last_success_at, room.last_snapshot_at, room.updated_at, now()),
    'MIGRATION_CURRENT_STATE',
    COALESCE(room.last_success_at, room.last_snapshot_at, room.updated_at, now()),
    COALESCE(room.last_success_at, room.last_snapshot_at, room.updated_at, now()),
    room.title
FROM bilibili_live_room_monitor room
WHERE room.live_status = 1
ON CONFLICT DO NOTHING;
