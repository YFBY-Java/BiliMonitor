package com.socialmonitor.bilibili.live.session.insight;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionInsightView;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSessionInsightRepository {

    static final String COVERAGE_SQL = """
            SELECT GREATEST(transport.connected_at, session.started_at) AS active_from,
                   LEAST(COALESCE(transport.ended_at, CURRENT_TIMESTAMP),
                         COALESCE(session.ended_at, CURRENT_TIMESTAMP)) AS active_to
            FROM bilibili_live_session session
            JOIN bilibili_live_danmaku_session transport
              ON transport.live_room_monitor_id = session.monitor_id
             AND transport.connected_at IS NOT NULL
             AND (session.ended_at IS NULL OR transport.connected_at < session.ended_at)
             AND (transport.ended_at IS NULL OR transport.ended_at > session.started_at)
            WHERE session.id = :sessionId
              AND session.start_source NOT LIKE 'STATUS_EVENT_BACKFILL%'
              AND GREATEST(transport.connected_at, session.started_at)
                  < LEAST(COALESCE(transport.ended_at, CURRENT_TIMESTAMP),
                          COALESCE(session.ended_at, CURRENT_TIMESTAMP))
            ORDER BY active_from
            """;

    static final String TIMELINE_SQL = """
            SELECT to_timestamp(
                       floor(EXTRACT(EPOCH FROM event.occurred_at) / :bucketSeconds) * :bucketSeconds
                   ) AS bucket_start,
                   COUNT(*) FILTER (WHERE event.event_kind = 'DANMAKU') AS danmaku_count,
                   COUNT(*) FILTER (WHERE event.event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                       AND (COALESCE(event.paid, false) = true
                            OR COALESCE(event.paid_amount_milli_yuan, 0) > 0)) AS paid_event_count,
                   COALESCE(SUM(COALESCE(event.paid_amount_milli_yuan, 0))
                       FILTER (WHERE event.event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')), 0)
                       AS paid_amount_milli_yuan,
                   COUNT(DISTINCT event.sender_uid)
                       FILTER (WHERE event.sender_uid IS NOT NULL AND event.sender_uid > 0)
                       AS active_user_count
            FROM bilibili_live_session_event event
            WHERE event.live_session_id = :sessionId
              AND event.event_kind IN ('DANMAKU', 'GIFT', 'SUPER_CHAT', 'GUARD_BUY')
            GROUP BY bucket_start
            ORDER BY bucket_start
            """;

    static final String GIFT_MIX_SQL = """
            SELECT COALESCE(NULLIF(BTRIM(event.gift_name), ''), event.command, event.event_kind) AS gift_name,
                   event.event_kind,
                   COALESCE(SUM(COALESCE(event.gift_count, 1)), 0) AS gift_count,
                   COALESCE(SUM(COALESCE(event.paid_amount_milli_yuan, 0)), 0) AS paid_amount_milli_yuan
            FROM bilibili_live_session_event event
            WHERE event.live_session_id = :sessionId
              AND event.event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
            GROUP BY 1, event.event_kind
            ORDER BY paid_amount_milli_yuan DESC, gift_count DESC, gift_name
            LIMIT 12
            """;

    static final String QUALITY_SQL = """
            SELECT COUNT(*) AS supported_event_count,
                   COUNT(*) FILTER (WHERE sender_uid IS NOT NULL AND sender_uid > 0)
                       AS resolved_event_count,
                   PERCENTILE_CONT(0.95) WITHIN GROUP (
                       ORDER BY GREATEST(EXTRACT(EPOCH FROM (received_at - occurred_at)) * 1000, 0)
                   ) FILTER (WHERE received_at IS NOT NULL AND occurred_at IS NOT NULL)
                       AS latency_p95_millis
            FROM bilibili_live_session_event
            WHERE live_session_id = :sessionId
              AND event_kind IN ('DANMAKU', 'GIFT', 'SUPER_CHAT', 'GUARD_BUY')
            """;

    static final String USER_SEGMENTS_SQL = """
            WITH verified_users AS (
                SELECT sender_uid,
                       COUNT(*) FILTER (WHERE event_kind = 'DANMAKU') AS danmaku_count,
                       COALESCE(SUM(COALESCE(paid_amount_milli_yuan, 0))
                           FILTER (WHERE event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')), 0)
                           AS paid_amount_milli_yuan
                FROM bilibili_live_session_event
                WHERE live_session_id = :sessionId
                  AND sender_uid IS NOT NULL AND sender_uid > 0
                  AND event_kind IN ('DANMAKU', 'GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                GROUP BY sender_uid
            )
            SELECT COUNT(*) FILTER (WHERE paid_amount_milli_yuan > 0 AND danmaku_count > 0) AS core_count,
                   COUNT(*) FILTER (WHERE paid_amount_milli_yuan > 0 AND danmaku_count = 0) AS silent_count,
                   COUNT(*) FILTER (WHERE paid_amount_milli_yuan = 0 AND danmaku_count >= 3) AS active_unpaid_count,
                   COUNT(*) FILTER (WHERE paid_amount_milli_yuan = 0 AND danmaku_count < 3) AS casual_count
            FROM verified_users
            """;

    static final String DANMAKU_DEPTH_SQL = """
            WITH danmaku_events AS (
                SELECT sender_uid,
                       message_text,
                       occurred_at,
                       MIN(occurred_at) OVER () AS first_at,
                       MAX(occurred_at) OVER () AS last_at
                FROM bilibili_live_session_event
                WHERE live_session_id = :sessionId
                  AND event_kind = 'DANMAKU'
            ), tagged AS (
                SELECT sender_uid,
                       message_text,
                       CASE
                           WHEN first_at IS NULL OR last_at IS NULL OR last_at <= first_at THEN 0
                           ELSE LEAST(2, FLOOR(
                               EXTRACT(EPOCH FROM (occurred_at - first_at)) * 3
                               / NULLIF(EXTRACT(EPOCH FROM (last_at - first_at)), 0)
                           )::integer)
                       END AS stage_no
                FROM danmaku_events
            ), verified_users AS (
                SELECT sender_uid,
                       COUNT(*) AS danmaku_count,
                       COUNT(DISTINCT stage_no) AS active_stage_count
                FROM tagged
                WHERE sender_uid IS NOT NULL AND sender_uid > 0
                GROUP BY sender_uid
            )
            SELECT (SELECT COUNT(*) FROM tagged
                        WHERE sender_uid IS NOT NULL AND sender_uid > 0) AS identified_danmaku_count,
                   (SELECT COUNT(*) FROM verified_users) AS identified_danmaku_user_count,
                   (SELECT COUNT(*) FROM verified_users
                        WHERE danmaku_count >= 3) AS repeat_user_count,
                   (SELECT COUNT(*) FROM verified_users
                        WHERE active_stage_count >= 2) AS sustained_user_count,
                   (SELECT COUNT(*) FROM tagged
                        WHERE message_text IS NOT NULL AND BTRIM(message_text) <> '') AS nonblank_message_count,
                   (SELECT COUNT(DISTINCT LOWER(BTRIM(message_text))) FROM tagged
                        WHERE message_text IS NOT NULL AND BTRIM(message_text) <> '') AS distinct_message_count
            """;

    static final String DANMAKU_STAGES_SQL = """
            WITH danmaku_events AS (
                SELECT sender_uid,
                       occurred_at,
                       MIN(occurred_at) OVER () AS first_at,
                       MAX(occurred_at) OVER () AS last_at
                FROM bilibili_live_session_event
                WHERE live_session_id = :sessionId
                  AND event_kind = 'DANMAKU'
            ), tagged AS (
                SELECT sender_uid,
                       CASE
                           WHEN first_at IS NULL OR last_at IS NULL OR last_at <= first_at THEN 0
                           ELSE LEAST(2, FLOOR(
                               EXTRACT(EPOCH FROM (occurred_at - first_at)) * 3
                               / NULLIF(EXTRACT(EPOCH FROM (last_at - first_at)), 0)
                           )::integer)
                       END AS stage_no
                FROM danmaku_events
            )
            SELECT stage.stage_no,
                   COUNT(tagged.stage_no) AS danmaku_count,
                   COUNT(DISTINCT tagged.sender_uid)
                       FILTER (WHERE tagged.sender_uid IS NOT NULL AND tagged.sender_uid > 0)
                       AS active_user_count
            FROM generate_series(0, 2) AS stage(stage_no)
            LEFT JOIN tagged ON tagged.stage_no = stage.stage_no
            GROUP BY stage.stage_no
            ORDER BY stage.stage_no
            """;

    static final String REPEATED_MESSAGES_SQL = """
            SELECT MIN(BTRIM(message_text)) AS message_text,
                   COUNT(*) AS message_count,
                   COUNT(DISTINCT sender_uid)
                       FILTER (WHERE sender_uid IS NOT NULL AND sender_uid > 0) AS user_count
            FROM bilibili_live_session_event
            WHERE live_session_id = :sessionId
              AND event_kind = 'DANMAKU'
              AND message_text IS NOT NULL
              AND BTRIM(message_text) <> ''
            GROUP BY LOWER(BTRIM(message_text))
            HAVING COUNT(*) >= 2
            ORDER BY message_count DESC, user_count DESC, message_text
            LIMIT 5
            """;

    static final String PAYMENT_DEPTH_SQL = """
            WITH current_session AS (
                SELECT id, monitor_id, started_at
                FROM bilibili_live_session
                WHERE id = :sessionId
            ), per_user AS (
                SELECT event.sender_uid,
                       COUNT(*) FILTER (
                           WHERE event.event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                             AND (COALESCE(event.paid, false) = true
                                  OR COALESCE(event.paid_amount_milli_yuan, 0) > 0)
                       ) AS paid_event_count,
                       COALESCE(SUM(COALESCE(event.paid_amount_milli_yuan, 0)) FILTER (
                           WHERE event.event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                       ), 0) AS paid_amount_milli_yuan,
                       COUNT(*) FILTER (WHERE event.event_kind = 'DANMAKU') AS danmaku_count,
                       MIN(event.occurred_at) FILTER (WHERE event.event_kind = 'DANMAKU') AS first_danmaku_at,
                       MIN(event.occurred_at) FILTER (
                           WHERE event.event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                             AND (COALESCE(event.paid, false) = true
                                  OR COALESCE(event.paid_amount_milli_yuan, 0) > 0)
                       ) AS first_paid_at
                FROM bilibili_live_session_event event
                WHERE event.live_session_id = :sessionId
                  AND event.sender_uid IS NOT NULL AND event.sender_uid > 0
                  AND event.event_kind IN ('DANMAKU', 'GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                GROUP BY event.sender_uid
            ), payers AS (
                SELECT * FROM per_user WHERE paid_event_count > 0
            ), payer_history AS (
                SELECT payer.*,
                       EXISTS (
                           SELECT 1
                           FROM current_session
                           JOIN bilibili_live_session prior_session
                             ON prior_session.monitor_id = current_session.monitor_id
                            AND prior_session.started_at < current_session.started_at
                           JOIN bilibili_live_session_event prior_event
                             ON prior_event.live_session_id = prior_session.id
                            AND prior_event.sender_uid = payer.sender_uid
                           WHERE prior_event.event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                             AND (COALESCE(prior_event.paid, false) = true
                                  OR COALESCE(prior_event.paid_amount_milli_yuan, 0) > 0)
                       ) AS returning_payer
                FROM payers payer
            )
            SELECT COUNT(*) AS payer_count,
                   COUNT(*) FILTER (WHERE paid_event_count >= 2) AS repeat_payer_count,
                   COUNT(*) FILTER (WHERE danmaku_count > 0) AS engaged_payer_count,
                   COUNT(*) FILTER (WHERE returning_payer) AS returning_payer_count,
                   PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY paid_amount_milli_yuan)
                       AS median_payer_amount_milli_yuan,
                   PERCENTILE_CONT(0.5) WITHIN GROUP (
                       ORDER BY EXTRACT(EPOCH FROM (first_paid_at - first_danmaku_at))
                   ) FILTER (WHERE first_danmaku_at <= first_paid_at)
                       AS median_conversion_lag_seconds,
                   COALESCE(MAX(paid_amount_milli_yuan), 0) AS top_one_paid_amount_milli_yuan
            FROM payer_history
            """;

    static final String SPEND_TIERS_SQL = """
            WITH per_user AS (
                SELECT sender_uid,
                       COUNT(*) FILTER (
                           WHERE event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                             AND (COALESCE(paid, false) = true
                                  OR COALESCE(paid_amount_milli_yuan, 0) > 0)
                       ) AS paid_event_count,
                       COALESCE(SUM(COALESCE(paid_amount_milli_yuan, 0)) FILTER (
                           WHERE event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                       ), 0) AS paid_amount_milli_yuan
                FROM bilibili_live_session_event
                WHERE live_session_id = :sessionId
                  AND sender_uid IS NOT NULL AND sender_uid > 0
                  AND event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')
                GROUP BY sender_uid
            ), tiered AS (
                SELECT CASE
                           WHEN paid_amount_milli_yuan < 1000 THEN 'LIGHT'
                           WHEN paid_amount_milli_yuan < 10000 THEN 'STANDARD'
                           ELSE 'CORE'
                       END AS tier_code,
                       paid_amount_milli_yuan
                FROM per_user
                WHERE paid_event_count > 0
            )
            SELECT tier_code,
                   COUNT(*) AS user_count,
                   COALESCE(SUM(paid_amount_milli_yuan), 0) AS paid_amount_milli_yuan
            FROM tiered
            GROUP BY tier_code
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BilibiliLiveSessionInsightRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CoverageInterval> findCoverageIntervals(Long sessionId) {
        return jdbcTemplate.query(COVERAGE_SQL, new MapSqlParameterSource("sessionId", sessionId),
                (resultSet, rowNum) -> new CoverageInterval(
                        resultSet.getObject("active_from", OffsetDateTime.class),
                        resultSet.getObject("active_to", OffsetDateTime.class)));
    }

    public List<BilibiliLiveSessionInsightView.TimeBucket> findTimeline(Long sessionId, int bucketSeconds) {
        return jdbcTemplate.query(TIMELINE_SQL, new MapSqlParameterSource()
                        .addValue("sessionId", sessionId)
                        .addValue("bucketSeconds", bucketSeconds),
                (resultSet, rowNum) -> new BilibiliLiveSessionInsightView.TimeBucket(
                        resultSet.getObject("bucket_start", OffsetDateTime.class),
                        resultSet.getLong("danmaku_count"),
                        resultSet.getLong("paid_event_count"),
                        resultSet.getLong("paid_amount_milli_yuan"),
                        resultSet.getLong("active_user_count")));
    }

    public List<GiftAggregate> findGiftMix(Long sessionId) {
        return jdbcTemplate.query(GIFT_MIX_SQL, new MapSqlParameterSource("sessionId", sessionId),
                (resultSet, rowNum) -> new GiftAggregate(
                        resultSet.getString("gift_name"),
                        resultSet.getString("event_kind"),
                        resultSet.getLong("gift_count"),
                        resultSet.getLong("paid_amount_milli_yuan")));
    }

    public QualityStats findQuality(Long sessionId) {
        return jdbcTemplate.queryForObject(QUALITY_SQL, new MapSqlParameterSource("sessionId", sessionId),
                (resultSet, rowNum) -> {
                    Number latency = (Number) resultSet.getObject("latency_p95_millis");
                    return new QualityStats(
                            resultSet.getLong("supported_event_count"),
                            resultSet.getLong("resolved_event_count"),
                            latency == null ? null : Math.round(latency.doubleValue()));
                });
    }

    public List<BilibiliLiveSessionInsightView.UserSegment> findUserSegments(Long sessionId) {
        return jdbcTemplate.queryForObject(
                USER_SEGMENTS_SQL,
                new MapSqlParameterSource("sessionId", sessionId),
                (resultSet, rowNum) -> List.of(
                        new BilibiliLiveSessionInsightView.UserSegment(
                                "CORE_SUPPORTER", "核心支持者", resultSet.getLong("core_count"),
                                "既有弹幕互动，也发生付费"),
                        new BilibiliLiveSessionInsightView.UserSegment(
                                "SILENT_PAYER", "静默付费者", resultSet.getLong("silent_count"),
                                "有付费，但本场没有弹幕"),
                        new BilibiliLiveSessionInsightView.UserSegment(
                                "ACTIVE_UNPAID", "活跃未付费", resultSet.getLong("active_unpaid_count"),
                                "至少 3 条弹幕，尚未付费"),
                        new BilibiliLiveSessionInsightView.UserSegment(
                                "CASUAL_INTERACTOR", "轻度互动", resultSet.getLong("casual_count"),
                                "少于 3 条弹幕，尚未付费")
                ));
    }

    public DanmakuDepthStats findDanmakuDepth(Long sessionId) {
        return jdbcTemplate.queryForObject(
                DANMAKU_DEPTH_SQL,
                new MapSqlParameterSource("sessionId", sessionId),
                (resultSet, rowNum) -> new DanmakuDepthStats(
                        resultSet.getLong("identified_danmaku_count"),
                        resultSet.getLong("identified_danmaku_user_count"),
                        resultSet.getLong("repeat_user_count"),
                        resultSet.getLong("sustained_user_count"),
                        resultSet.getLong("nonblank_message_count"),
                        resultSet.getLong("distinct_message_count")));
    }

    public List<DanmakuStageAggregate> findDanmakuStages(Long sessionId) {
        return jdbcTemplate.query(
                DANMAKU_STAGES_SQL,
                new MapSqlParameterSource("sessionId", sessionId),
                (resultSet, rowNum) -> new DanmakuStageAggregate(
                        resultSet.getInt("stage_no"),
                        resultSet.getLong("danmaku_count"),
                        resultSet.getLong("active_user_count")));
    }

    public List<RepeatedMessageAggregate> findRepeatedMessages(Long sessionId) {
        return jdbcTemplate.query(
                REPEATED_MESSAGES_SQL,
                new MapSqlParameterSource("sessionId", sessionId),
                (resultSet, rowNum) -> new RepeatedMessageAggregate(
                        resultSet.getString("message_text"),
                        resultSet.getLong("message_count"),
                        resultSet.getLong("user_count")));
    }

    public PaymentDepthStats findPaymentDepth(Long sessionId) {
        return jdbcTemplate.queryForObject(
                PAYMENT_DEPTH_SQL,
                new MapSqlParameterSource("sessionId", sessionId),
                (resultSet, rowNum) -> new PaymentDepthStats(
                        resultSet.getLong("payer_count"),
                        resultSet.getLong("repeat_payer_count"),
                        resultSet.getLong("engaged_payer_count"),
                        resultSet.getLong("returning_payer_count"),
                        roundedLong(resultSet.getObject("median_payer_amount_milli_yuan")),
                        roundedLong(resultSet.getObject("median_conversion_lag_seconds")),
                        resultSet.getLong("top_one_paid_amount_milli_yuan")));
    }

    public List<SpendTierAggregate> findSpendTiers(Long sessionId) {
        return jdbcTemplate.query(
                SPEND_TIERS_SQL,
                new MapSqlParameterSource("sessionId", sessionId),
                (resultSet, rowNum) -> new SpendTierAggregate(
                        resultSet.getString("tier_code"),
                        resultSet.getLong("user_count"),
                        resultSet.getLong("paid_amount_milli_yuan")));
    }

    private Long roundedLong(Object value) {
        return value instanceof Number number ? Math.round(number.doubleValue()) : null;
    }

    public record CoverageInterval(OffsetDateTime activeFrom, OffsetDateTime activeTo) {
    }

    public record GiftAggregate(String giftName, String eventKind, long giftCount, long paidAmountMilliYuan) {
    }

    public record QualityStats(long supportedEventCount, long resolvedEventCount, Long latencyP95Millis) {
    }

    public record DanmakuDepthStats(
            long identifiedDanmakuCount,
            long identifiedDanmakuUserCount,
            long repeatUserCount,
            long sustainedUserCount,
            long nonblankMessageCount,
            long distinctMessageCount
    ) {
    }

    public record DanmakuStageAggregate(int stageNo, long danmakuCount, long activeUserCount) {
    }

    public record RepeatedMessageAggregate(String messageText, long messageCount, long userCount) {
    }

    public record PaymentDepthStats(
            long payerCount,
            long repeatPayerCount,
            long engagedPayerCount,
            long returningPayerCount,
            Long medianPayerAmountMilliYuan,
            Long medianConversionLagSeconds,
            long topOnePaidAmountMilliYuan
    ) {
    }

    public record SpendTierAggregate(String code, long userCount, long paidAmountMilliYuan) {
    }
}
