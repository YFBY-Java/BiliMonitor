package com.socialmonitor.bilibili.live.session.export;

import java.time.OffsetDateTime;

public record BilibiliLiveSessionGiftExportRow(
        OffsetDateTime occurredAt,
        OffsetDateTime receivedAt,
        String eventKind,
        Long senderUid,
        String senderName,
        String medalName,
        String messageText,
        Long giftId,
        String giftName,
        Long giftCount,
        String coinType,
        Long unitPriceMilliYuan,
        Long paidAmountMilliYuan,
        Boolean paid,
        Integer guardLevel,
        String amountSource,
        String command,
        String protocolVersion,
        String sourceEventId,
        String eventKey,
        Long transportSessionId
) {
}
