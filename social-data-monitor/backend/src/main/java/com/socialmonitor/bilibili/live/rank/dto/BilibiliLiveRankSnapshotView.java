package com.socialmonitor.bilibili.live.rank.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BilibiliLiveRankSnapshotView(
        Long id,
        Long roomMonitorId,
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
        List<BilibiliLiveRankEntryView> entries
) {
}
