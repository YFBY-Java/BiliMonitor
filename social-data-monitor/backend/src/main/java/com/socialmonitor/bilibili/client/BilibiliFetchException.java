package com.socialmonitor.bilibili.client;

import com.socialmonitor.platform.enums.FetchErrorType;
import com.socialmonitor.platform.enums.RiskLevel;

public class BilibiliFetchException extends RuntimeException {

    private final FetchErrorType errorType;
    private final boolean retryable;
    private final RiskLevel riskLevel;
    private final String endpointKey;
    private final Integer statusCode;
    private final Integer biliCode;
    private final String rawPayload;

    public BilibiliFetchException(
            FetchErrorType errorType,
            boolean retryable,
            RiskLevel riskLevel,
            String endpointKey,
            Integer statusCode,
            Integer biliCode,
            String message,
            String rawPayload
    ) {
        super(message);
        this.errorType = errorType;
        this.retryable = retryable;
        this.riskLevel = riskLevel;
        this.endpointKey = endpointKey;
        this.statusCode = statusCode;
        this.biliCode = biliCode;
        this.rawPayload = rawPayload;
    }

    public FetchErrorType errorType() {
        return errorType;
    }

    public boolean retryable() {
        return retryable;
    }

    public RiskLevel riskLevel() {
        return riskLevel;
    }

    public String endpointKey() {
        return endpointKey;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public Integer biliCode() {
        return biliCode;
    }

    public String rawPayload() {
        return rawPayload;
    }
}
