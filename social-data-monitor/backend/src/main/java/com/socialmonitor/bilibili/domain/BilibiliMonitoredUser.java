package com.socialmonitor.bilibili.domain;

import java.time.OffsetDateTime;

public record BilibiliMonitoredUser(
        Long id,
        Long mid,
        String nickname,
        String avatarUrl,
        String profileUrl,
        Long currentFollowerCount,
        Long followingCount,
        String monitorStatus,
        Integer intervalSeconds,
        OffsetDateTime nextCollectAt,
        OffsetDateTime lastSnapshotAt,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime lastErrorAt,
        String lastErrorType,
        String lastErrorMessage,
        String sourceEndpoint,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
