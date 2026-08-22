package com.socialmonitor.bilibili.live.session.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionInsightView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionUserView;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BilibiliLiveSessionInsightCalculatorTests {

    private final BilibiliLiveSessionInsightCalculator calculator = new BilibiliLiveSessionInsightCalculator();

    @Test
    void mergesOverlappingCoverageIntervalsBeforeCalculatingDanmakuRate() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-16T12:00:00+08:00");
        List<BilibiliLiveSessionInsightRepository.CoverageInterval> intervals = List.of(
                new BilibiliLiveSessionInsightRepository.CoverageInterval(start, start.plusMinutes(10)),
                new BilibiliLiveSessionInsightRepository.CoverageInterval(start.plusMinutes(8), start.plusMinutes(20))
        );

        long coveredSeconds = calculator.mergeCoveredSeconds(intervals);
        BilibiliLiveSessionInsightView.Kpis kpis = calculator.calculateKpis(
                summary(start, 40L, 2L, 10_000L), coveredSeconds, List.of(
                        user("uid:1", 1L, 6_000L, 4L),
                        user("uid:2", 1L, 4_000L, 0L)));

        assertThat(coveredSeconds).isEqualTo(1_200L);
        assertThat(kpis.danmakuPerMinute()).isEqualTo(2.0);
        assertThat(kpis.payerConversionRate()).isEqualTo(0.2);
        assertThat(kpis.arppuMilliYuan()).isEqualTo(5_000L);
        assertThat(kpis.topFiveRevenueShare()).isEqualTo(1.0);
    }

    @Test
    void preservesUnknownMetricsWhenCoverageCannotSupportZero() {
        BilibiliLiveSessionSummaryView summary = summary(
                OffsetDateTime.parse("2026-08-16T12:00:00+08:00"), null, null, null);

        BilibiliLiveSessionInsightView.Kpis kpis = calculator.calculateKpis(summary, 0L, List.of());

        assertThat(kpis.danmakuPerMinute()).isNull();
        assertThat(kpis.payerConversionRate()).isNull();
        assertThat(kpis.arppuMilliYuan()).isNull();
        assertThat(kpis.topFiveRevenueShare()).isNull();
    }

    @Test
    void createsExclusiveVerifiedUserSegmentsAndExplainableFindings() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-16T12:00:00+08:00");
        List<BilibiliLiveSessionUserView> users = List.of(
                user("uid:1", 2L, 9_000L, 8L),
                user("uid:2", 1L, 1_000L, 0L),
                user("uid:3", 0L, 0L, 5L),
                user("uid:4", 0L, 0L, 1L),
                new BilibiliLiveSessionUserView("event:9", "UNRESOLVED_EVENT", null, "匿名",
                        20L, 0L, 0L, 0L, 0L, 0L, start, start)
        );
        List<BilibiliLiveSessionInsightView.TimeBucket> buckets = List.of(
                new BilibiliLiveSessionInsightView.TimeBucket(start, 1L, 0L, 0L, 1L),
                new BilibiliLiveSessionInsightView.TimeBucket(start.plusMinutes(5), 12L, 0L, 0L, 5L),
                new BilibiliLiveSessionInsightView.TimeBucket(start.plusMinutes(10), 2L, 2L, 10_000L, 2L)
        );

        assertThat(calculator.segmentUsers(users))
                .extracting(BilibiliLiveSessionInsightView.UserSegment::code,
                        BilibiliLiveSessionInsightView.UserSegment::userCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("CORE_SUPPORTER", 1L),
                        org.assertj.core.groups.Tuple.tuple("SILENT_PAYER", 1L),
                        org.assertj.core.groups.Tuple.tuple("ACTIVE_UNPAID", 1L),
                        org.assertj.core.groups.Tuple.tuple("CASUAL_INTERACTOR", 1L));

        BilibiliLiveSessionInsightView.Kpis kpis = new BilibiliLiveSessionInsightView.Kpis(
                3.0, 0.5, 5_000L, 10_000L, 0.9);
        assertThat(calculator.findings(buckets, kpis, null, null))
                .extracting(BilibiliLiveSessionInsightView.Finding::code)
                .contains("HEAT_WITHOUT_PAYMENT", "REVENUE_CONCENTRATION");
    }

    @Test
    void calculatesDanmakuDepthFromVerifiedUsersAndStageTotals() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-16T12:00:00+08:00");
        BilibiliLiveSessionInsightRepository.DanmakuDepthStats stats =
                new BilibiliLiveSessionInsightRepository.DanmakuDepthStats(
                        100L, 20L, 8L, 5L, 120L, 90L);
        List<BilibiliLiveSessionInsightRepository.DanmakuStageAggregate> stages = List.of(
                new BilibiliLiveSessionInsightRepository.DanmakuStageAggregate(0, 40L, 12L),
                new BilibiliLiveSessionInsightRepository.DanmakuStageAggregate(1, 35L, 10L),
                new BilibiliLiveSessionInsightRepository.DanmakuStageAggregate(2, 25L, 8L));
        List<BilibiliLiveSessionInsightRepository.RepeatedMessageAggregate> repeated = List.of(
                new BilibiliLiveSessionInsightRepository.RepeatedMessageAggregate("主播加油", 6L, 4L));

        BilibiliLiveSessionInsightView.DanmakuDepth depth = calculator.calculateDanmakuDepth(
                summary(start, 120L, 2L, 10_000L), stats, stages, repeated);

        assertThat(depth.messagesPerActiveUser()).isEqualTo(5.0);
        assertThat(depth.repeatInteractionRate()).isEqualTo(0.4);
        assertThat(depth.sustainedParticipationRate()).isEqualTo(0.25);
        assertThat(depth.duplicateMessageRate()).isEqualTo(0.25);
        assertThat(depth.stages())
                .extracting(BilibiliLiveSessionInsightView.DanmakuStage::code,
                        BilibiliLiveSessionInsightView.DanmakuStage::messageShare)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("OPENING", 0.4),
                        org.assertj.core.groups.Tuple.tuple("MIDDLE", 0.35),
                        org.assertj.core.groups.Tuple.tuple("ENDING", 0.25));
        assertThat(depth.repeatedMessages().get(0).messageText()).isEqualTo("主播加油");
    }

    @Test
    void calculatesPaymentDepthAndSpendTierShares() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-16T12:00:00+08:00");
        BilibiliLiveSessionInsightRepository.PaymentDepthStats stats =
                new BilibiliLiveSessionInsightRepository.PaymentDepthStats(
                        10L, 4L, 7L, 3L, 2_500L, 120L, 6_000L);
        List<BilibiliLiveSessionInsightRepository.SpendTierAggregate> tiers = List.of(
                new BilibiliLiveSessionInsightRepository.SpendTierAggregate("LIGHT", 3L, 900L),
                new BilibiliLiveSessionInsightRepository.SpendTierAggregate("STANDARD", 5L, 5_100L),
                new BilibiliLiveSessionInsightRepository.SpendTierAggregate("CORE", 2L, 4_000L));

        BilibiliLiveSessionInsightView.PaymentDepth depth = calculator.calculatePaymentDepth(
                summary(start, 120L, 10L, 10_000L), stats, tiers);

        assertThat(depth.repeatPayerRate()).isEqualTo(0.4);
        assertThat(depth.engagedPayerRate()).isEqualTo(0.7);
        assertThat(depth.returningPayerRate()).isEqualTo(0.3);
        assertThat(depth.medianPayerAmountMilliYuan()).isEqualTo(2_500L);
        assertThat(depth.medianConversionLagSeconds()).isEqualTo(120L);
        assertThat(depth.topOneRevenueShare()).isEqualTo(0.6);
        assertThat(depth.spendTiers())
                .extracting(BilibiliLiveSessionInsightView.SpendTier::code,
                        BilibiliLiveSessionInsightView.SpendTier::revenueShare)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("LIGHT", 0.09),
                        org.assertj.core.groups.Tuple.tuple("STANDARD", 0.51),
                        org.assertj.core.groups.Tuple.tuple("CORE", 0.4));
    }

    @Test
    void preservesUnknownDepthMetricsForBoundaryOnlySessions() {
        OffsetDateTime start = OffsetDateTime.parse("2026-08-16T12:00:00+08:00");
        BilibiliLiveSessionSummaryView unknown = summary(start, null, null, null);

        BilibiliLiveSessionInsightView.DanmakuDepth danmaku = calculator.calculateDanmakuDepth(
                unknown,
                new BilibiliLiveSessionInsightRepository.DanmakuDepthStats(0L, 0L, 0L, 0L, 0L, 0L),
                List.of(), List.of());
        BilibiliLiveSessionInsightView.PaymentDepth payment = calculator.calculatePaymentDepth(
                unknown,
                new BilibiliLiveSessionInsightRepository.PaymentDepthStats(
                        0L, 0L, 0L, 0L, null, null, 0L),
                List.of());

        assertThat(danmaku.messagesPerActiveUser()).isNull();
        assertThat(danmaku.stages()).isEmpty();
        assertThat(payment.repeatPayerRate()).isNull();
        assertThat(payment.spendTiers()).isEmpty();
    }

    private BilibiliLiveSessionSummaryView summary(
            OffsetDateTime startedAt,
            Long danmakuCount,
            Long paidUserCount,
            Long paidAmountMilliYuan
    ) {
        return new BilibiliLiveSessionSummaryView(
                42L, 7L, 1001L, 2002L, "CLOSED", startedAt, startedAt.plusHours(1),
                "WEBSOCKET", "WEBSOCKET", danmakuCount == null ? "BOUNDARY_ONLY" : "RECEIVED_WHILE_ONLINE", 2L,
                startedAt, startedAt.plusMinutes(20), danmakuCount, 2L, 2L, 0L, 2L,
                paidUserCount, paidUserCount == null ? null : 10L, 0L, 0L, 0L, 2L,
                paidAmountMilliYuan, startedAt, startedAt.plusMinutes(20));
    }

    private BilibiliLiveSessionUserView user(String actorKey, long paidEvents, long amount, long danmaku) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-08-16T12:01:00+08:00");
        return new BilibiliLiveSessionUserView(actorKey, "VERIFIED_UID",
                Long.parseLong(actorKey.substring(4)), actorKey, danmaku, paidEvents, paidEvents,
                0L, paidEvents, amount, timestamp, timestamp.plusMinutes(10));
    }
}
