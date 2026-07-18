package com.socialmonitor.douyin.auth.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record DouyinAuthSession(
        UUID loginId,
        String flowType,
        String providerMode,
        String workerSessionId,
        String state,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime completedAt,
        String errorCode,
        String errorMessage,
        Map<String, Object> rawResult,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
