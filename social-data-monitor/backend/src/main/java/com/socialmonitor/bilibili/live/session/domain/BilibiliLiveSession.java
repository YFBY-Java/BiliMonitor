package com.socialmonitor.bilibili.live.session.domain;

import java.time.OffsetDateTime;

public record BilibiliLiveSession(
        Long id,
        Long monitorId,
        Long uid,
        Long roomId,
        String state,
        OffsetDateTime platformLiveTime,
        String liveKey,
        OffsetDateTime startedAt,
        OffsetDateTime startDetectedAt,
        String startSource,
        OffsetDateTime endSignalAt,
        OffsetDateTime endedAt,
        OffsetDateTime endDetectedAt,
        String endSource,
        OffsetDateTime lastLiveObservedAt,
        OffsetDateTime lastObservedAt,
        String titleAtStart,
        String titleAtEnd,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
