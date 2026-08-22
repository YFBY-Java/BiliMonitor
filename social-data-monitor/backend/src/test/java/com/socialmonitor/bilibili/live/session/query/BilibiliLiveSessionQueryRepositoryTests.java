package com.socialmonitor.bilibili.live.session.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class BilibiliLiveSessionQueryRepositoryTests {

    @Test
    void summarySqlSeparatesCanonicalGiftsFromPaidFinancialEvents() {
        String sql = compact(BilibiliLiveSessionQueryRepository.SESSION_SUMMARY_SELECT);

        assertThat(sql)
                .contains("COUNT(*) FILTER (WHERE event_kind = 'GIFT') AS gift_event_count")
                .contains("FILTER (WHERE event_kind = 'GIFT'), 0) AS gift_count")
                .contains("event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')")
                .contains("AS paid_event_count")
                .doesNotContain("event_kind IN ('NOTIFICATION'")
                .doesNotContain("event_kind IN ('COMBO'")
                .doesNotContain("event_kind IN ('USER_TOAST'");
    }

    @Test
    void recentSummarySelectsTheBoundedCandidateSessionsBeforeJoiningEvents() {
        String sql = compact(BilibiliLiveSessionQueryRepository.RECENT_SESSIONS_SQL);

        assertThat(sql)
                .startsWith("WITH candidate_sessions AS")
                .contains("WHERE monitor_id = :monitorId ORDER BY started_at DESC, id DESC LIMIT :limit")
                .contains("JOIN candidate_sessions candidate ON candidate.id = event.live_session_id");
    }

    @Test
    void userSqlUsesOnlyPositiveUidAsVerifiedIdentityAndKeepsUidlessEventsSeparate() {
        String sql = compact(BilibiliLiveSessionQueryRepository.SESSION_USERS_SQL);

        assertThat(sql)
                .contains("sender_uid IS NOT NULL AND sender_uid > 0")
                .contains("'uid:' || sender_uid::text")
                .contains("'event:' || event.id::text")
                .contains("'VERIFIED_UID'")
                .contains("'UNRESOLVED_EVENT'")
                .contains("GROUP BY actor_key, identity_quality")
                .doesNotContain("'name:'", "REGEXP_REPLACE")
                .contains("event_kind IN ('DANMAKU', 'GIFT', 'SUPER_CHAT', 'GUARD_BUY')");
    }

    @Test
    void eventSqlCastsNullableFiltersBeforeNullChecksForPostgresql() {
        String sql = compact(BilibiliLiveSessionQueryRepository.SESSION_EVENTS_SQL);

        assertThat(sql)
                .contains("CAST(:kind AS varchar) IS NULL")
                .contains("CAST(:userUid AS bigint) IS NULL")
                .contains("CAST(:paid AS boolean) IS NULL")
                .contains("CAST(:keyword AS varchar) IS NULL");
    }

    @Test
    void summarySqlKeepsGiftSendersSeparateFromPaidSpenders() {
        String sql = compact(BilibiliLiveSessionQueryRepository.SESSION_SUMMARY_SELECT);

        assertThat(sql)
                .contains("COUNT(DISTINCT sender_uid) FILTER (WHERE sender_uid IS NOT NULL"
                        + " AND sender_uid > 0 AND event_kind = 'GIFT') AS gift_sender_count")
                .contains("event_kind IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')"
                        + " AND (COALESCE(paid, false) = true"
                        + " OR COALESCE(paid_amount_milli_yuan, 0) > 0)) AS paid_user_count");
    }

    @Test
    void summarySqlExposesUnresolvedCountsAndTransportCoverageWithoutFullArrayAggregation() {
        String sql = compact(BilibiliLiveSessionQueryRepository.SESSION_SUMMARY_SELECT);
        String usersSql = compact(BilibiliLiveSessionQueryRepository.SESSION_USERS_SQL);

        assertThat(sql)
                .contains("unresolved_interacting_event_count")
                .contains("unresolved_gift_event_count")
                .contains("unresolved_paid_event_count")
                .contains("transport_session_count")
                .contains("capture_started_at")
                .contains("capture_ended_at")
                .contains("'BOUNDARY_ONLY'")
                .contains("'NO_ONLINE_COVERAGE'")
                .contains("'RECEIVED_WHILE_ONLINE'")
                .contains("transport.connected_at")
                .contains("session.start_source NOT LIKE 'STATUS_EVENT_BACKFILL%'")
                .contains("session.start_source LIKE 'STATUS_EVENT_BACKFILL%'");
        assertThat(usersSql)
                .contains("DISTINCT ON (actor_key)")
                .doesNotContain("ARRAY_AGG");
    }

    @Test
    void transportCoverageUsesStrictHalfOpenIntervalIntersectionAndTruthfulOpenEnd() {
        String sql = compact(BilibiliLiveSessionQueryRepository.SESSION_SUMMARY_SELECT);

        assertThat(sql)
                .contains("MIN(GREATEST(transport.connected_at, session.started_at))"
                        + " FILTER (WHERE transport.id IS NOT NULL) AS capture_started_at")
                .contains("MAX(LEAST(session.ended_at, transport.ended_at))")
                .contains("session.ended_at IS NULL OR transport.connected_at < session.ended_at")
                .contains("transport.ended_at IS NULL OR transport.ended_at > session.started_at")
                .contains("BOOL_OR(session.ended_at IS NULL AND transport.ended_at IS NULL)")
                .contains("GREATEST(transport.connected_at, session.started_at) < LEAST(")
                .contains("COALESCE(transport.ended_at, 'infinity'::timestamptz)")
                .contains("COALESCE(session.ended_at, 'infinity'::timestamptz)")
                .doesNotContain("transport.connected_at <= COALESCE(session.ended_at, CURRENT_TIMESTAMP)")
                .doesNotContain("COALESCE(transport.ended_at, CURRENT_TIMESTAMP) >= session.started_at");
    }

    @Test
    void summaryMapperPreservesHistoricalUnknownsButCurrentObservedZeros() throws Exception {
        assertThat(compact(BilibiliLiveSessionQueryRepository.SESSION_SUMMARY_SELECT))
                .contains("CASE WHEN session.start_source LIKE 'STATUS_EVENT_BACKFILL%'"
                        + " AND aggregate.first_event_at IS NULL THEN NULL"
                        + " ELSE COALESCE(aggregate.danmaku_count, 0) END AS danmaku_count");
        BilibiliLiveSessionSummaryView historical = mapSummaryFixture(
                "STATUS_EVENT_BACKFILL_INCOMPLETE", "BOUNDARY_ONLY", true, 0L, false);
        BilibiliLiveSessionSummaryView current = mapSummaryFixture(
                "WEBSOCKET", "RECEIVED_WHILE_ONLINE", false, 1L, true);
        BilibiliLiveSessionSummaryView noCoverage = mapSummaryFixture(
                "WEBSOCKET", "NO_ONLINE_COVERAGE", false, 0L, false);

        assertThat(historical.danmakuCount()).isNull();
        assertThat(historical.giftSenderCount()).isNull();
        assertThat(historical.paidUserCount()).isNull();
        assertThat(historical.paidAmountMilliYuan()).isNull();
        assertThat(current.danmakuCount()).isZero();
        assertThat(current.giftSenderCount()).isZero();
        assertThat(current.paidUserCount()).isZero();
        assertThat(current.paidAmountMilliYuan()).isZero();
        assertThat(current.captureStartedAt())
                .isEqualTo(OffsetDateTime.parse("2026-08-16T12:05:00+08:00"));
        assertThat(current.captureEndedAt()).as("open session and transport intersection").isNull();
        assertThat(noCoverage.transportSessionCount()).isZero();
        assertThat(noCoverage.coverageStatus()).isEqualTo("NO_ONLINE_COVERAGE");
        assertThat(noCoverage.captureStartedAt()).isNull();
        assertThat(noCoverage.captureEndedAt()).isNull();
    }

    private BilibiliLiveSessionSummaryView mapSummaryFixture(
            String startSource,
            String coverageStatus,
            boolean metricsNull,
            long transportSessionCount,
            boolean hasCaptureStart
    ) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-08-16T12:00:00+08:00");
        when(resultSet.getLong("id")).thenReturn(42L);
        when(resultSet.getLong("monitor_id")).thenReturn(7L);
        when(resultSet.getLong("uid")).thenReturn(1001L);
        when(resultSet.getLong("room_id")).thenReturn(2002L);
        when(resultSet.getLong("transport_session_count")).thenReturn(transportSessionCount);
        when(resultSet.getString("state")).thenReturn(metricsNull ? "INCOMPLETE" : "OPEN");
        when(resultSet.getString("start_source")).thenReturn(startSource);
        when(resultSet.getString("coverage_status")).thenReturn(coverageStatus);
        when(resultSet.getObject("started_at", OffsetDateTime.class)).thenReturn(startedAt);
        when(resultSet.getObject("capture_started_at", OffsetDateTime.class))
                .thenReturn(hasCaptureStart ? startedAt.plusMinutes(5) : null);
        when(resultSet.wasNull()).thenReturn(metricsNull);

        BilibiliLiveSessionQueryRepository repository =
                new BilibiliLiveSessionQueryRepository(mock(NamedParameterJdbcTemplate.class));
        Method mapper = BilibiliLiveSessionQueryRepository.class
                .getDeclaredMethod("mapSummary", ResultSet.class, int.class);
        mapper.setAccessible(true);
        return (BilibiliLiveSessionSummaryView) mapper.invoke(repository, resultSet, 0);
    }

    private String compact(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
