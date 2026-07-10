package com.socialmonitor.subject.dto;

import java.util.Map;

public record SubjectWidgetLayoutView(
        String widgetKey,
        Boolean enabled,
        Map<String, Object> position,
        Map<String, Object> settings
) {
}
