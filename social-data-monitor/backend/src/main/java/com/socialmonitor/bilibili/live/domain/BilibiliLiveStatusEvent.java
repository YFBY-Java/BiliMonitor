package com.socialmonitor.bilibili.live.domain;

import java.time.OffsetDateTime;

public record BilibiliLiveStatusEvent(
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
