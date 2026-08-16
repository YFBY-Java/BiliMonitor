package com.socialmonitor.bilibili.live.session.repository;

import com.socialmonitor.bilibili.live.session.domain.BilibiliLiveSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class JdbcBilibiliLiveSessionRepository implements BilibiliLiveSessionRepository {

    private static final String ACTIVE_SESSION_SELECT = """
            SELECT *
            FROM bilibili_live_session
            WHERE monitor_id = :monitorId
              AND state IN ('OPEN', 'END_PENDING')
            ORDER BY id DESC
            LIMIT 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcBilibiliLiveSessionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void lockMonitor(Long monitorId) {
        jdbcTemplate.queryForObject(
                "SELECT id FROM bilibili_live_room_monitor WHERE id = :monitorId FOR UPDATE",
                Map.of("monitorId", monitorId),
                Long.class
        );
    }

    @Override
    public Optional<BilibiliLiveSession> findActive(Long monitorId) {
        return jdbcTemplate.query(
                ACTIVE_SESSION_SELECT,
                Map.of("monitorId", monitorId),
                this::mapSession
        ).stream().findFirst();
    }

    @Override
    public Optional<BilibiliLiveSession> findActiveForUpdate(Long monitorId) {
        return jdbcTemplate.query(
                ACTIVE_SESSION_SELECT + " FOR UPDATE",
                Map.of("monitorId", monitorId),
                this::mapSession
        ).stream().findFirst();
    }

    @Override
    public Optional<BilibiliLiveSession> findByPlatformLiveTimeForUpdate(
            Long monitorId,
            OffsetDateTime platformLiveTime
    ) {
        return jdbcTemplate.query("""
                SELECT *
                FROM bilibili_live_session
                WHERE monitor_id = :monitorId
                  AND platform_live_time = :platformLiveTime
                ORDER BY id DESC
                LIMIT 1
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("platformLiveTime", platformLiveTime), this::mapSession
        ).stream().findFirst();
    }

    @Override
    public Optional<BilibiliLiveSession> findByLiveKeyForUpdate(Long monitorId, String liveKey) {
        return jdbcTemplate.query("""
                SELECT *
                FROM bilibili_live_session
                WHERE monitor_id = :monitorId
                  AND live_key = :liveKey
                ORDER BY id DESC
                LIMIT 1
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("liveKey", liveKey), this::mapSession
        ).stream().findFirst();
    }

    @Override
    public Optional<BilibiliLiveSession> findByEventTimeForUpdate(
            Long monitorId,
            OffsetDateTime occurredAt
    ) {
        return jdbcTemplate.query("""
                SELECT *
                FROM bilibili_live_session
                WHERE monitor_id = :monitorId
                  AND state = 'CLOSED'
                  AND ended_at IS NOT NULL
                  AND started_at <= :occurredAt
                  AND ended_at >= :occurredAt
                ORDER BY started_at DESC, id DESC
                LIMIT 1
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("occurredAt", occurredAt), this::mapSession
        ).stream().findFirst();
    }

    @Override
    public BilibiliLiveSession insertOpen(BilibiliLiveSession session) {
        String sql = """
                INSERT INTO bilibili_live_session (
                    monitor_id, uid, room_id, state, platform_live_time, live_key,
                    started_at, start_detected_at, start_source,
                    end_signal_at, ended_at, end_detected_at, end_source,
                    last_live_observed_at, last_observed_at,
                    title_at_start, title_at_end, created_at, updated_at
                ) VALUES (
                    :monitorId, :uid, :roomId, :state, :platformLiveTime, :liveKey,
                    :startedAt, :startDetectedAt, :startSource,
                    :endSignalAt, :endedAt, :endDetectedAt, :endSource,
                    :lastLiveObservedAt, :lastObservedAt,
                    :titleAtStart, :titleAtEnd, :createdAt, :updatedAt
                )
                RETURNING *
                """;
        return jdbcTemplate.queryForObject(sql, params(session), this::mapSession);
    }

    @Override
    public void update(BilibiliLiveSession session) {
        String sql = """
                UPDATE bilibili_live_session SET
                    state = :state,
                    platform_live_time = :platformLiveTime,
                    live_key = :liveKey,
                    started_at = :startedAt,
                    start_detected_at = :startDetectedAt,
                    start_source = :startSource,
                    end_signal_at = :endSignalAt,
                    ended_at = :endedAt,
                    end_detected_at = :endDetectedAt,
                    end_source = :endSource,
                    last_live_observed_at = :lastLiveObservedAt,
                    last_observed_at = :lastObservedAt,
                    title_at_start = :titleAtStart,
                    title_at_end = :titleAtEnd,
                    updated_at = :updatedAt
                WHERE id = :id AND monitor_id = :monitorId
                """;
        jdbcTemplate.update(sql, params(session));
    }

    @Override
    public void scheduleImmediateCollection(Long monitorId) {
        jdbcTemplate.update("""
                UPDATE bilibili_live_room_monitor SET
                    next_collect_at = now(),
                    backoff_until = NULL,
                    updated_at = now()
                WHERE id = :monitorId
                """, Map.of("monitorId", monitorId));
    }

    private MapSqlParameterSource params(BilibiliLiveSession session) {
        return new MapSqlParameterSource()
                .addValue("id", session.id())
                .addValue("monitorId", session.monitorId())
                .addValue("uid", session.uid())
                .addValue("roomId", session.roomId())
                .addValue("state", session.state())
                .addValue("platformLiveTime", session.platformLiveTime())
                .addValue("liveKey", session.liveKey())
                .addValue("startedAt", session.startedAt())
                .addValue("startDetectedAt", session.startDetectedAt())
                .addValue("startSource", session.startSource())
                .addValue("endSignalAt", session.endSignalAt())
                .addValue("endedAt", session.endedAt())
                .addValue("endDetectedAt", session.endDetectedAt())
                .addValue("endSource", session.endSource())
                .addValue("lastLiveObservedAt", session.lastLiveObservedAt())
                .addValue("lastObservedAt", session.lastObservedAt())
                .addValue("titleAtStart", session.titleAtStart())
                .addValue("titleAtEnd", session.titleAtEnd())
                .addValue("createdAt", session.createdAt())
                .addValue("updatedAt", session.updatedAt());
    }

    private BilibiliLiveSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        return new BilibiliLiveSession(
                rs.getLong("id"),
                rs.getLong("monitor_id"),
                rs.getLong("uid"),
                rs.getLong("room_id"),
                rs.getString("state"),
                offsetDateTime(rs, "platform_live_time"),
                rs.getString("live_key"),
                offsetDateTime(rs, "started_at"),
                offsetDateTime(rs, "start_detected_at"),
                rs.getString("start_source"),
                offsetDateTime(rs, "end_signal_at"),
                offsetDateTime(rs, "ended_at"),
                offsetDateTime(rs, "end_detected_at"),
                rs.getString("end_source"),
                offsetDateTime(rs, "last_live_observed_at"),
                offsetDateTime(rs, "last_observed_at"),
                rs.getString("title_at_start"),
                rs.getString("title_at_end"),
                offsetDateTime(rs, "created_at"),
                offsetDateTime(rs, "updated_at")
        );
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }
}
