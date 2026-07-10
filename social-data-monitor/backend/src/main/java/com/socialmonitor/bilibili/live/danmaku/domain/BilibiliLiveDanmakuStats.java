package com.socialmonitor.bilibili.live.danmaku.domain;

import java.time.OffsetDateTime;

public record BilibiliLiveDanmakuStats(
        Integer ratePerMinute,
        Integer last5MinutesCount,
        Long likeCount,
        Long likeIncrement,
        Long watchedCount,
        Long heartbeatPopularity,
        OffsetDateTime latestBucketAt,
        OffsetDateTime latestMessageAt
) {
}
