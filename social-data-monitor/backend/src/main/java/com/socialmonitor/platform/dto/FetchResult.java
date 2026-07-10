package com.socialmonitor.platform.dto;

import com.socialmonitor.platform.enums.FetchErrorType;
import com.socialmonitor.platform.enums.RiskLevel;

public record FetchResult<T>(
        boolean success,
        T data,
        String rawPayload,
        String nextCursor,
        RateLimitInfo rateLimitInfo,
        FetchErrorType errorType,
        boolean retryable,
        RiskLevel riskLevel,
        String message
) {
    public static <T> FetchResult<T> success(T data, String rawPayload) {
        return new FetchResult<>(
                true,
                data,
                rawPayload,
                null,
                RateLimitInfo.notLimited("default"),
                FetchErrorType.NONE,
                false,
                RiskLevel.LOW,
                "ok"
        );
    }

    public static <T> FetchResult<T> unsupported(String message) {
        return new FetchResult<>(
                false,
                null,
                null,
                null,
                RateLimitInfo.notLimited("unsupported"),
                FetchErrorType.UNSUPPORTED,
                false,
                RiskLevel.LOW,
                message
        );
    }

    public static <T> FetchResult<T> failure(
            FetchErrorType errorType,
            boolean retryable,
            RiskLevel riskLevel,
            String message,
            String rawPayload
    ) {
        return new FetchResult<>(
                false,
                null,
                rawPayload,
                null,
                RateLimitInfo.notLimited("failure"),
                errorType,
                retryable,
                riskLevel,
                message
        );
    }
}
