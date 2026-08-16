package com.socialmonitor.bilibili.live.session.dto;

import java.time.OffsetDateTime;

public record BilibiliLiveSessionUserView(
        String actorKey,
        String identityQuality,
        Long userUid,
        String displayName,
        long danmakuCount,
        long giftEventCount,
        long giftCount,
        long freeGiftCount,
        long paidEventCount,
        long paidAmountMilliYuan,
        OffsetDateTime firstSeenAt,
        OffsetDateTime lastSeenAt
) {
}
