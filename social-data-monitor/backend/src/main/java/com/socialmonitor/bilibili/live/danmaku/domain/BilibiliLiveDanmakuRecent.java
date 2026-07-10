package com.socialmonitor.bilibili.live.danmaku.domain;

import java.time.OffsetDateTime;

public record BilibiliLiveDanmakuRecent(
        Long id,
        Long liveRoomMonitorId,
        Long roomId,
        String messageText,
        String displayName,
        String medalName,
        OffsetDateTime sentAt,
        OffsetDateTime createdAt
) {
}
