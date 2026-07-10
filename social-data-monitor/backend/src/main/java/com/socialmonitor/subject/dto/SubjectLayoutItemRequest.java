package com.socialmonitor.subject.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record SubjectLayoutItemRequest(
        @NotBlank String widgetKey,
        Boolean enabled,
        Map<String, Object> position,
        Map<String, Object> settings
) {
}
