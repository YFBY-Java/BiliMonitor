package com.socialmonitor.bilibili.live.danmaku.domain;

import java.time.OffsetDateTime;

public record BilibiliLiveDanmakuSession(
        Long id,
        Long liveRoomMonitorId,
        Long roomId,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        String status,
        String connectHost,
        Integer reconnectCount,
        OffsetDateTime lastHeartbeatAt,
        OffsetDateTime lastErrorAt,
        String lastErrorType,
        String lastErrorMessage,
        OffsetDateTime createdAt
) {
}
