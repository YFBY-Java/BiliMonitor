package com.socialmonitor.subject.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SubjectDanmuView(
        Boolean enabled,
        String status,
        Integer ratePerMinute,
        Integer last5MinutesCount,
        Long likeIncrement,
        Long watchedCount,
        OffsetDateTime lastMessageAt,
        List<SubjectDanmuRecentMessageView> recentMessages
) {
}
