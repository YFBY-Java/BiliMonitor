package com.socialmonitor.bilibili.live.rank.dto;

import java.util.List;

public record BilibiliLiveRankRefreshResultView(
        Long roomMonitorId,
        int successCount,
        List<String> errors,
        BilibiliLiveRankSummaryView summary
) {
}
