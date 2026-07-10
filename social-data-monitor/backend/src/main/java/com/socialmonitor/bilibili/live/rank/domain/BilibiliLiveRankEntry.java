package com.socialmonitor.bilibili.live.rank.domain;

public record BilibiliLiveRankEntry(
        Long id,
        Long snapshotId,
        Long monitorId,
        Long roomId,
        Long ruid,
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
        Long medalRuid,
        Integer medalIsLight,
        String guardExpiredText,
        Integer accompanyDays,
        String rawEntryJson
) {
}
