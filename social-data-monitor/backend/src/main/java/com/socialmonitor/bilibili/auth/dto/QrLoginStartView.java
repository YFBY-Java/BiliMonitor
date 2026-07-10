package com.socialmonitor.bilibili.auth.dto;

public record QrLoginStartView(
        String loginId,
        String qrUrl,
        int expiresInSeconds,
        int pollIntervalMillis
) {
}
