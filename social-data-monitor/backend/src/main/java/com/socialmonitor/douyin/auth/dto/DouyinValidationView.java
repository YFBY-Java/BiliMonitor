package com.socialmonitor.douyin.auth.dto;

import java.util.Map;

public record DouyinValidationView(
        boolean valid,
        String message,
        DouyinCredentialFullView credential,
        Map<String, Object> rawResult
) {
}
