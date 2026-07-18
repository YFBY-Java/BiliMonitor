package com.socialmonitor.douyin.auth.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record DouyinQrStartView(
        UUID loginId,
        String status,
        String imageUrl,
        int expiresInSeconds,
        int pollIntervalMs,
        OffsetDateTime expiresAt,
        Map<String, Object> rawResult
) {
}
