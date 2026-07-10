package com.socialmonitor.bilibili.live.rank.domain;

import java.time.OffsetDateTime;

public record BilibiliLiveRankSnapshot(
        Long id,
        Long monitorId,
        Long roomId,
        Long ruid,
        String rankFamily,
        String rankType,
        String rankSwitch,
        String periodScope,
        Integer pageNo,
        Integer pageSize,
        Long totalCount,
        String countText,
        String valueText,
        String remindMsg,
        String sourceEndpoint,
        Boolean signedRequired,
        OffsetDateTime capturedAt,
        OffsetDateTime capturedBucket
) {
}
