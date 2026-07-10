package com.socialmonitor.subject.dto;

import java.time.OffsetDateTime;

public record SubjectBilibiliLiveRoomView(
        Long monitorId,
        Long uid,
        Long roomId,
        String uname,
        String faceUrl,
        String title,
        String coverUrl,
        String keyframeUrl,
        String areaName,
        String parentAreaName,
        Integer liveStatus,
        OffsetDateTime liveTime,
        Long onlineCount,
        Long onlineDelta24h,
        Long onlinePeak24h,
        String monitorStatus,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime nextCollectAt,
        String lastErrorType,
        String lastErrorMessage
) {
}
