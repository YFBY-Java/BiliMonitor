package com.socialmonitor.douyin.auth.dto;

import java.util.Map;
import java.util.UUID;

public record DouyinQrStatusView(
        UUID loginId,
        String status,
        String message,
        int expiresInSeconds,
        Map<String, Object> rawResult,
        DouyinCredentialFullView credential
) {
}
