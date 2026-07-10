package com.socialmonitor.collector.service;

import com.socialmonitor.platform.enums.FetchErrorType;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

    private static final Set<FetchErrorType> RETRYABLE = Set.of(
            FetchErrorType.RATE_LIMITED,
            FetchErrorType.NETWORK_ERROR,
            FetchErrorType.SERVER_ERROR
    );

    public boolean shouldRetry(FetchErrorType errorType, int attempt) {
        return attempt < 3 && RETRYABLE.contains(errorType);
    }
}

