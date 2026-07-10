package com.socialmonitor.bilibili.live.dto;

import java.time.OffsetDateTime;

public record BilibiliLiveStatusEventView(
        Long id,
        Long monitorId,
        Long uid,
        Long roomId,
        String eventType,
        Integer fromLiveStatus,
        Integer toLiveStatus,
        String titleBefore,
        String titleAfter,
        Long onlineCount,
        OffsetDateTime occurredAt
) {
}
