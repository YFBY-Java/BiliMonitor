package com.socialmonitor.bilibili.live.rank.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BilibiliLiveRankSummaryView(
        Long roomMonitorId,
        Long roomId,
        Long ruid,
        Long audienceCount,
        String audienceCountText,
        Long guardCount,
        String guardCountText,
        OffsetDateTime updatedAt,
        List<BilibiliLiveRankSnapshotView> snapshots
) {
}
