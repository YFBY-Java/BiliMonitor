package com.socialmonitor.bilibili.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.domain.BilibiliFetchedUserSnapshot;
import com.socialmonitor.bilibili.domain.BilibiliFollowerSnapshot;
import com.socialmonitor.bilibili.domain.BilibiliMonitoredUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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
@ConditionalOnProperty(prefix = "app.bilibili.follower-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliFollowerMonitorRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BilibiliFollowerMonitorRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<BilibiliMonitoredUser> findAll() {
        String sql = """
                SELECT * FROM bilibili_monitored_user
                ORDER BY monitor_status ASC, current_follower_count DESC NULLS LAST, id ASC
                """;
        return jdbcTemplate.query(sql, Map.of(), this::mapUser);
    }

    public Optional<BilibiliMonitoredUser> findById(Long id) {
        String sql = "SELECT * FROM bilibili_monitored_user WHERE id = :id";
        List<BilibiliMonitoredUser> users = jdbcTemplate.query(sql, Map.of("id", id), this::mapUser);
        return users.stream().findFirst();
    }

    public Optional<BilibiliMonitoredUser> findByMid(Long mid) {
        String sql = "SELECT * FROM bilibili_monitored_user WHERE mid = :mid";
        List<BilibiliMonitoredUser> users = jdbcTemplate.query(sql, Map.of("mid", mid), this::mapUser);
        return users.stream().findFirst();
    }

    public List<BilibiliMonitoredUser> findDueUsers(OffsetDateTime now, int limit) {
        String sql = """
                SELECT * FROM bilibili_monitored_user
                WHERE monitor_status = 'ACTIVE'
                  AND (next_collect_at IS NULL OR next_collect_at <= :now)
                ORDER BY COALESCE(next_collect_at, created_at) ASC
                LIMIT :limit
                """;
        return jdbcTemplate.query(sql, Map.of("now", now, "limit", limit), this::mapUser);
    }

    public BilibiliMonitoredUser upsertUserFromSnapshot(
            BilibiliFetchedUserSnapshot snapshot,
            int intervalSeconds,
            OffsetDateTime nextCollectAt
    ) {
        String sql = """
                INSERT INTO bilibili_monitored_user (
                    mid, nickname, avatar_url, profile_url, current_follower_count, following_count,
                    monitor_status, interval_seconds, next_collect_at, last_snapshot_at, last_success_at,
                    source_endpoint, updated_at
                )
                VALUES (
                    :mid, :nickname, :avatarUrl, :profileUrl, :followerCount, :followingCount,
                    'ACTIVE', :intervalSeconds, :nextCollectAt, :capturedAt, :capturedAt,
                    :sourceEndpoint, now()
                )
                ON CONFLICT (mid) DO UPDATE SET
                    nickname = EXCLUDED.nickname,
                    avatar_url = EXCLUDED.avatar_url,
                    profile_url = EXCLUDED.profile_url,
                    current_follower_count = EXCLUDED.current_follower_count,
                    following_count = EXCLUDED.following_count,
                    monitor_status = 'ACTIVE',
                    interval_seconds = EXCLUDED.interval_seconds,
                    next_collect_at = EXCLUDED.next_collect_at,
                    last_snapshot_at = EXCLUDED.last_snapshot_at,
                    last_success_at = EXCLUDED.last_success_at,
                    last_error_at = NULL,
                    last_error_type = NULL,
                    last_error_message = NULL,
                    source_endpoint = EXCLUDED.source_endpoint,
                    updated_at = now()
                RETURNING *
                """;
        return jdbcTemplate.queryForObject(sql, snapshotParams(snapshot)
                .addValue("intervalSeconds", intervalSeconds)
                .addValue("nextCollectAt", nextCollectAt), this::mapUser);
    }

    public void updateSuccessfulSnapshot(Long userId, BilibiliFetchedUserSnapshot snapshot, OffsetDateTime nextCollectAt) {
        String sql = """
                UPDATE bilibili_monitored_user SET
                    nickname = :nickname,
                    avatar_url = :avatarUrl,
                    profile_url = :profileUrl,
                    current_follower_count = :followerCount,
                    following_count = :followingCount,
                    next_collect_at = :nextCollectAt,
                    last_snapshot_at = :capturedAt,
                    last_success_at = :capturedAt,
                    last_error_at = NULL,
                    last_error_type = NULL,
                    last_error_message = NULL,
                    source_endpoint = :sourceEndpoint,
                    updated_at = now()
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, snapshotParams(snapshot)
                .addValue("id", userId)
                .addValue("nextCollectAt", nextCollectAt));
    }

    public void markFailure(Long userId, BilibiliFetchException exception, OffsetDateTime nextCollectAt) {
        String sql = """
                UPDATE bilibili_monitored_user SET
                    next_collect_at = :nextCollectAt,
                    last_error_at = :now,
                    last_error_type = :errorType,
                    last_error_message = :errorMessage,
                    updated_at = now()
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, Map.of(
                "id", userId,
                "nextCollectAt", nextCollectAt,
                "now", OffsetDateTime.now(),
                "errorType", exception.errorType().name(),
                "errorMessage", truncate(exception.getMessage(), 900)
        ));
    }

    public void updateStatus(Long userId, boolean enabled) {
        String sql = """
                UPDATE bilibili_monitored_user SET
                    monitor_status = :status,
                    next_collect_at = CASE WHEN :status = 'ACTIVE' THEN now() ELSE next_collect_at END,
                    updated_at = now()
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, Map.of(
                "id", userId,
                "status", enabled ? "ACTIVE" : "PAUSED"
        ));
    }

    public void updateInterval(Long userId, int intervalSeconds, OffsetDateTime nextCollectAt) {
        String sql = """
                UPDATE bilibili_monitored_user SET
                    interval_seconds = :intervalSeconds,
                    next_collect_at = CASE
                        WHEN monitor_status = 'ACTIVE' THEN :nextCollectAt
                        ELSE next_collect_at
                    END,
                    updated_at = now()
                WHERE id = :id
                """;
        jdbcTemplate.update(sql, Map.of(
                "id", userId,
                "intervalSeconds", intervalSeconds,
                "nextCollectAt", nextCollectAt
        ));
    }

    public void delete(Long userId) {
        jdbcTemplate.update("DELETE FROM bilibili_monitored_user WHERE id = :id", Map.of("id", userId));
    }

    public void upsertSnapshot(Long userId, BilibiliFetchedUserSnapshot snapshot) {
        String sql = """
                INSERT INTO bilibili_follower_snapshot (
                    monitored_user_id, mid, follower_count, following_count, captured_at,
                    captured_bucket, source_endpoint, raw_payload_json
                )
                VALUES (
                    :userId, :mid, :followerCount, :followingCount, :capturedAt,
                    :capturedBucket, :sourceEndpoint, CAST(:rawPayload AS jsonb)
                )
                ON CONFLICT (monitored_user_id, captured_bucket) DO UPDATE SET
                    follower_count = EXCLUDED.follower_count,
                    following_count = EXCLUDED.following_count,
                    captured_at = EXCLUDED.captured_at,
                    source_endpoint = EXCLUDED.source_endpoint,
                    raw_payload_json = EXCLUDED.raw_payload_json
                """;
        jdbcTemplate.update(sql, snapshotParams(snapshot)
                .addValue("userId", userId)
                .addValue("capturedBucket", snapshot.fetchedAt().truncatedTo(ChronoUnit.SECONDS)));
    }

    public List<BilibiliFollowerSnapshot> findSnapshots(Long userId, OffsetDateTime from, OffsetDateTime to, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", limit);
        StringBuilder whereClause = new StringBuilder("WHERE monitored_user_id = :userId\n");
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
                    SELECT * FROM bilibili_follower_snapshot
                    %s
                    ORDER BY captured_at DESC
                    LIMIT :limit
                ) limited
                ORDER BY captured_at ASC
                """.formatted(whereClause);
        return jdbcTemplate.query(sql, params, this::mapSnapshot);
    }

    public List<BilibiliFollowerSnapshot> findRecentSnapshots(Long userId, int limit) {
        String sql = """
                SELECT * FROM (
                    SELECT * FROM bilibili_follower_snapshot
                    WHERE monitored_user_id = :userId
                    ORDER BY captured_at DESC
                    LIMIT :limit
                ) limited
                ORDER BY captured_at ASC
                """;
        return jdbcTemplate.query(sql, Map.of("userId", userId, "limit", limit), this::mapSnapshot);
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

    private MapSqlParameterSource snapshotParams(BilibiliFetchedUserSnapshot snapshot) {
        return new MapSqlParameterSource()
                .addValue("mid", snapshot.mid())
                .addValue("nickname", snapshot.nickname())
                .addValue("avatarUrl", snapshot.avatarUrl())
                .addValue("profileUrl", snapshot.profileUrl())
                .addValue("followerCount", snapshot.followerCount())
                .addValue("followingCount", snapshot.followingCount())
                .addValue("capturedAt", snapshot.fetchedAt())
                .addValue("sourceEndpoint", snapshot.sourceEndpoint())
                .addValue("rawPayload", snapshot.rawPayload() == null || snapshot.rawPayload().isBlank() ? "{}" : snapshot.rawPayload());
    }

    private BilibiliMonitoredUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new BilibiliMonitoredUser(
                rs.getLong("id"),
                rs.getLong("mid"),
                rs.getString("nickname"),
                rs.getString("avatar_url"),
                rs.getString("profile_url"),
                nullableLong(rs, "current_follower_count"),
                nullableLong(rs, "following_count"),
                rs.getString("monitor_status"),
                rs.getInt("interval_seconds"),
                offsetDateTime(rs, "next_collect_at"),
                offsetDateTime(rs, "last_snapshot_at"),
                offsetDateTime(rs, "last_success_at"),
                offsetDateTime(rs, "last_error_at"),
                rs.getString("last_error_type"),
                rs.getString("last_error_message"),
                rs.getString("source_endpoint"),
                offsetDateTime(rs, "created_at"),
                offsetDateTime(rs, "updated_at")
        );
    }

    private BilibiliFollowerSnapshot mapSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new BilibiliFollowerSnapshot(
                rs.getLong("id"),
                rs.getLong("monitored_user_id"),
                rs.getLong("mid"),
                rs.getLong("follower_count"),
                nullableLong(rs, "following_count"),
                offsetDateTime(rs, "captured_at"),
                offsetDateTime(rs, "captured_bucket"),
                rs.getString("source_endpoint")
        );
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
