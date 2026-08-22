package com.socialmonitor.bilibili.live.session.insight;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionInsightView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionUserView;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BilibiliLiveSessionInsightCalculator {

    public long mergeCoveredSeconds(List<BilibiliLiveSessionInsightRepository.CoverageInterval> intervals) {
        List<BilibiliLiveSessionInsightRepository.CoverageInterval> ordered = intervals.stream()
                .filter(interval -> interval.activeFrom() != null && interval.activeTo() != null
                        && interval.activeFrom().isBefore(interval.activeTo()))
                .sorted(Comparator.comparing(BilibiliLiveSessionInsightRepository.CoverageInterval::activeFrom))
                .toList();
        if (ordered.isEmpty()) {
            return 0L;
        }

        long seconds = 0L;
        OffsetDateTime currentStart = ordered.get(0).activeFrom();
        OffsetDateTime currentEnd = ordered.get(0).activeTo();
        for (int index = 1; index < ordered.size(); index++) {
            BilibiliLiveSessionInsightRepository.CoverageInterval interval = ordered.get(index);
            if (!interval.activeFrom().isAfter(currentEnd)) {
                if (interval.activeTo().isAfter(currentEnd)) {
                    currentEnd = interval.activeTo();
                }
                continue;
            }
            seconds += Duration.between(currentStart, currentEnd).toSeconds();
            currentStart = interval.activeFrom();
            currentEnd = interval.activeTo();
        }
        return seconds + Duration.between(currentStart, currentEnd).toSeconds();
    }

    public BilibiliLiveSessionInsightView.Kpis calculateKpis(
            BilibiliLiveSessionSummaryView summary,
            long coveredSeconds,
            List<BilibiliLiveSessionUserView> users
    ) {
        if (summary.danmakuCount() == null || summary.paidUserCount() == null
                || summary.interactingUserCount() == null || summary.paidAmountMilliYuan() == null) {
            return new BilibiliLiveSessionInsightView.Kpis(null, null, null, null, null);
        }
        Double danmakuRate = coveredSeconds > 0
                ? round((summary.danmakuCount() * 60.0) / coveredSeconds)
                : null;
        double conversion = summary.interactingUserCount() == 0
                ? 0.0
                : round(summary.paidUserCount().doubleValue() / summary.interactingUserCount());
        long arppu = summary.paidUserCount() == 0
                ? 0L
                : Math.round(summary.paidAmountMilliYuan().doubleValue() / summary.paidUserCount());
        long topFiveAmount = users.stream()
                .filter(this::verified)
                .map(BilibiliLiveSessionUserView::paidAmountMilliYuan)
                .sorted(Comparator.reverseOrder())
                .limit(5)
                .mapToLong(Long::longValue)
                .sum();
        double concentration = summary.paidAmountMilliYuan() == 0
                ? 0.0
                : round(Math.min(1.0, topFiveAmount / summary.paidAmountMilliYuan().doubleValue()));
        return new BilibiliLiveSessionInsightView.Kpis(
                danmakuRate, conversion, arppu, summary.paidAmountMilliYuan(), concentration);
    }

    public List<BilibiliLiveSessionInsightView.UserSegment> segmentUsers(
            List<BilibiliLiveSessionUserView> users
    ) {
        long core = users.stream().filter(this::verified)
                .filter(user -> user.paidAmountMilliYuan() > 0 && user.danmakuCount() > 0).count();
        long silent = users.stream().filter(this::verified)
                .filter(user -> user.paidAmountMilliYuan() > 0 && user.danmakuCount() == 0).count();
        long activeUnpaid = users.stream().filter(this::verified)
                .filter(user -> user.paidAmountMilliYuan() == 0 && user.danmakuCount() >= 3).count();
        long casual = users.stream().filter(this::verified)
                .filter(user -> user.paidAmountMilliYuan() == 0 && user.danmakuCount() < 3).count();
        return List.of(
                new BilibiliLiveSessionInsightView.UserSegment(
                        "CORE_SUPPORTER", "核心支持者", core, "既有弹幕互动，也发生付费"),
                new BilibiliLiveSessionInsightView.UserSegment(
                        "SILENT_PAYER", "静默付费者", silent, "有付费，但本场没有弹幕"),
                new BilibiliLiveSessionInsightView.UserSegment(
                        "ACTIVE_UNPAID", "活跃未付费", activeUnpaid, "至少 3 条弹幕，尚未付费"),
                new BilibiliLiveSessionInsightView.UserSegment(
                        "CASUAL_INTERACTOR", "轻度互动", casual, "少于 3 条弹幕，尚未付费")
        );
    }

    public List<BilibiliLiveSessionInsightView.Peak> peaks(
            List<BilibiliLiveSessionInsightView.TimeBucket> buckets
    ) {
        List<BilibiliLiveSessionInsightView.Peak> peaks = new ArrayList<>();
        buckets.stream().max(Comparator.comparingLong(BilibiliLiveSessionInsightView.TimeBucket::danmakuCount))
                .filter(bucket -> bucket.danmakuCount() > 0)
                .ifPresent(bucket -> peaks.add(new BilibiliLiveSessionInsightView.Peak(
                        "INTERACTION", bucket.bucketStart(), bucket.danmakuCount(), "弹幕峰值")));
        buckets.stream().max(Comparator.comparingLong(BilibiliLiveSessionInsightView.TimeBucket::paidAmountMilliYuan))
                .filter(bucket -> bucket.paidAmountMilliYuan() > 0)
                .ifPresent(bucket -> peaks.add(new BilibiliLiveSessionInsightView.Peak(
                        "REVENUE", bucket.bucketStart(), bucket.paidAmountMilliYuan(), "付费峰值")));
        return peaks;
    }

    public List<BilibiliLiveSessionInsightView.Finding> findings(
            List<BilibiliLiveSessionInsightView.TimeBucket> buckets,
            BilibiliLiveSessionInsightView.Kpis kpis,
            BilibiliLiveSessionInsightView.DanmakuDepth danmakuDepth,
            BilibiliLiveSessionInsightView.PaymentDepth paymentDepth
    ) {
        List<BilibiliLiveSessionInsightView.Finding> findings = new ArrayList<>();
        buckets.stream()
                .filter(bucket -> bucket.danmakuCount() >= 5 && bucket.paidAmountMilliYuan() == 0)
                .max(Comparator.comparingLong(BilibiliLiveSessionInsightView.TimeBucket::danmakuCount))
                .ifPresent(bucket -> findings.add(new BilibiliLiveSessionInsightView.Finding(
                        "HEAT_WITHOUT_PAYMENT", "OPPORTUNITY", "高互动未转化",
                        "弹幕峰值时段没有付费，可回看当时话题并尝试增加明确的支持引导。")));
        if (kpis.topFiveRevenueShare() != null && kpis.paidAmountMilliYuan() != null
                && kpis.paidAmountMilliYuan() > 0 && kpis.topFiveRevenueShare() >= 0.8) {
            findings.add(new BilibiliLiveSessionInsightView.Finding(
                    "REVENUE_CONCENTRATION", "RISK", "收入集中度较高",
                    "Top 5 付费用户贡献达到 80% 以上，建议同时维护核心用户并扩大普通付费用户规模。"));
        }
        if (danmakuDepth != null && danmakuDepth.identifiedDanmakuUserCount() != null
                && danmakuDepth.identifiedDanmakuUserCount() >= 20
                && danmakuDepth.repeatInteractionRate() != null
                && danmakuDepth.repeatInteractionRate() < 0.2) {
            findings.add(new BilibiliLiveSessionInsightView.Finding(
                    "LOW_REPEAT_INTERACTION", "OPPORTUNITY", "重复互动偏低",
                    "发过 3 条及以上弹幕的用户不足 20%，可增加追问、投票或连续话题来促成二次互动。"));
        }
        if (paymentDepth != null && paymentDepth.payerCount() != null
                && paymentDepth.payerCount() >= 5
                && paymentDepth.returningPayerRate() != null
                && paymentDepth.returningPayerRate() < 0.2) {
            findings.add(new BilibiliLiveSessionInsightView.Finding(
                    "LOW_RETURNING_PAYER", "OPPORTUNITY", "历史复购者偏少",
                    "已记录历史中再次付费的用户不足 20%，可加强感谢反馈和下次直播召回。"));
        }
        if (findings.isEmpty()) {
            findings.add(new BilibiliLiveSessionInsightView.Finding(
                    "NO_STRONG_SIGNAL", "INFO", "暂未发现强信号",
                    "当前样本没有命中高置信规则，建议结合更多场次观察。"));
        }
        return findings;
    }

    public BilibiliLiveSessionInsightView.DanmakuDepth calculateDanmakuDepth(
            BilibiliLiveSessionSummaryView summary,
            BilibiliLiveSessionInsightRepository.DanmakuDepthStats stats,
            List<BilibiliLiveSessionInsightRepository.DanmakuStageAggregate> stageRows,
            List<BilibiliLiveSessionInsightRepository.RepeatedMessageAggregate> repeatedRows
    ) {
        if (summary.danmakuCount() == null) {
            return new BilibiliLiveSessionInsightView.DanmakuDepth(
                    null, null, null, null, null, null, List.of(), List.of());
        }
        long userCount = stats.identifiedDanmakuUserCount();
        long totalStageMessages = stageRows.stream()
                .mapToLong(BilibiliLiveSessionInsightRepository.DanmakuStageAggregate::danmakuCount)
                .sum();
        Map<Integer, BilibiliLiveSessionInsightRepository.DanmakuStageAggregate> byStage = new HashMap<>();
        stageRows.forEach(row -> byStage.put(row.stageNo(), row));
        List<BilibiliLiveSessionInsightView.DanmakuStage> stages = List.of(
                danmakuStage(0, "OPENING", "开场", byStage, totalStageMessages),
                danmakuStage(1, "MIDDLE", "中段", byStage, totalStageMessages),
                danmakuStage(2, "ENDING", "收尾", byStage, totalStageMessages));
        List<BilibiliLiveSessionInsightView.RepeatedMessage> repeatedMessages = repeatedRows.stream()
                .map(row -> new BilibiliLiveSessionInsightView.RepeatedMessage(
                        row.messageText(), row.messageCount(), row.userCount()))
                .toList();
        return new BilibiliLiveSessionInsightView.DanmakuDepth(
                userCount,
                stats.identifiedDanmakuCount(),
                userCount == 0 ? 0.0 : round(stats.identifiedDanmakuCount() / (double) userCount),
                userCount == 0 ? 0.0 : round(stats.repeatUserCount() / (double) userCount),
                userCount == 0 ? 0.0 : round(stats.sustainedUserCount() / (double) userCount),
                stats.nonblankMessageCount() == 0
                        ? 0.0
                        : round(1.0 - stats.distinctMessageCount() / (double) stats.nonblankMessageCount()),
                stages,
                repeatedMessages);
    }

    public BilibiliLiveSessionInsightView.PaymentDepth calculatePaymentDepth(
            BilibiliLiveSessionSummaryView summary,
            BilibiliLiveSessionInsightRepository.PaymentDepthStats stats,
            List<BilibiliLiveSessionInsightRepository.SpendTierAggregate> tierRows
    ) {
        if (summary.paidUserCount() == null || summary.paidAmountMilliYuan() == null) {
            return new BilibiliLiveSessionInsightView.PaymentDepth(
                    null, null, null, null, null, null, null, List.of());
        }
        long payerCount = stats.payerCount();
        long totalPaidAmount = summary.paidAmountMilliYuan();
        Map<String, BilibiliLiveSessionInsightRepository.SpendTierAggregate> byTier = new HashMap<>();
        tierRows.forEach(row -> byTier.put(row.code(), row));
        List<BilibiliLiveSessionInsightView.SpendTier> tiers = List.of(
                spendTier("LIGHT", "轻量支持", byTier, totalPaidAmount),
                spendTier("STANDARD", "常规支持", byTier, totalPaidAmount),
                spendTier("CORE", "核心支持", byTier, totalPaidAmount));
        return new BilibiliLiveSessionInsightView.PaymentDepth(
                payerCount,
                payerCount == 0 ? 0.0 : round(stats.repeatPayerCount() / (double) payerCount),
                payerCount == 0 ? 0.0 : round(stats.engagedPayerCount() / (double) payerCount),
                payerCount == 0 ? 0.0 : round(stats.returningPayerCount() / (double) payerCount),
                stats.medianPayerAmountMilliYuan(),
                stats.medianConversionLagSeconds(),
                totalPaidAmount == 0
                        ? 0.0
                        : round(Math.min(1.0, stats.topOnePaidAmountMilliYuan() / (double) totalPaidAmount)),
                tiers);
    }

    private BilibiliLiveSessionInsightView.DanmakuStage danmakuStage(
            int stageNo,
            String code,
            String label,
            Map<Integer, BilibiliLiveSessionInsightRepository.DanmakuStageAggregate> byStage,
            long totalMessages
    ) {
        BilibiliLiveSessionInsightRepository.DanmakuStageAggregate row = byStage.getOrDefault(
                stageNo, new BilibiliLiveSessionInsightRepository.DanmakuStageAggregate(stageNo, 0L, 0L));
        return new BilibiliLiveSessionInsightView.DanmakuStage(
                code,
                label,
                row.danmakuCount(),
                row.activeUserCount(),
                totalMessages == 0 ? 0.0 : round(row.danmakuCount() / (double) totalMessages));
    }

    private BilibiliLiveSessionInsightView.SpendTier spendTier(
            String code,
            String label,
            Map<String, BilibiliLiveSessionInsightRepository.SpendTierAggregate> byTier,
            long totalPaidAmount
    ) {
        BilibiliLiveSessionInsightRepository.SpendTierAggregate row = byTier.getOrDefault(
                code, new BilibiliLiveSessionInsightRepository.SpendTierAggregate(code, 0L, 0L));
        return new BilibiliLiveSessionInsightView.SpendTier(
                code,
                label,
                row.userCount(),
                row.paidAmountMilliYuan(),
                totalPaidAmount == 0 ? 0.0 : round(row.paidAmountMilliYuan() / (double) totalPaidAmount));
    }

    private boolean verified(BilibiliLiveSessionUserView user) {
        return "VERIFIED_UID".equals(user.identityQuality()) && user.userUid() != null && user.userUid() > 0;
    }

    private double round(double value) {
        return Math.round(value * 1_000.0) / 1_000.0;
    }
}
