package com.socialmonitor.bilibili.live.session.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BilibiliLiveSessionInsightView(
        Long sessionId,
        int bucketSeconds,
        Kpis kpis,
        List<TimeBucket> timeline,
        List<Peak> peaks,
        List<UserSegment> userSegments,
        List<GiftMix> giftMix,
        List<Finding> findings,
        DanmakuDepth danmakuDepth,
        PaymentDepth paymentDepth,
        Quality quality
) {
    public record Kpis(
            Double danmakuPerMinute,
            Double payerConversionRate,
            Long arppuMilliYuan,
            Long paidAmountMilliYuan,
            Double topFiveRevenueShare
    ) {
    }

    public record TimeBucket(
            OffsetDateTime bucketStart,
            long danmakuCount,
            long paidEventCount,
            long paidAmountMilliYuan,
            long activeUserCount
    ) {
    }

    public record Peak(
            String type,
            OffsetDateTime bucketStart,
            long value,
            String label
    ) {
    }

    public record UserSegment(
            String code,
            String label,
            long userCount,
            String description
    ) {
    }

    public record GiftMix(
            String giftName,
            String eventKind,
            long giftCount,
            long paidAmountMilliYuan,
            double revenueShare
    ) {
    }

    public record Finding(
            String code,
            String level,
            String title,
            String description
    ) {
    }

    public record DanmakuDepth(
            Long identifiedDanmakuUserCount,
            Long identifiedDanmakuCount,
            Double messagesPerActiveUser,
            Double repeatInteractionRate,
            Double sustainedParticipationRate,
            Double duplicateMessageRate,
            List<DanmakuStage> stages,
            List<RepeatedMessage> repeatedMessages
    ) {
    }

    public record DanmakuStage(
            String code,
            String label,
            long danmakuCount,
            long activeUserCount,
            double messageShare
    ) {
    }

    public record RepeatedMessage(
            String messageText,
            long messageCount,
            long userCount
    ) {
    }

    public record PaymentDepth(
            Long payerCount,
            Double repeatPayerRate,
            Double engagedPayerRate,
            Double returningPayerRate,
            Long medianPayerAmountMilliYuan,
            Long medianConversionLagSeconds,
            Double topOneRevenueShare,
            List<SpendTier> spendTiers
    ) {
    }

    public record SpendTier(
            String code,
            String label,
            long userCount,
            long paidAmountMilliYuan,
            double revenueShare
    ) {
    }

    public record Quality(
            String coverageStatus,
            long coveredSeconds,
            Double identityResolvedEventShare,
            long supportedEventCount,
            long unresolvedInteractionEventCount,
            Long eventLatencyP95Millis,
            String caveat
    ) {
    }
}
