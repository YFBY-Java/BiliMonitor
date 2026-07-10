package com.socialmonitor.subject.dto;

import java.time.OffsetDateTime;

public record SubjectBilibiliUserView(
        Long monitorId,
        Long mid,
        String nickname,
        String avatarUrl,
        String profileUrl,
        Long followerCount,
        Long followingCount,
        Long followerDelta24h,
        String monitorStatus,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime nextCollectAt,
        String lastErrorType,
        String lastErrorMessage
) {
}
