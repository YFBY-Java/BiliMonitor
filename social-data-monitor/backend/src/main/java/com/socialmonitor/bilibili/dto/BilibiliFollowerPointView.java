package com.socialmonitor.bilibili.dto;

import java.time.OffsetDateTime;

public record BilibiliFollowerPointView(
        OffsetDateTime capturedAt,
        Long followerCount,
        Long followingCount,
        String sourceEndpoint
) {
}
