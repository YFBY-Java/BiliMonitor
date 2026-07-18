package com.socialmonitor.douyin.auth.domain;

import java.time.OffsetDateTime;
import java.util.Map;

public record DouyinStoredCredential(
        Long credentialId,
        Long platformId,
        String authType,
        String status,
        Map<String, Object> payload,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
