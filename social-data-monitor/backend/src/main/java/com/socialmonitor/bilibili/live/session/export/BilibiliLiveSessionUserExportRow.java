package com.socialmonitor.bilibili.live.session.export;

import java.time.OffsetDateTime;

public record BilibiliLiveSessionUserExportRow(
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
