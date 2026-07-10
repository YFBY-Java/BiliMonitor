package com.socialmonitor.bilibili.live.dto;

import java.time.OffsetDateTime;

public record BilibiliLiveTrendPointView(
        Long roomId,
        Long uid,
        OffsetDateTime capturedAt,
        Integer liveStatus,
        Long onlineCount,
        Long attentionCount,
        String sourceEndpoint
) {
}
