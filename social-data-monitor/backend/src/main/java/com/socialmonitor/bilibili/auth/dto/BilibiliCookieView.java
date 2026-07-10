package com.socialmonitor.bilibili.auth.dto;

import java.time.OffsetDateTime;

public record BilibiliCookieView(
        String name,
        String value,
        String domain,
        String path,
        OffsetDateTime expiresAt,
        Boolean httpOnly,
        Boolean secure,
        String sameSite
) {
}
