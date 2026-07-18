package com.socialmonitor.douyin.auth.domain;

import java.util.List;
import java.util.Map;

public record DouyinWebSessionCredential(
        List<Map<String, Object>> cookies,
        Map<String, String> cookieHeadersByOrigin,
        Map<String, Object> storageState,
        Map<String, Object> browserContext,
        Map<String, Object> rawPayload
) {
}
