package com.socialmonitor.bilibili.auth.dto;

import java.time.OffsetDateTime;

public record BilibiliAuthStatusView(
        boolean loggedIn,
        Long credentialId,
        BilibiliAccountView account,
        OffsetDateTime lastValidatedAt,
        OffsetDateTime lastRefreshCheckedAt,
        OffsetDateTime expiresAt,
        String status,
        BilibiliCredentialFullView credential
) {
}
