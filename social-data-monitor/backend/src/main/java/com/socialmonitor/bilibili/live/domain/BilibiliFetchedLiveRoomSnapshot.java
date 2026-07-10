package com.socialmonitor.bilibili.live.domain;

import java.time.OffsetDateTime;

public record BilibiliFetchedLiveRoomSnapshot(
        Long uid,
        Long roomId,
        Long shortId,
        String uname,
        String faceUrl,
        String title,
        String coverUrl,
        String keyframeUrl,
        Long areaId,
        String areaName,
        Long parentAreaId,
        String parentAreaName,
        Integer liveStatus,
        OffsetDateTime liveTime,
        Long onlineCount,
        Long attentionCount,
        OffsetDateTime fetchedAt,
        String sourceEndpoint,
        String rawPayload
) {

    public BilibiliFetchedLiveRoomSnapshot mergeWith(BilibiliFetchedLiveRoomSnapshot fallback) {
        if (fallback == null) {
            return this;
        }
        return new BilibiliFetchedLiveRoomSnapshot(
                first(uid, fallback.uid()),
                first(roomId, fallback.roomId()),
                first(shortId, fallback.shortId()),
                firstText(uname, fallback.uname()),
                firstText(faceUrl, fallback.faceUrl()),
                firstText(title, fallback.title()),
                firstText(coverUrl, fallback.coverUrl()),
                firstText(keyframeUrl, fallback.keyframeUrl()),
                first(areaId, fallback.areaId()),
                firstText(areaName, fallback.areaName()),
                first(parentAreaId, fallback.parentAreaId()),
                firstText(parentAreaName, fallback.parentAreaName()),
                first(liveStatus, fallback.liveStatus()),
                first(liveTime, fallback.liveTime()),
                first(onlineCount, fallback.onlineCount()),
                first(attentionCount, fallback.attentionCount()),
                fetchedAt,
                firstText(sourceEndpoint, fallback.sourceEndpoint()),
                firstText(rawPayload, fallback.rawPayload())
        );
    }

    public BilibiliFetchedLiveRoomSnapshot withExistingProfile(BilibiliLiveRoomMonitor monitor) {
        return new BilibiliFetchedLiveRoomSnapshot(
                uid,
                roomId,
                first(shortId, monitor.shortId()),
                firstText(uname, monitor.uname()),
                firstText(faceUrl, monitor.faceUrl()),
                firstText(title, monitor.title()),
                firstText(coverUrl, monitor.coverUrl()),
                firstText(keyframeUrl, monitor.keyframeUrl()),
                first(areaId, monitor.areaId()),
                firstText(areaName, monitor.areaName()),
                first(parentAreaId, monitor.parentAreaId()),
                firstText(parentAreaName, monitor.parentAreaName()),
                liveStatus,
                liveTime,
                onlineCount,
                first(attentionCount, monitor.attentionCount()),
                fetchedAt,
                sourceEndpoint,
                rawPayload
        );
    }

    private static <T> T first(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }

    private static String firstText(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }
}
