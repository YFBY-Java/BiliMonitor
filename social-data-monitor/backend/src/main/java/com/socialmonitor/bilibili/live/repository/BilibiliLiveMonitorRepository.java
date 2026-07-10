package com.socialmonitor.bilibili.live.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.live.domain.BilibiliFetchedLiveRoomSnapshot;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomSnapshot;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveStatusEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveMonitorRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BilibiliLiveMonitorRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<BilibiliLiveRoomMonitor> findAll() {
        String sql = """
                SELECT * FROM bilibili_live_room_monitor
                ORDER BY monitor_status ASC, live_status DESC, online_count DESC NULLS LAST, id ASC
                """;
        return jdbcTemplate.query(sql, Map.of(), this::mapMonitor);
    }

    public Optional<BilibiliLiveRoomMonitor> findById(Long id) {
        String sql = "SELECT * FROM bilibili_live_room_monitor WHERE id = :id";
        return jdbcTemplate.query(sql, Map.of("id", id), this::mapMonitor).stream().findFirst();
    }

    public Optional<BilibiliLiveRoomMonitor> findByUid(Long uid) {
        String sql = "SELECT * FROM bilibili_live_room_monitor WHERE uid = :uid";
        return jdbcTemplate.query(sql, Map.of("uid", uid), this::mapMonitor).stream().findFirst();
    }

    public Optional<BilibiliLiveRoomMonitor> findByRoomId(Long roomId) {
        String sql = "SELECT * FROM bilibili_live_room_monitor WHERE room_id = :roomId";
        return jdbcTemplate.query(sql, Map.of("roomId", roomId), this::mapMonitor).stream().findFirst();
    }

    public List<BilibiliLiveRoomMonitor> findDueRooms(OffsetDateTime now, int limit) {
        String sql = """
                SELECT * FROM bilibili_live_room_monitor
                WHERE monitor_status = 'ACTIVE'
                  AND (next_collect_at IS NULL OR next_collect_at <= :now)
                  AND (backoff_until IS NULL OR backoff_until <= :now)
                ORDER BY COALESCE(next_collect_at, created_at) ASC
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, Map.of("now", now, "limit", limit), this::mapMonitor);
    }

    public BilibiliLiveRoomMonitor upsertMonitorFromSnapshot(
            BilibiliFetchedLiveRoomSnapshot snapshot,
            int intervalSeconds,
            OffsetDateTime nextCollectAt
    ) {
        String sql = """
                INSERT INTO bilibili_live_room_monitor (
                    uid, room_id, short_id, uname, face_url, title, cover_url, keyframe_url,
                    area_id, area_name, parent_area_id, parent_area_name, live_status, live_time,
                    online_count, attention_count, monitor_status, interval_seconds, next_collect_at,
                    last_snapshot_at, last_success_at, source_endpoint, updated_at
                )
                VALUES (
                    :uid, :roomId, :shortId, :uname, :faceUrl, :title, :coverUrl, :keyframeUrl,
                    :areaId, :areaName, :parentAreaId, :parentAreaName, :liveStatus, :liveTime,
                    :onlineCount, :attentionCount, 'ACTIVE', :intervalSeconds, :nextCollectAt,
                    :capturedAt, :capturedAt, :sourceEndpoint, now()
                )
                ON CONFLICT (uid) DO UPDATE SET
                    room_id = EXCLUDED.room_id,
                    short_id = EXCLUDED.short_id,
                    uname = EXCLUDED.uname,
                    face_url = EXCLUDED.face_url,
                    title = EXCLUDED.title,
                    cover_url = EXCLUDED.cover_url,
                    keyframe_url = EXCLUDED.keyframe_url,
                    area_id = EXCLUDED.area_id,
                    area_name = EXCLUDED.area_name,
                    parent_area_id = EXCLUDED.parent_area_id,
                    parent_area_name = EXCLUDED.parent_area_name,
                    live_status = EXCLUDED.live_status,
                    live_time = EXCLUDED.live_time,
                    online_count = EXCLUDED.online_count,
                    attention_count = EXCLUDED.attention_count,
                    monitor_status = 'ACTIVE',
                    interval_seconds = EXCLUDED.interval_seconds,
                    next_collect_at = EXCLUDED.next_collect_at,
                    last_snapshot_at = EXCLUDED.last_snapshot_at,
                    last_success_at = EXCLUDED.last_success_at,
                    last_error_at = NULL,
                    last_error_type = NULL,
                    last_error_message = NULL,
                    backoff_until = NULL,
                    source_endpoint = EXCLUDED.source_endpoint,
                    updated_at = now()
                RETURNING *
                """;
        return jdbcTemplate.queryForObject(sql, snapshotParams(snapshot)
                .addValue("intervalSeconds", intervalSeconds)
                .addValue("nextCollectAt", nextCollectAt), this::mapMonitor);
    }

    public void updateSuccessfulSnapshot(Long monitorId, BilibiliFetchedLiveRoomSnapshot snapshot, OffsetDateTime nextCollectAt) {
        String sql = """
                UPDATE bilibili_live_room_monitor SET
                    uid = :uid,
                    room_id = :roomId,
                    short_id = :shortId,
                    uname = :uname,
                    face_url = :faceUrl,
                    title = :title,
                    cover_url = :coverUrl,
                    keyframe_url = :keyframeUrl,
                    area_id = :areaId,
                    area_name = :areaName,
                    parent_area_id = :parentAreaId,
                    parent_area_name = :parentAreaName,
                    live_status = :liveStatus,
                    live_time = :liveTime,
                    online_count = :onlineCount,
                    attention_count = :attentionCount,
                    next_collect_at = :nextCollectAt,
                    last_snapshot_at = :capturedAt,
                    last_success_at = :capturedAt,
                    last_error_at = NULL,
                    last_error_type = NULL,
                    last_error_message = NULL,
                    backoff_until = NULL,
                    source_endpoint = :sourceEndpoint,
                    updated_at = now()
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, snapshotParams(snapshot)
                .addValue("id", monitorId)
                .addValue("nextCollectAt", nextCollectAt));
    }

    public void markFailure(Long monitorId, BilibiliFetchException exception, OffsetDateTime nextCollectAt) {
        String sql = """
                UPDATE bilibili_live_room_monitor SET
                    next_collect_at = :nextCollectAt,
                    last_error_at = :now,
                    last_error_type = :errorType,
                    last_error_message = :errorMessage,
                    backoff_until = :nextCollectAt,
                    updated_at = now()
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, Map.of(
                "id", monitorId,
                "nextCollectAt", nextCollectAt,
                "now", OffsetDateTime.now(),
                "errorType", exception.errorType().name(),
                "errorMessage", truncate(exception.getMessage(), 900)
        ));
    }

    public void updateMonitor(Long monitorId, Integer intervalSeconds, Boolean enabled, OffsetDateTime nextCollectAt) {
        StringBuilder sql = new StringBuilder("UPDATE bilibili_live_room_monitor SET updated_at = now()");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", monitorId);

        if (intervalSeconds != null) {
            sql.append(", interval_seconds = :intervalSeconds");
            params.addValue("intervalSeconds", intervalSeconds);
        }

        if (enabled != null) {
            sql.append(", monitor_status = :monitorStatus");
            params.addValue("monitorStatus", enabled ? "ACTIVE" : "PAUSED");
            if (enabled) {
                sql.append(", next_collect_at = now()");
            }
        } else if (intervalSeconds != null) {
            sql.append("""
                    , next_collect_at = CASE
                        WHEN monitor_status = 'ACTIVE' THEN :nextCollectAt
                        ELSE next_collect_at
                    END
                    """);
            params.addValue("nextCollectAt", nextCollectAt);
        }

        sql.append(" WHERE id = :id");
        jdbcTemplate.update(sql.toString(), params);
    }

    public void delete(Long monitorId) {
        jdbcTemplate.update("DELETE FROM bilibili_live_room_monitor WHERE id = :id", Map.of("id", monitorId));
    }

    public void upsertSnapshot(Long monitorId, BilibiliFetchedLiveRoomSnapshot snapshot) {
        String sql = """
                INSERT INTO bilibili_live_room_snapshot (
                    monitor_id, uid, room_id, live_status, title, area_id, area_name, parent_area_id,
                    parent_area_name, online_count, attention_count, live_time, source_endpoint,
                    raw_payload_json, captured_at, captured_bucket
                )
                VALUES (
                    :monitorId, :uid, :roomId, :liveStatus, :title, :areaId, :areaName, :parentAreaId,
                    :parentAreaName, :onlineCount, :attentionCount, :liveTime, :sourceEndpoint,
                    CAST(:rawPayload AS jsonb), :capturedAt, :capturedBucket
                )
                ON CONFLICT (monitor_id, captured_bucket) DO UPDATE SET
                    live_status = EXCLUDED.live_status,
                    title = EXCLUDED.title,
                    area_id = EXCLUDED.area_id,
                    area_name = EXCLUDED.area_name,
                    parent_area_id = EXCLUDED.parent_area_id,
                    parent_area_name = EXCLUDED.parent_area_name,
                    online_count = EXCLUDED.online_count,
                    attention_count = EXCLUDED.attention_count,
                    live_time = EXCLUDED.live_time,
                    source_endpoint = EXCLUDED.source_endpoint,
                    raw_payload_json = EXCLUDED.raw_payload_json,
                    captured_at = EXCLUDED.captured_at
                """;
        jdbcTemplate.update(sql, snapshotParams(snapshot)
                .addValue("monitorId", monitorId)
                .addValue("capturedBucket", snapshot.fetchedAt().truncatedTo(ChronoUnit.SECONDS)));
    }

    public void insertStatusEvent(
            Long monitorId,
            Long uid,
            Long roomId,
            String eventType,
            Integer fromLiveStatus,
            Integer toLiveStatus,
            String titleBefore,
            String titleAfter,
            Long onlineCount
    ) {
        String sql = """
                INSERT INTO bilibili_live_status_event (
                    monitor_id, uid, room_id, event_type, from_live_status, to_live_status,
                    title_before, title_after, online_count, occurred_at
                )
                VALUES (
                    :monitorId, :uid, :roomId, :eventType, :fromLiveStatus, :toLiveStatus,
                    :titleBefore, :titleAfter, :onlineCount, now()
                )
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("uid", uid)
                .addValue("roomId", roomId)
                .addValue("eventType", eventType)
                .addValue("fromLiveStatus", fromLiveStatus)
                .addValue("toLiveStatus", toLiveStatus)
                .addValue("titleBefore", titleBefore)
                .addValue("titleAfter", titleAfter)
                .addValue("onlineCount", onlineCount));
    }

    public List<BilibiliLiveRoomSnapshot> findSnapshots(Long monitorId, OffsetDateTime from, OffsetDateTime to, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("limit", limit);
        StringBuilder whereClause = new StringBuilder("WHERE monitor_id = :monitorId\n");
        if (from != null) {
            whereClause.append("  AND captured_at >= :fromTime\n");
            params.addValue("fromTime", from);
        }
        if (to != null) {
            whereClause.append("  AND captured_at <= :toTime\n");
            params.addValue("toTime", to);
        }
        String sql = """
                SELECT * FROM (
                    SELECT * FROM bilibili_live_room_snapshot
                    %s
                    ORDER BY captured_at DESC
                    LIMIT :limit
                ) limited
                ORDER BY captured_at ASC
                """.formatted(whereClause);
        return jdbcTemplate.query(sql, params, this::mapSnapshot);
    }

    public List<BilibiliLiveRoomSnapshot> findRecentSnapshots(Long monitorId, int limit) {
        String sql = """
                SELECT * FROM (
                    SELECT * FROM bilibili_live_room_snapshot
                    WHERE monitor_id = :monitorId
                    ORDER BY captured_at DESC
                    LIMIT :limit
                ) limited
                ORDER BY captured_at ASC
                """;
        return jdbcTemplate.query(sql, Map.of("monitorId", monitorId, "limit", limit), this::mapSnapshot);
    }

    public long countTodayLiveStarts() {
        OffsetDateTime dayStart = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.ofHours(8));
        String sql = """
                SELECT COUNT(*) FROM bilibili_live_status_event
                WHERE event_type = 'LIVE_STARTED' AND occurred_at >= :dayStart
                """;
        Long count = jdbcTemplate.queryForObject(sql, Map.of("dayStart", dayStart), Long.class);
        return count == null ? 0L : count;
    }

    public List<BilibiliLiveStatusEvent> findRecentEvents(int limit) {
        String sql = """
                SELECT * FROM bilibili_live_status_event
                ORDER BY occurred_at DESC
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, Map.of("limit", limit), this::mapEvent);
    }

    public void recordApiCall(
            String endpointKey,
            Integer statusCode,
            long durationMs,
            String errorType,
            boolean retryable,
            Map<String, Object> requestMeta,
            Map<String, Object> responseMeta
    ) {
        String sql = """
                INSERT INTO api_call_log (
                    platform_id, endpoint_key, status_code, duration_ms, error_type, retryable,
                    request_meta_json, response_meta_json
                )
                VALUES (
                    (SELECT id FROM platform WHERE code = 'bilibili'),
                    :endpointKey, :statusCode, :durationMs, :errorType, :retryable,
                    CAST(:requestMeta AS jsonb), CAST(:responseMeta AS jsonb)
                )
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("endpointKey", endpointKey)
                .addValue("statusCode", statusCode)
                .addValue("durationMs", (int) Math.min(durationMs, Integer.MAX_VALUE))
                .addValue("errorType", errorType)
                .addValue("retryable", retryable)
                .addValue("requestMeta", toJson(requestMeta))
                .addValue("responseMeta", toJson(responseMeta)));
    }

    private MapSqlParameterSource snapshotParams(BilibiliFetchedLiveRoomSnapshot snapshot) {
        return new MapSqlParameterSource()
                .addValue("uid", snapshot.uid())
                .addValue("roomId", snapshot.roomId())
                .addValue("shortId", snapshot.shortId())
                .addValue("uname", snapshot.uname())
                .addValue("faceUrl", snapshot.faceUrl())
                .addValue("title", snapshot.title())
                .addValue("coverUrl", snapshot.coverUrl())
                .addValue("keyframeUrl", snapshot.keyframeUrl())
                .addValue("areaId", snapshot.areaId())
                .addValue("areaName", snapshot.areaName())
                .addValue("parentAreaId", snapshot.parentAreaId())
                .addValue("parentAreaName", snapshot.parentAreaName())
                .addValue("liveStatus", snapshot.liveStatus())
                .addValue("liveTime", snapshot.liveTime())
                .addValue("onlineCount", snapshot.onlineCount())
                .addValue("attentionCount", snapshot.attentionCount())
                .addValue("capturedAt", snapshot.fetchedAt())
                .addValue("sourceEndpoint", snapshot.sourceEndpoint())
                .addValue("rawPayload", snapshot.rawPayload() == null || snapshot.rawPayload().isBlank() ? "{}" : snapshot.rawPayload());
    }

    private BilibiliLiveRoomMonitor mapMonitor(ResultSet rs, int rowNum) throws SQLException {
        return new BilibiliLiveRoomMonitor(
                rs.getLong("id"),
                rs.getLong("uid"),
                rs.getLong("room_id"),
                nullableLong(rs, "short_id"),
                rs.getString("uname"),
                rs.getString("face_url"),
                rs.getString("title"),
                rs.getString("cover_url"),
                rs.getString("keyframe_url"),
                nullableLong(rs, "area_id"),
                rs.getString("area_name"),
                nullableLong(rs, "parent_area_id"),
                rs.getString("parent_area_name"),
                rs.getInt("live_status"),
                offsetDateTime(rs, "live_time"),
                nullableLong(rs, "online_count"),
                nullableLong(rs, "attention_count"),
                rs.getString("monitor_status"),
                rs.getInt("interval_seconds"),
                offsetDateTime(rs, "next_collect_at"),
                offsetDateTime(rs, "last_snapshot_at"),
                offsetDateTime(rs, "last_success_at"),
                offsetDateTime(rs, "last_error_at"),
                rs.getString("last_error_type"),
                rs.getString("last_error_message"),
                offsetDateTime(rs, "backoff_until"),
                rs.getString("source_endpoint"),
                offsetDateTime(rs, "created_at"),
                offsetDateTime(rs, "updated_at")
        );
    }

    private BilibiliLiveRoomSnapshot mapSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new BilibiliLiveRoomSnapshot(
                rs.getLong("id"),
                rs.getLong("monitor_id"),
                rs.getLong("uid"),
                rs.getLong("room_id"),
                rs.getInt("live_status"),
                rs.getString("title"),
                nullableLong(rs, "area_id"),
                rs.getString("area_name"),
                nullableLong(rs, "parent_area_id"),
                rs.getString("parent_area_name"),
                nullableLong(rs, "online_count"),
                nullableLong(rs, "attention_count"),
                offsetDateTime(rs, "live_time"),
                rs.getString("source_endpoint"),
                offsetDateTime(rs, "captured_at"),
                offsetDateTime(rs, "captured_bucket")
        );
    }

    private BilibiliLiveStatusEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new BilibiliLiveStatusEvent(
                rs.getLong("id"),
                rs.getLong("monitor_id"),
                rs.getLong("uid"),
                rs.getLong("room_id"),
                rs.getString("event_type"),
                nullableInteger(rs, "from_live_status"),
                nullableInteger(rs, "to_live_status"),
                rs.getString("title_before"),
                rs.getString("title_after"),
                nullableLong(rs, "online_count"),
                offsetDateTime(rs, "occurred_at")
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(new HashMap<>(value));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
