package com.socialmonitor.bilibili.live.domain;

import java.time.OffsetDateTime;

public record BilibiliLiveRoomMonitor(
        Long id,
        Long uid,
        Long roomId,
        Long shortId,
        String uname,
        String faceUrl,
        String title,
        String coverUrl,
        String keyframeUrl,
        Long areaId,
        String areaName,
        Long parentAreaId,
        String parentAreaName,
        Integer liveStatus,
        OffsetDateTime liveTime,
        Long onlineCount,
        Long attentionCount,
        String monitorStatus,
        Integer intervalSeconds,
        OffsetDateTime nextCollectAt,
        OffsetDateTime lastSnapshotAt,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime lastErrorAt,
        String lastErrorType,
        String lastErrorMessage,
        OffsetDateTime backoffUntil,
        String sourceEndpoint,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
