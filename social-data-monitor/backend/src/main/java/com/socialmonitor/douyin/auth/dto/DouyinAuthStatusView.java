package com.socialmonitor.douyin.auth.dto;

import java.util.Map;

public record DouyinAuthStatusView(
        boolean enabled,
        String oauthMode,
        boolean workerAvailable,
        String workerStatus,
        int pollIntervalMs,
        DouyinCredentialFullView oauthCredential,
        DouyinCredentialFullView webCredential,
        Map<String, Object> workerRawResult
) {
}
