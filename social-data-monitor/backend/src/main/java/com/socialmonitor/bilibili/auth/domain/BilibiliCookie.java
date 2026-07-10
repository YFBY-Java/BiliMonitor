package com.socialmonitor.bilibili.auth.domain;

import java.time.OffsetDateTime;

public record BilibiliCookie(
        String name,
        String value,
        String domain,
        String path,
        OffsetDateTime expiresAt,
        boolean httpOnly,
        boolean secure,
        String sameSite
) {
}
