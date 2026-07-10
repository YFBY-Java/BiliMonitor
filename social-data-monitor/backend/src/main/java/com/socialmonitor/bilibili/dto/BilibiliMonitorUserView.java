package com.socialmonitor.bilibili.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BilibiliMonitorUserView(
        Long id,
        Long mid,
        String nickname,
        String avatarUrl,
        String profileUrl,
        Long currentFollowerCount,
        Long followingCount,
        Long deltaSincePrevious,
        Double growthRateSincePrevious,
        OffsetDateTime lastSnapshotAt,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime nextCollectAt,
        String monitorStatus,
        Integer intervalSeconds,
        String lastErrorType,
        String lastErrorMessage,
        OffsetDateTime lastErrorAt,
        String sourceEndpoint,
        List<BilibiliFollowerPointView> recentTrend
) {
}
