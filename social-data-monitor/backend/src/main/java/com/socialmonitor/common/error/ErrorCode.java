package com.socialmonitor.common.error;

public enum ErrorCode {
    OK("OK", "success"),
    BAD_REQUEST("BAD_REQUEST", "Bad request"),
    NOT_FOUND("NOT_FOUND", "Resource not found"),
    VALIDATION_FAILED("VALIDATION_FAILED", "Validation failed"),
    BUSINESS_ERROR("BUSINESS_ERROR", "Business error"),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}

