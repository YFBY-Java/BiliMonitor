package com.socialmonitor.bilibili.live.session.export;

import java.time.OffsetDateTime;

public record BilibiliLiveSessionDanmakuExportRow(
        OffsetDateTime occurredAt,
        OffsetDateTime receivedAt,
        Long senderUid,
        String senderName,
        String medalName,
        String messageText,
        String command,
        String protocolVersion,
        String sourceEventId
) {
}
