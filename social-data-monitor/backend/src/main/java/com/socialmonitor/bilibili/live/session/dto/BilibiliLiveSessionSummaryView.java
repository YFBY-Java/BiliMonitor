package com.socialmonitor.bilibili.live.session.dto;

import java.time.OffsetDateTime;

public record BilibiliLiveSessionSummaryView(
        Long id,
        Long monitorId,
        Long uid,
        Long roomId,
        String state,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String startSource,
        String endSource,
        String coverageStatus,
        long transportSessionCount,
        OffsetDateTime captureStartedAt,
        OffsetDateTime captureEndedAt,
        Long danmakuCount,
        Long giftEventCount,
        Long giftCount,
        Long freeGiftCount,
        Long giftSenderCount,
        Long paidUserCount,
        Long interactingUserCount,
        Long unresolvedInteractingEventCount,
        Long unresolvedGiftEventCount,
        Long unresolvedPaidEventCount,
        Long paidEventCount,
        Long paidAmountMilliYuan,
        OffsetDateTime firstEventAt,
        OffsetDateTime lastEventAt
) {
}
