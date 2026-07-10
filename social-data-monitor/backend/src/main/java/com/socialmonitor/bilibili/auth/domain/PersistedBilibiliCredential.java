package com.socialmonitor.bilibili.auth.domain;

import java.time.OffsetDateTime;

public record PersistedBilibiliCredential(
        Long credentialId,
        Long platformId,
        String status,
        BilibiliCookieState state,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
