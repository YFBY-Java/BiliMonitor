package com.socialmonitor.douyin.auth.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record DouyinCredentialFullView(
        Long credentialId,
        String authType,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Map<String, Object> payload
) {
}
