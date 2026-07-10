package com.socialmonitor.subject.dto;

import java.time.OffsetDateTime;

public record SubjectHealthEventView(
        String eventType,
        String title,
        String description,
        String source,
        OffsetDateTime occurredAt,
        String level
) {
}
