package com.socialmonitor.subject.dto;

import java.time.OffsetDateTime;

public record SubjectTrendPointView(
        OffsetDateTime bucketAt,
        Long followerCount,
        Long liveOnlineCount
) {
}
