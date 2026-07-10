package com.socialmonitor.platform.dto;

import java.time.OffsetDateTime;

public record RateLimitInfo(
        boolean limited,
        Integer remaining,
        OffsetDateTime resetAt,
        String policyKey
) {
    public static RateLimitInfo notLimited(String policyKey) {
        return new RateLimitInfo(false, null, null, policyKey);
    }
}

