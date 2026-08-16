package com.socialmonitor.bilibili.live.session.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSessionExportRepository {

    static final String DANMAKU_SQL = """
            SELECT occurred_at, received_at, sender_uid, sender_name, medal_name, message_text,
                   command, protocol_version, source_event_id
            FROM bilibili_live_session_event
            WHERE live_session_id = ?
              AND event_kind = 'DANMAKU'
            ORDER BY occurred_at ASC, id ASC
            """;

    static final String GIFTS_SQL = """
            SELECT occurred_at, received_at, event_kind, sender_uid, sender_name,
                   medal_name, message_text, gift_id, gift_name, gift_count, coin_type,
                   CASE
                       WHEN COALESCE(paid, false) = false
                         OR LOWER(COALESCE(coin_type, '')) = 'silver' THEN NULL
                       ELSE unit_price_milli_yuan
                   END AS unit_price_milli_yuan,
                   paid_amount_milli_yuan, paid, guard_level, amount_source,
                   command, protocol_version, source_event_id, event_key, transport_session_id
            FROM bilibili_live_session_event
            WHERE live_session_id = ?
              AND event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
            ORDER BY occurred_at ASC, id ASC
            """;

    static final String USERS_SQL = """
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
                WHERE live_session_id = ?
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
                    AND (COALESCE(paid, false) = true OR COALESCE(paid_amount_milli_yuan, 0) > 0))
                    AS paid_event_count,
                COALESCE(SUM(COALESCE(paid_amount_milli_yuan, 0))
                    FILTER (WHERE event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')), 0)
                    AS paid_amount_milli_yuan,
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
            """;

    private static final int FETCH_SIZE = 1_000;

    private final JdbcTemplate jdbcTemplate;

    public BilibiliLiveSessionExportRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void streamDanmaku(Long sessionId, ExportRowConsumer<BilibiliLiveSessionDanmakuExportRow> consumer)
            throws IOException {
        stream(DANMAKU_SQL, sessionId, this::mapDanmaku, consumer);
    }

    public void streamGifts(Long sessionId, ExportRowConsumer<BilibiliLiveSessionGiftExportRow> consumer)
            throws IOException {
        stream(GIFTS_SQL, sessionId, this::mapGift, consumer);
    }

    public void streamUsers(Long sessionId, ExportRowConsumer<BilibiliLiveSessionUserExportRow> consumer)
            throws IOException {
        stream(USERS_SQL, sessionId, this::mapUser, consumer);
    }

    private <T> void stream(
            String sql,
            Long sessionId,
            RowMapper<T> mapper,
            ExportRowConsumer<T> consumer
    ) throws IOException {
        try {
            jdbcTemplate.query(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        sql,
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY
                );
                statement.setLong(1, sessionId);
                statement.setFetchSize(FETCH_SIZE);
                return statement;
            }, resultSet -> {
                try {
                    consumer.accept(mapper.mapRow(resultSet, resultSet.getRow()));
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private BilibiliLiveSessionDanmakuExportRow mapDanmaku(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new BilibiliLiveSessionDanmakuExportRow(
                offsetDateTime(resultSet, "occurred_at"),
                offsetDateTime(resultSet, "received_at"),
                nullableLong(resultSet, "sender_uid"),
                resultSet.getString("sender_name"),
                resultSet.getString("medal_name"),
                resultSet.getString("message_text"),
                resultSet.getString("command"),
                resultSet.getString("protocol_version"),
                resultSet.getString("source_event_id")
        );
    }

    private BilibiliLiveSessionGiftExportRow mapGift(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BilibiliLiveSessionGiftExportRow(
                offsetDateTime(resultSet, "occurred_at"),
                offsetDateTime(resultSet, "received_at"),
                resultSet.getString("event_kind"),
                nullableLong(resultSet, "sender_uid"),
                resultSet.getString("sender_name"),
                resultSet.getString("medal_name"),
                resultSet.getString("message_text"),
                nullableLong(resultSet, "gift_id"),
                resultSet.getString("gift_name"),
                nullableLong(resultSet, "gift_count"),
                resultSet.getString("coin_type"),
                nullableLong(resultSet, "unit_price_milli_yuan"),
                nullableLong(resultSet, "paid_amount_milli_yuan"),
                resultSet.getObject("paid", Boolean.class),
                resultSet.getObject("guard_level", Integer.class),
                resultSet.getString("amount_source"),
                resultSet.getString("command"),
                resultSet.getString("protocol_version"),
                resultSet.getString("source_event_id"),
                resultSet.getString("event_key"),
                nullableLong(resultSet, "transport_session_id")
        );
    }

    private BilibiliLiveSessionUserExportRow mapUser(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BilibiliLiveSessionUserExportRow(
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

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private OffsetDateTime offsetDateTime(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class);
    }

    @FunctionalInterface
    public interface ExportRowConsumer<T> {
        void accept(T row) throws IOException;
    }
}
