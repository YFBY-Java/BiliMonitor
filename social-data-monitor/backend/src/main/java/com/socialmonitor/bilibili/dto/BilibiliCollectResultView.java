package com.socialmonitor.bilibili.dto;

import java.time.OffsetDateTime;

public record BilibiliCollectResultView(
        Long userId,
        Long mid,
        boolean success,
        Long followerCount,
        OffsetDateTime capturedAt,
        String sourceEndpoint,
        String message
) {
}
