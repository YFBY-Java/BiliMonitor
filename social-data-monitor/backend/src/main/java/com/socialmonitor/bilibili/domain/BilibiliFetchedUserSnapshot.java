package com.socialmonitor.bilibili.domain;

import java.time.OffsetDateTime;

public record BilibiliFetchedUserSnapshot(
        Long mid,
        String nickname,
        String avatarUrl,
        String profileUrl,
        Long followerCount,
        Long followingCount,
        OffsetDateTime fetchedAt,
        String sourceEndpoint,
        String rawPayload
) {

    public BilibiliFetchedUserSnapshot withExistingProfile(BilibiliMonitoredUser user) {
        return new BilibiliFetchedUserSnapshot(
                mid,
                user.nickname(),
                user.avatarUrl(),
                user.profileUrl(),
                followerCount,
                followingCount,
                fetchedAt,
                sourceEndpoint,
                rawPayload
        );
    }
}
