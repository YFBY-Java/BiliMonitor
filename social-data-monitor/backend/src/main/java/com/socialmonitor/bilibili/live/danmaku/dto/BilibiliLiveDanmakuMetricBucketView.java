package com.socialmonitor.bilibili.live.danmaku.dto;

import java.time.OffsetDateTime;

public record BilibiliLiveDanmakuMetricBucketView(
        OffsetDateTime bucketStart,
        Integer bucketSeconds,
        Integer danmuCount,
        Long likeCount,
        Long likeIncrement,
        Long watchedCount,
        Long heartbeatPopularity,
        Integer rawEventCount
) {
}
