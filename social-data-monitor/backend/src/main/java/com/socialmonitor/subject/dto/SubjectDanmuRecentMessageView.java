package com.socialmonitor.subject.dto;

import java.time.OffsetDateTime;

public record SubjectDanmuRecentMessageView(
        String displayName,
        String messageText,
        String medalName,
        OffsetDateTime sentAt
) {
}
