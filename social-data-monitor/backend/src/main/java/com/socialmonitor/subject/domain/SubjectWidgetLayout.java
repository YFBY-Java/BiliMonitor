package com.socialmonitor.subject.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record SubjectWidgetLayout(
        Long id,
        Long subjectId,
        String widgetKey,
        Boolean enabled,
        Map<String, Object> position,
        Map<String, Object> settings,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
