package com.socialmonitor.subject.dto;

import java.util.List;

public record SubjectTrendView(
        Long subjectId,
        List<String> metrics,
        String range,
        String bucket,
        List<SubjectTrendPointView> points
) {
}
