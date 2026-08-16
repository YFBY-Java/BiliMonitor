package com.socialmonitor.bilibili.live.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BilibiliLiveSessionMigrationTests {

    @Test
    void definesLiveSessionTableAndActiveSessionConstraints() throws Exception {
        String sql = migrationSql();

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS bilibili_live_session")
                .contains("state VARCHAR(32) NOT NULL")
                .contains("CHECK (state IN ('OPEN', 'END_PENDING', 'CLOSED', 'INCOMPLETE'))")
                .contains("CREATE UNIQUE INDEX IF NOT EXISTS ux_bilibili_live_session_active")
                .contains("WHERE state IN ('OPEN', 'END_PENDING')")
                .contains("CREATE UNIQUE INDEX IF NOT EXISTS ux_bilibili_live_session_platform_time")
                .contains("WHERE platform_live_time IS NOT NULL");
    }

    @Test
    void definesUnifiedSessionEventTableWithReceiptProvenanceAndScopedIdempotency() throws Exception {
        String sql = migrationSql();

        assertThat(sql)
                .contains("ALTER TABLE bilibili_live_danmaku_session")
                .contains("ADD COLUMN IF NOT EXISTS connected_at TIMESTAMPTZ")
                .contains("CREATE TABLE IF NOT EXISTS bilibili_live_session_event")
                .contains("live_session_id BIGINT NOT NULL REFERENCES bilibili_live_session(id) ON DELETE CASCADE")
                .contains("transport_session_id BIGINT REFERENCES bilibili_live_danmaku_session(id) ON DELETE SET NULL")
                .contains("receipt_ordinal BIGINT")
                .contains("event_key VARCHAR(240) NOT NULL")
                .contains("raw_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb")
                .contains("UNIQUE (live_session_id, event_key)")
                .contains("CREATE UNIQUE INDEX IF NOT EXISTS ux_bilibili_live_session_event_strong_source")
                .contains("source_event_id NOT LIKE 'semantic:%'")
                .contains("CHECK (protocol_version IS NULL OR protocol_version >= 0)")
                .contains("CHECK (receipt_ordinal IS NULL OR receipt_ordinal > 0)")
                .contains("CHECK (gift_count IS NULL OR gift_count >= 0)")
                .contains("CHECK (paid_amount_milli_yuan IS NULL OR paid_amount_milli_yuan >= 0)")
                .contains("CREATE INDEX IF NOT EXISTS idx_bilibili_live_session_event_session_time")
                .contains("CREATE INDEX IF NOT EXISTS idx_bilibili_live_session_event_session_kind_time")
                .contains("CREATE INDEX IF NOT EXISTS idx_bilibili_live_session_event_monitor_kind_time");
    }

    @Test
    void backfillsPairedAndDiscoverableIncompleteHistoricalBoundariesAndCurrentLiveRooms() throws Exception {
        String sql = migrationSql();

        assertThat(sql)
                .contains("WITH ordered_starts AS")
                .contains("event_type = 'LIVE_STARTED'")
                .contains("event_type = 'LIVE_ENDED'")
                .contains("end_event.occurred_at < start_event.next_started_at")
                .contains("'STATUS_EVENT_BACKFILL'")
                .contains("'INCOMPLETE'")
                .contains("'STATUS_EVENT_BACKFILL_INCOMPLETE'")
                .contains("WHERE room.live_status = 1")
                .contains("'MIGRATION_CURRENT_STATE'")
                .contains("ON CONFLICT DO NOTHING");
    }

    private String migrationSql() throws Exception {
        URL resource = getClass().getResource("/db/migration/V10__bilibili_live_session.sql");
        assertThat(resource).as("V10 live session migration").isNotNull();
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
    }
}
