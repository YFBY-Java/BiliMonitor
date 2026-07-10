package com.socialmonitor.bilibili.live.rank.dto;

public record BilibiliLiveRankEntryView(
        Long userUid,
        Integer rankNo,
        String entryKind,
        String displayName,
        String faceUrl,
        Long score,
        Integer guardLevel,
        Integer wealthLevel,
        String medalName,
        Integer medalLevel,
        String guardExpiredText,
        Integer accompanyDays
) {
}
