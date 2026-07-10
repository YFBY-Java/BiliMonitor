package com.socialmonitor.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AiAnalysisResult(
        String provider,
        String model,
        String summary,
        String sentiment,
        List<String> labels,
        OffsetDateTime generatedAt
) {
}

