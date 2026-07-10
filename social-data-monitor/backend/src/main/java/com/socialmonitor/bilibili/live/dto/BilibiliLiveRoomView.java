package com.socialmonitor.bilibili.live.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BilibiliLiveRoomView(
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
        Long onlineDelta,
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
        List<BilibiliLiveTrendPointView> recentTrend
) {
}
