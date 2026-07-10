package com.socialmonitor.bilibili.live.danmaku.domain;

import java.time.OffsetDateTime;

public record BilibiliLiveDanmakuMetricBucket(
        Long id,
        Long liveRoomMonitorId,
        Long sessionId,
        Long roomId,
        OffsetDateTime bucketStart,
        Integer bucketSeconds,
        Integer danmuCount,
        Long likeCount,
        Long likeIncrement,
        Long watchedCount,
        Long heartbeatPopularity,
        Integer giftCount,
        Integer superChatCount,
        Integer rawEventCount,
        OffsetDateTime updatedAt,
        OffsetDateTime createdAt
) {
}
