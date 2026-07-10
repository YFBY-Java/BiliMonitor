package com.socialmonitor.bilibili.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record BilibiliCredentialFullView(
        Long credentialId,
        BilibiliAccountView account,
        String cookieHeader,
        List<BilibiliCookieView> cookies,
        String csrf,
        String refreshToken,
        OffsetDateTime expiresAt,
        Map<String, Object> rawPayload
) {
}
