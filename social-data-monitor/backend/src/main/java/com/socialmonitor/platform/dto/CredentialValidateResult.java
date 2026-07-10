package com.socialmonitor.platform.dto;

import com.socialmonitor.platform.enums.RiskLevel;

public record CredentialValidateResult(
        boolean valid,
        String message,
        RiskLevel riskLevel
) {
    public static CredentialValidateResult valid(String message) {
        return new CredentialValidateResult(true, message, RiskLevel.LOW);
    }

    public static CredentialValidateResult invalid(String message, RiskLevel riskLevel) {
        return new CredentialValidateResult(false, message, riskLevel);
    }
}

