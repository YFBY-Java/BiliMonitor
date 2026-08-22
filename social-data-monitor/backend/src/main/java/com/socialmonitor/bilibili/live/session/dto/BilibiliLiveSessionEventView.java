package com.socialmonitor.bilibili.live.session.dto;

import java.time.OffsetDateTime;

public record BilibiliLiveSessionEventView(
        Long id,
        Long sessionId,
        String eventKind,
        String command,
        Long senderUid,
        String senderName,
        String medalName,
        String messageText,
        Long giftId,
        String giftName,
        Long giftCount,
        Boolean paid,
        Long paidAmountMilliYuan,
        Integer guardLevel,
        OffsetDateTime occurredAt,
        OffsetDateTime receivedAt
) {
}
