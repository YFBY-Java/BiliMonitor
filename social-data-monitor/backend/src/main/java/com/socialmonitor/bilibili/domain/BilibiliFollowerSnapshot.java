package com.socialmonitor.bilibili.domain;

import java.time.OffsetDateTime;

public record BilibiliFollowerSnapshot(
        Long id,
        Long monitoredUserId,
        Long mid,
        Long followerCount,
        Long followingCount,
        OffsetDateTime capturedAt,
        OffsetDateTime capturedBucket,
        String sourceEndpoint
) {
}
