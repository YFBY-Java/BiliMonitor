package com.socialmonitor.admin.dto;

import java.util.List;

public record SystemOverview(
        int platformCount,
        int enabledTaskCount,
        int todaySuccessCount,
        int todayFailedCount,
        List<TrendPoint> followerTrend
) {
    public record TrendPoint(String date, long value) {
    }
}

