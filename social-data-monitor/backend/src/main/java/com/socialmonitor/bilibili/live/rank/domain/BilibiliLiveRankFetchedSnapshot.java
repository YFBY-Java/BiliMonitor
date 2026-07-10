package com.socialmonitor.bilibili.live.rank.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record BilibiliLiveRankFetchedSnapshot(
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
        String rawPayload,
        List<BilibiliLiveRankFetchedEntry> entries
) {
}
