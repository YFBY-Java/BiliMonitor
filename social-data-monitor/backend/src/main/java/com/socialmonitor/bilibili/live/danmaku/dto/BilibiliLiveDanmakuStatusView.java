package com.socialmonitor.bilibili.live.danmaku.dto;

import java.time.OffsetDateTime;

public record BilibiliLiveDanmakuStatusView(
        Long liveRoomMonitorId,
        Long roomId,
        boolean running,
        String status,
        String connectHost,
        Long sessionId,
        Integer ratePerMinute,
        Integer last5MinutesCount,
        Long likeCount,
        Long likeIncrement,
        Long watchedCount,
        Long heartbeatPopularity,
        Integer protocolVersion,
        String authMode,
        Long authUid,
        OffsetDateTime startedAt,
        OffsetDateTime lastHeartbeatAt,
        OffsetDateTime lastErrorAt,
        String lastErrorType,
        String lastErrorMessage
) {
}
