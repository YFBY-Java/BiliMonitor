package com.socialmonitor.bilibili.live.session.query;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionUserView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionEventView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSessionQueryRepository {

    static final String SESSION_SUMMARY_SELECT = """
            , normalized_events AS (
                SELECT event.*,
                       CASE
                           WHEN sender_uid IS NOT NULL AND sender_uid > 0
                               THEN 'uid:' || sender_uid::text
                           ELSE 'event:' || event.id::text
                       END AS actor_key
                FROM bilibili_live_session_event event
                JOIN candidate_sessions candidate ON candidate.id = event.live_session_id
            ), event_aggregates AS (
                SELECT
                    live_session_id,
                    COUNT(*) FILTER (WHERE event_kind = 'DANMAKU') AS danmaku_count,
                    COUNT(*) FILTER (WHERE event_kind = 'GIFT') AS gift_event_count,
                    COALESCE(SUM(COALESCE(gift_count, 1))
                        FILTER (WHERE event_kind = 'GIFT'), 0) AS gift_count,
                    COALESCE(SUM(COALESCE(gift_count, 1))
                        FILTER (WHERE event_kind = 'GIFT' AND COALESCE(paid, false) = false), 0) AS free_gift_count,
                    COUNT(DISTINCT sender_uid)
                        FILTER (WHERE sender_uid IS NOT NULL AND sender_uid > 0
                            AND event_kind = 'GIFT') AS gift_sender_count,
                    COUNT(DISTINCT sender_uid)
                        FILTER (WHERE sender_uid IS NOT NULL AND sender_uid > 0
                            AND event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                            AND (COALESCE(paid, false) = true OR COALESCE(paid_amount_milli_yuan, 0) > 0))
                        AS paid_user_count,
                    COUNT(DISTINCT sender_uid)
                        FILTER (WHERE sender_uid IS NOT NULL AND sender_uid > 0
                            AND event_kind IN ('DANMAKU', 'GIFT', 'SUPER_CHAT', 'GUARD_BUY'))
                        AS interacting_user_count,
                    COUNT(*) FILTER (WHERE (sender_uid IS NULL OR sender_uid <= 0)
                        AND event_kind IN ('DANMAKU', 'GIFT', 'SUPER_CHAT', 'GUARD_BUY'))
                        AS unresolved_interacting_event_count,
                    COUNT(*) FILTER (WHERE (sender_uid IS NULL OR sender_uid <= 0)
                        AND event_kind = 'GIFT') AS unresolved_gift_event_count,
                    COUNT(*) FILTER (WHERE (sender_uid IS NULL OR sender_uid <= 0)
                        AND event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                        AND (COALESCE(paid, false) = true OR COALESCE(paid_amount_milli_yuan, 0) > 0))
                        AS unresolved_paid_event_count,
                    COUNT(*) FILTER (WHERE event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                        AND (COALESCE(paid, false) = true OR COALESCE(paid_amount_milli_yuan, 0) > 0))
                        AS paid_event_count,
                    COALESCE(SUM(COALESCE(paid_amount_milli_yuan, 0))
                        FILTER (WHERE event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')), 0)
                        AS paid_amount_milli_yuan,
                    MIN(occurred_at) AS first_event_at,
                    MAX(occurred_at) AS last_event_at
                FROM normalized_events
                GROUP BY live_session_id
            ), transport_coverage AS (
                SELECT
                    session.id AS live_session_id,
                    COUNT(transport.id) AS transport_session_count,
                    MIN(GREATEST(transport.connected_at, session.started_at))
                        FILTER (WHERE transport.id IS NOT NULL) AS capture_started_at,
                    CASE
                        WHEN COUNT(transport.id) = 0 THEN NULL
                        WHEN BOOL_OR(session.ended_at IS NULL AND transport.ended_at IS NULL) THEN NULL
                        ELSE MAX(LEAST(session.ended_at, transport.ended_at))
                    END AS capture_ended_at
                FROM candidate_sessions session
                LEFT JOIN bilibili_live_danmaku_session transport
                  ON session.start_source NOT LIKE 'STATUS_EVENT_BACKFILL%'
                 AND transport.live_room_monitor_id = session.monitor_id
                 AND transport.connected_at IS NOT NULL
                 AND (session.ended_at IS NULL OR transport.connected_at < session.ended_at)
                 AND (transport.ended_at IS NULL OR transport.ended_at > session.started_at)
                 AND GREATEST(transport.connected_at, session.started_at)
                     < LEAST(
                         COALESCE(transport.ended_at, 'infinity'::timestamptz),
                         COALESCE(session.ended_at, 'infinity'::timestamptz)
                     )
                GROUP BY session.id
            )
            SELECT
                session.id,
                session.monitor_id,
                session.uid,
                session.room_id,
                session.state,
                session.started_at,
                session.ended_at,
                session.start_source,
                session.end_source,
                CASE
                    WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' THEN 'BOUNDARY_ONLY'
                    WHEN coverage.transport_session_count = 0 THEN 'NO_ONLINE_COVERAGE'
                    ELSE 'RECEIVED_WHILE_ONLINE'
                END AS coverage_status,
                coverage.transport_session_count,
                coverage.capture_started_at,
                coverage.capture_ended_at,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.danmaku_count, 0) END AS danmaku_count,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.gift_event_count, 0) END AS gift_event_count,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.gift_count, 0) END AS gift_count,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.free_gift_count, 0) END AS free_gift_count,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.gift_sender_count, 0) END AS gift_sender_count,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.paid_user_count, 0) END AS paid_user_count,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.interacting_user_count, 0) END AS interacting_user_count,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.unresolved_interacting_event_count, 0)
                    END AS unresolved_interacting_event_count,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.unresolved_gift_event_count, 0)
                    END AS unresolved_gift_event_count,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.unresolved_paid_event_count, 0)
                    END AS unresolved_paid_event_count,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.paid_event_count, 0) END AS paid_event_count,
                CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%' AND aggregate.first_event_at IS NULL
                    THEN NULL ELSE COALESCE(aggregate.paid_amount_milli_yuan, 0) END AS paid_amount_milli_yuan,
                aggregate.first_event_at,
                aggregate.last_event_at
            FROM candidate_sessions session
            LEFT JOIN event_aggregates aggregate ON aggregate.live_session_id = session.id
            JOIN transport_coverage coverage ON coverage.live_session_id = session.id
            """;

    static final String RECENT_SESSIONS_SQL = """
            WITH candidate_sessions AS (
                SELECT *
                FROM bilibili_live_session
                WHERE monitor_id = :monitorId
                ORDER BY started_at DESC, id DESC
                LIMIT :limit
            )
            """ + SESSION_SUMMARY_SELECT + """
            ORDER BY session.started_at DESC, session.id DESC
            """;

    static final String SESSION_DETAIL_SQL = """
            WITH candidate_sessions AS (
                SELECT *
                FROM bilibili_live_session
                WHERE id = :sessionId
            )
            """ + SESSION_SUMMARY_SELECT;

    static final String SESSION_USERS_SQL = """
            WITH normalized_events AS (
                SELECT event.*,
                       CASE
                           WHEN sender_uid IS NOT NULL AND sender_uid > 0
                               THEN 'uid:' || sender_uid::text
                           ELSE 'event:' || event.id::text
                       END AS actor_key,
                       CASE
                           WHEN sender_uid IS NOT NULL AND sender_uid > 0 THEN 'VERIFIED_UID'
                           ELSE 'UNRESOLVED_EVENT'
                       END AS identity_quality
                FROM bilibili_live_session_event event
                WHERE live_session_id = :sessionId
                  AND event_kind IN ('DANMAKU', 'GIFT', 'SUPER_CHAT', 'GUARD_BUY')
            ), aggregated_users AS (
                SELECT
                actor_key,
                identity_quality,
                MAX(sender_uid) FILTER (WHERE sender_uid IS NOT NULL AND sender_uid > 0) AS user_uid,
                COUNT(*) FILTER (WHERE event_kind = 'DANMAKU') AS danmaku_count,
                COUNT(*) FILTER (WHERE event_kind = 'GIFT') AS gift_event_count,
                COALESCE(SUM(COALESCE(gift_count, 1))
                    FILTER (WHERE event_kind = 'GIFT'), 0) AS gift_count,
                COALESCE(SUM(COALESCE(gift_count, 1))
                    FILTER (WHERE event_kind = 'GIFT' AND COALESCE(paid, false) = false), 0) AS free_gift_count,
                COUNT(*) FILTER (WHERE event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                    AND (COALESCE(paid, false) = true OR COALESCE(paid_amount_milli_yuan, 0) > 0)) AS paid_event_count,
                COALESCE(SUM(COALESCE(paid_amount_milli_yuan, 0))
                    FILTER (WHERE event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')), 0) AS paid_amount_milli_yuan,
                MIN(occurred_at) AS first_seen_at,
                MAX(occurred_at) AS last_seen_at
                FROM normalized_events
                GROUP BY actor_key, identity_quality
            ), latest_names AS (
                SELECT DISTINCT ON (actor_key) actor_key, sender_name AS display_name
                FROM normalized_events
                WHERE NULLIF(BTRIM(sender_name), '') IS NOT NULL
                ORDER BY actor_key, occurred_at DESC, id DESC
            )
            SELECT aggregated.*, latest.display_name
            FROM aggregated_users aggregated
            LEFT JOIN latest_names latest ON latest.actor_key = aggregated.actor_key
            ORDER BY paid_amount_milli_yuan DESC, gift_count DESC, danmaku_count DESC, aggregated.actor_key ASC
            LIMIT :limit
            """;

    static final String SESSION_EVENTS_WHERE = """
            FROM bilibili_live_session_event event
            WHERE event.live_session_id = :sessionId
              AND event.event_kind IN ('DANMAKU', 'GIFT', 'SUPER_CHAT', 'GUARD_BUY')
              AND (CAST(:kind AS varchar) IS NULL OR event.event_kind = CAST(:kind AS varchar))
              AND (CAST(:userUid AS bigint) IS NULL OR event.sender_uid = CAST(:userUid AS bigint))
              AND (CAST(:paid AS boolean) IS NULL OR COALESCE(event.paid, false) = CAST(:paid AS boolean))
              AND (CAST(:keyword AS varchar) IS NULL
                   OR event.sender_name ILIKE '%' || CAST(:keyword AS varchar) || '%'
                   OR event.message_text ILIKE '%' || CAST(:keyword AS varchar) || '%'
                   OR event.gift_name ILIKE '%' || CAST(:keyword AS varchar) || '%')
            """;

    static final String SESSION_EVENTS_SQL = """
            SELECT event.id, event.live_session_id, event.event_kind, event.command,
                   event.sender_uid, event.sender_name, event.medal_name, event.message_text,
                   event.gift_id, event.gift_name, event.gift_count, event.paid,
                   event.paid_amount_milli_yuan, event.guard_level,
                   event.occurred_at, event.received_at
            """ + SESSION_EVENTS_WHERE + """
            ORDER BY event.occurred_at DESC, event.id DESC
            OFFSET :offset LIMIT :limit
            """;

    static final String SESSION_EVENTS_COUNT_SQL = "SELECT COUNT(*) " + SESSION_EVENTS_WHERE;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BilibiliLiveSessionQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BilibiliLiveSessionSummaryView> findRecentSessions(Long monitorId, int limit) {
        return jdbcTemplate.query(RECENT_SESSIONS_SQL, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("limit", limit), this::mapSummary);
    }

    public Optional<BilibiliLiveSessionSummaryView> findSession(Long sessionId) {
        return jdbcTemplate.query(SESSION_DETAIL_SQL, Map.of("sessionId", sessionId), this::mapSummary)
                .stream()
                .findFirst();
    }

    public List<BilibiliLiveSessionUserView> findUsers(Long sessionId, int limit) {
        return jdbcTemplate.query(SESSION_USERS_SQL, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("limit", limit), this::mapUser);
    }

    public List<BilibiliLiveSessionEventView> findEvents(
            Long sessionId,
            String kind,
            String keyword,
            Long userUid,
            Boolean paid,
            int offset,
            int limit
    ) {
        MapSqlParameterSource parameters = eventParameters(sessionId, kind, keyword, userUid, paid)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return jdbcTemplate.query(SESSION_EVENTS_SQL, parameters, this::mapEvent);
    }

    public long countEvents(Long sessionId, String kind, String keyword, Long userUid, Boolean paid) {
        Long count = jdbcTemplate.queryForObject(
                SESSION_EVENTS_COUNT_SQL,
                eventParameters(sessionId, kind, keyword, userUid, paid),
                Long.class);
        return count == null ? 0L : count;
    }

    private MapSqlParameterSource eventParameters(
            Long sessionId,
            String kind,
            String keyword,
            Long userUid,
            Boolean paid
    ) {
        return new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("kind", kind)
                .addValue("keyword", keyword)
                .addValue("userUid", userUid)
                .addValue("paid", paid);
    }

    private BilibiliLiveSessionSummaryView mapSummary(ResultSet resultSet, int rowNum) throws SQLException {
        return new BilibiliLiveSessionSummaryView(
                resultSet.getLong("id"),
                resultSet.getLong("monitor_id"),
                resultSet.getLong("uid"),
                resultSet.getLong("room_id"),
                resultSet.getString("state"),
                offsetDateTime(resultSet, "started_at"),
                offsetDateTime(resultSet, "ended_at"),
                resultSet.getString("start_source"),
                resultSet.getString("end_source"),
                resultSet.getString("coverage_status"),
                resultSet.getLong("transport_session_count"),
                offsetDateTime(resultSet, "capture_started_at"),
                offsetDateTime(resultSet, "capture_ended_at"),
                nullableLong(resultSet, "danmaku_count"),
                nullableLong(resultSet, "gift_event_count"),
                nullableLong(resultSet, "gift_count"),
                nullableLong(resultSet, "free_gift_count"),
                nullableLong(resultSet, "gift_sender_count"),
                nullableLong(resultSet, "paid_user_count"),
                nullableLong(resultSet, "interacting_user_count"),
                nullableLong(resultSet, "unresolved_interacting_event_count"),
                nullableLong(resultSet, "unresolved_gift_event_count"),
                nullableLong(resultSet, "unresolved_paid_event_count"),
                nullableLong(resultSet, "paid_event_count"),
                nullableLong(resultSet, "paid_amount_milli_yuan"),
                offsetDateTime(resultSet, "first_event_at"),
                offsetDateTime(resultSet, "last_event_at")
        );
    }

    private BilibiliLiveSessionUserView mapUser(ResultSet resultSet, int rowNum) throws SQLException {
        return new BilibiliLiveSessionUserView(
                resultSet.getString("actor_key"),
                resultSet.getString("identity_quality"),
                nullableLong(resultSet, "user_uid"),
                resultSet.getString("display_name"),
                resultSet.getLong("danmaku_count"),
                resultSet.getLong("gift_event_count"),
                resultSet.getLong("gift_count"),
                resultSet.getLong("free_gift_count"),
                resultSet.getLong("paid_event_count"),
                resultSet.getLong("paid_amount_milli_yuan"),
                offsetDateTime(resultSet, "first_seen_at"),
                offsetDateTime(resultSet, "last_seen_at")
        );
    }

    private BilibiliLiveSessionEventView mapEvent(ResultSet resultSet, int rowNum) throws SQLException {
        return new BilibiliLiveSessionEventView(
                resultSet.getLong("id"),
                resultSet.getLong("live_session_id"),
                resultSet.getString("event_kind"),
                resultSet.getString("command"),
                nullableLong(resultSet, "sender_uid"),
                resultSet.getString("sender_name"),
                resultSet.getString("medal_name"),
                resultSet.getString("message_text"),
                nullableLong(resultSet, "gift_id"),
                resultSet.getString("gift_name"),
                nullableLong(resultSet, "gift_count"),
                nullableBoolean(resultSet, "paid"),
                nullableLong(resultSet, "paid_amount_milli_yuan"),
                nullableInteger(resultSet, "guard_level"),
                offsetDateTime(resultSet, "occurred_at"),
                offsetDateTime(resultSet, "received_at")
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Boolean nullableBoolean(ResultSet resultSet, String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private OffsetDateTime offsetDateTime(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class);
    }
}
