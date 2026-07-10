package com.socialmonitor.bilibili.live.dto;

import java.time.OffsetDateTime;

public record BilibiliLiveCollectResultView(
        Long roomMonitorId,
        Long uid,
        Long roomId,
        boolean success,
        Integer liveStatus,
        Long onlineCount,
        OffsetDateTime capturedAt,
        String sourceEndpoint,
        String message
) {
}
