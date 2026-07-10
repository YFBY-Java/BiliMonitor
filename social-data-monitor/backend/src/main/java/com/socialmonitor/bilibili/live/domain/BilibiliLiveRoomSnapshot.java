package com.socialmonitor.bilibili.live.domain;

import java.time.OffsetDateTime;

public record BilibiliLiveRoomSnapshot(
        Long id,
        Long monitorId,
        Long uid,
        Long roomId,
        Integer liveStatus,
        String title,
        Long areaId,
        String areaName,
        Long parentAreaId,
        String parentAreaName,
        Long onlineCount,
        Long attentionCount,
        OffsetDateTime liveTime,
        String sourceEndpoint,
        OffsetDateTime capturedAt,
        OffsetDateTime capturedBucket
) {
}
