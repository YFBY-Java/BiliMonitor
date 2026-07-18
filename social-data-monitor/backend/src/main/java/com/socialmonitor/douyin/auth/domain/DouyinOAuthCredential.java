package com.socialmonitor.douyin.auth.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record DouyinOAuthCredential(
        String accessToken,
        String refreshToken,
        String openId,
        String unionId,
        List<String> scope,
        OffsetDateTime expiresAt,
        Map<String, Object> rawPayload
) {
}
