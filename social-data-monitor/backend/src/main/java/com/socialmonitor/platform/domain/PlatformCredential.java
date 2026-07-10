package com.socialmonitor.platform.domain;

import java.util.Map;

public record PlatformCredential(
        String platformCode,
        String authType,
        Map<String, Object> secretPayload
) {
}

