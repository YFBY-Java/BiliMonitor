package com.socialmonitor.ai.dto;

public record AiAnalysisRequest(
        String capability,
        String targetType,
        String targetId,
        String text
) {
}

