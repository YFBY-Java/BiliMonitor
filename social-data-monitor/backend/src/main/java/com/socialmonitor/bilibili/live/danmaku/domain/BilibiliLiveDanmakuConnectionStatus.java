package com.socialmonitor.bilibili.live.danmaku.domain;

import java.time.OffsetDateTime;

public record BilibiliLiveDanmakuConnectionStatus(
        Long liveRoomMonitorId,
        Long roomId,
        boolean running,
        String status,
        String connectHost,
        Long sessionId,
        OffsetDateTime startedAt,
        OffsetDateTime lastHeartbeatAt,
        OffsetDateTime lastErrorAt,
        String lastErrorType,
        String lastErrorMessage
) {
}
