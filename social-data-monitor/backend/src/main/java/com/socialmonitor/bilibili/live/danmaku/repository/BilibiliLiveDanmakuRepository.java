package com.socialmonitor.bilibili.live.danmaku.repository;

import com.socialmonitor.bilibili.live.danmaku.domain.BilibiliLiveDanmakuMetricBucket;
import com.socialmonitor.bilibili.live.danmaku.domain.BilibiliLiveDanmakuRecent;
import com.socialmonitor.bilibili.live.danmaku.domain.BilibiliLiveDanmakuSession;
import com.socialmonitor.bilibili.live.danmaku.domain.BilibiliLiveDanmakuStats;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveDanmakuRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BilibiliLiveDanmakuRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BilibiliLiveDanmakuSession createSession(Long monitorId, Long roomId, String connectHost) {
        String sql = """
                INSERT INTO bilibili_live_danmaku_session (
                    live_room_monitor_id, room_id, status, connect_host
                )
                VALUES (:monitorId, :roomId, 'CONNECTING', :connectHost)
                RETURNING *
                """;
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("roomId", roomId)
                .addValue("connectHost", connectHost), this::mapSession);
    }

    public void markSessionStatus(Long sessionId, String status) {
        String sql = """
                UPDATE bilibili_live_danmaku_session SET
                    status = :status,
                    ended_at = CASE WHEN :status IN ('CLOSED', 'STOPPED', 'ERROR') THEN now() ELSE ended_at END
                WHERE id = :sessionId
                """;
        jdbcTemplate.update(sql, Map.of("sessionId", sessionId, "status", status));
    }

    public void markSessionHeartbeat(Long sessionId) {
        jdbcTemplate.update("""
                UPDATE bilibili_live_danmaku_session SET
                    last_heartbeat_at = now()
                WHERE id = :sessionId
                """, Map.of("sessionId", sessionId));
    }

    public void markSessionError(Long sessionId, String errorType, String errorMessage) {
        String sql = """
                UPDATE bilibili_live_danmaku_session SET
                    status = 'ERROR',
                    ended_at = COALESCE(ended_at, now()),
                    last_error_at = now(),
                    last_error_type = :errorType,
                    last_error_message = :errorMessage
                WHERE id = :sessionId
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("errorType", truncate(errorType, 80))
                .addValue("errorMessage", truncate(errorMessage, 900)));
    }

    public Optional<BilibiliLiveDanmakuSession> findLatestSession(Long monitorId) {
        String sql = """
                SELECT *
                FROM bilibili_live_danmaku_session
                WHERE live_room_monitor_id = :monitorId
                ORDER BY started_at DESC, id DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, Map.of("monitorId", monitorId), this::mapSession).stream().findFirst();
    }

    public List<Long> findAutoStartRoomMonitorIds() {
        String sql = """
                SELECT DISTINCT binding.bilibili_live_room_monitor_id
                FROM subject_bilibili_binding binding
                JOIN bilibili_live_room_monitor room
                  ON room.id = binding.bilibili_live_room_monitor_id
                WHERE binding.danmu_enabled = true
                  AND binding.bilibili_live_room_monitor_id IS NOT NULL
                  AND room.monitor_status = 'ACTIVE'
                ORDER BY binding.bilibili_live_room_monitor_id ASC
                """;
        return jdbcTemplate.queryForList(sql, Map.of(), Long.class);
    }

    public void recordMetricEvent(
            Long monitorId,
            Long sessionId,
            Long roomId,
            OffsetDateTime occurredAt,
            int bucketSeconds,
            int danmuDelta,
            Long likeCount,
            Long likeIncrement,
            Long watchedCount,
            Long heartbeatPopularity,
            int giftDelta,
            int superChatDelta,
            int rawEventDelta
    ) {
        OffsetDateTime bucketStart = bucketAt(occurredAt == null ? OffsetDateTime.now() : occurredAt, bucketSeconds);
        String sql = """
                INSERT INTO bilibili_live_danmaku_metric_bucket (
                    live_room_monitor_id, session_id, room_id, bucket_start, bucket_seconds,
                    danmu_count, like_count, like_increment, watched_count, heartbeat_popularity,
                    gift_count, super_chat_count, raw_event_count, updated_at
                )
                VALUES (
                    :monitorId, :sessionId, :roomId, :bucketStart, :bucketSeconds,
                    :danmuDelta, :likeCount, :likeIncrement, :watchedCount, :heartbeatPopularity,
                    :giftDelta, :superChatDelta, :rawEventDelta, now()
                )
                ON CONFLICT (live_room_monitor_id, bucket_start, bucket_seconds) DO UPDATE SET
                    session_id = COALESCE(EXCLUDED.session_id, bilibili_live_danmaku_metric_bucket.session_id),
                    room_id = EXCLUDED.room_id,
                    danmu_count = bilibili_live_danmaku_metric_bucket.danmu_count + EXCLUDED.danmu_count,
                    like_count = COALESCE(EXCLUDED.like_count, bilibili_live_danmaku_metric_bucket.like_count),
                    like_increment = bilibili_live_danmaku_metric_bucket.like_increment + EXCLUDED.like_increment,
                    watched_count = COALESCE(EXCLUDED.watched_count, bilibili_live_danmaku_metric_bucket.watched_count),
                    heartbeat_popularity = COALESCE(EXCLUDED.heartbeat_popularity, bilibili_live_danmaku_metric_bucket.heartbeat_popularity),
                    gift_count = bilibili_live_danmaku_metric_bucket.gift_count + EXCLUDED.gift_count,
                    super_chat_count = bilibili_live_danmaku_metric_bucket.super_chat_count + EXCLUDED.super_chat_count,
                    raw_event_count = bilibili_live_danmaku_metric_bucket.raw_event_count + EXCLUDED.raw_event_count,
                    updated_at = now()
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("sessionId", sessionId)
                .addValue("roomId", roomId)
                .addValue("bucketStart", bucketStart)
                .addValue("bucketSeconds", bucketSeconds)
                .addValue("danmuDelta", danmuDelta)
                .addValue("likeCount", likeCount)
                .addValue("likeIncrement", likeIncrement == null ? 0 : likeIncrement)
                .addValue("watchedCount", watchedCount)
                .addValue("heartbeatPopularity", heartbeatPopularity)
                .addValue("giftDelta", giftDelta)
                .addValue("superChatDelta", superChatDelta)
                .addValue("rawEventDelta", rawEventDelta));
    }

    public void insertRecent(Long monitorId, Long roomId, String messageText, String displayName, String medalName, OffsetDateTime sentAt) {
        String sql = """
                INSERT INTO bilibili_live_danmaku_recent (
                    live_room_monitor_id, room_id, message_text, display_name, medal_name, sent_at
                )
                VALUES (:monitorId, :roomId, :messageText, :displayName, :medalName, :sentAt)
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("roomId", roomId)
                .addValue("messageText", truncate(messageText, 500))
                .addValue("displayName", truncate(displayName, 160))
                .addValue("medalName", truncate(medalName, 80))
                .addValue("sentAt", sentAt == null ? OffsetDateTime.now() : sentAt));
    }

    public void trimRecent(Long monitorId, int limit) {
        String sql = """
                DELETE FROM bilibili_live_danmaku_recent
                WHERE live_room_monitor_id = :monitorId
                  AND id NOT IN (
                      SELECT id
                      FROM bilibili_live_danmaku_recent
                      WHERE live_room_monitor_id = :monitorId
                      ORDER BY sent_at DESC, id DESC
                      LIMIT :limit
                  )
                """;
        jdbcTemplate.update(sql, Map.of("monitorId", monitorId, "limit", Math.max(1, limit)));
    }

    public List<BilibiliLiveDanmakuRecent> findRecent(Long monitorId, int limit) {
        String sql = """
                SELECT *
                FROM (
                    SELECT *
                    FROM bilibili_live_danmaku_recent
                    WHERE live_room_monitor_id = :monitorId
                    ORDER BY sent_at DESC, id DESC
                    LIMIT :limit
                ) recent
                ORDER BY sent_at ASC, id ASC
                """;
        return jdbcTemplate.query(sql, Map.of("monitorId", monitorId, "limit", Math.min(Math.max(limit, 1), 500)),
                this::mapRecent);
    }

    public List<BilibiliLiveDanmakuMetricBucket> findBuckets(Long monitorId, OffsetDateTime from, OffsetDateTime to, int limit) {
        String sql = """
                SELECT *
                FROM (
                    SELECT *
                    FROM bilibili_live_danmaku_metric_bucket
                    WHERE live_room_monitor_id = :monitorId
                      AND bucket_start >= :fromTime
                      AND bucket_start <= :toTime
                    ORDER BY bucket_start DESC
                    LIMIT :limit
                ) buckets
                ORDER BY bucket_start ASC
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("fromTime", from)
                .addValue("toTime", to)
                .addValue("limit", Math.min(Math.max(limit, 1), 5000)), this::mapBucket);
    }

    public BilibiliLiveDanmakuStats stats(Long monitorId, OffsetDateTime now) {
        String sql = """
                SELECT
                    COALESCE((SELECT SUM(danmu_count)
                              FROM bilibili_live_danmaku_metric_bucket
                              WHERE live_room_monitor_id = :monitorId AND bucket_start >= :lastMinute), 0) AS rate_per_minute,
                    COALESCE((SELECT SUM(danmu_count)
                              FROM bilibili_live_danmaku_metric_bucket
                              WHERE live_room_monitor_id = :monitorId AND bucket_start >= :lastFiveMinutes), 0) AS last5_minutes_count,
                    (SELECT like_count
                     FROM bilibili_live_danmaku_metric_bucket
                     WHERE live_room_monitor_id = :monitorId AND like_count IS NOT NULL
                     ORDER BY bucket_start DESC
                     LIMIT 1) AS like_count,
                    COALESCE((SELECT SUM(like_increment)
                              FROM bilibili_live_danmaku_metric_bucket
                              WHERE live_room_monitor_id = :monitorId AND bucket_start >= :lastFiveMinutes), 0) AS like_increment,
                    (SELECT watched_count
                     FROM bilibili_live_danmaku_metric_bucket
                     WHERE live_room_monitor_id = :monitorId AND watched_count IS NOT NULL
                     ORDER BY bucket_start DESC
                     LIMIT 1) AS watched_count,
                    (SELECT heartbeat_popularity
                     FROM bilibili_live_danmaku_metric_bucket
                     WHERE live_room_monitor_id = :monitorId AND heartbeat_popularity IS NOT NULL
                     ORDER BY bucket_start DESC
                     LIMIT 1) AS heartbeat_popularity,
                    (SELECT MAX(bucket_start)
                     FROM bilibili_live_danmaku_metric_bucket
                     WHERE live_room_monitor_id = :monitorId) AS latest_bucket_at,
                    (SELECT MAX(sent_at)
                     FROM bilibili_live_danmaku_recent
                     WHERE live_room_monitor_id = :monitorId) AS latest_message_at
                """;
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("lastMinute", now.minusMinutes(1))
                .addValue("lastFiveMinutes", now.minusMinutes(5)), (rs, rowNum) -> new BilibiliLiveDanmakuStats(
                rs.getInt("rate_per_minute"),
                rs.getInt("last5_minutes_count"),
                nullableLong(rs, "like_count"),
                nullableLong(rs, "like_increment"),
                nullableLong(rs, "watched_count"),
                nullableLong(rs, "heartbeat_popularity"),
                offsetDateTime(rs, "latest_bucket_at"),
                offsetDateTime(rs, "latest_message_at")
        ));
    }

    private BilibiliLiveDanmakuSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        return new BilibiliLiveDanmakuSession(
                rs.getLong("id"),
                rs.getLong("live_room_monitor_id"),
                rs.getLong("room_id"),
                offsetDateTime(rs, "started_at"),
                offsetDateTime(rs, "ended_at"),
                rs.getString("status"),
                rs.getString("connect_host"),
                rs.getInt("reconnect_count"),
                offsetDateTime(rs, "last_heartbeat_at"),
                offsetDateTime(rs, "last_error_at"),
                rs.getString("last_error_type"),
                rs.getString("last_error_message"),
                offsetDateTime(rs, "created_at")
        );
    }

    private BilibiliLiveDanmakuMetricBucket mapBucket(ResultSet rs, int rowNum) throws SQLException {
        return new BilibiliLiveDanmakuMetricBucket(
                rs.getLong("id"),
                rs.getLong("live_room_monitor_id"),
                nullableLong(rs, "session_id"),
                rs.getLong("room_id"),
                offsetDateTime(rs, "bucket_start"),
                rs.getInt("bucket_seconds"),
                rs.getInt("danmu_count"),
                nullableLong(rs, "like_count"),
                nullableLong(rs, "like_increment"),
                nullableLong(rs, "watched_count"),
                nullableLong(rs, "heartbeat_popularity"),
                rs.getInt("gift_count"),
                rs.getInt("super_chat_count"),
                rs.getInt("raw_event_count"),
                offsetDateTime(rs, "updated_at"),
                offsetDateTime(rs, "created_at")
        );
    }

    private BilibiliLiveDanmakuRecent mapRecent(ResultSet rs, int rowNum) throws SQLException {
        return new BilibiliLiveDanmakuRecent(
                rs.getLong("id"),
                rs.getLong("live_room_monitor_id"),
                rs.getLong("room_id"),
                rs.getString("message_text"),
                rs.getString("display_name"),
                rs.getString("medal_name"),
                offsetDateTime(rs, "sent_at"),
                offsetDateTime(rs, "created_at")
        );
    }

    private OffsetDateTime bucketAt(OffsetDateTime occurredAt, int bucketSeconds) {
        int safeBucketSeconds = Math.max(10, bucketSeconds);
        long epoch = occurredAt.toEpochSecond();
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond((epoch / safeBucketSeconds) * safeBucketSeconds), ZoneOffset.ofHours(8));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
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
}
