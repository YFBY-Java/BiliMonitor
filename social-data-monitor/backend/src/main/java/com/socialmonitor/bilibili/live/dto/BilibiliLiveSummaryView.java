package com.socialmonitor.bilibili.live.dto;

public record BilibiliLiveSummaryView(
        int totalRooms,
        int activeRooms,
        int liveRooms,
        int roundRooms,
        int offlineRooms,
        int errorRooms,
        long totalOnlineCount,
        long todayLiveStarts,
        BilibiliLiveStatusEventView latestEvent
) {
}
