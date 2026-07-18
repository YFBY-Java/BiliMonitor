package com.socialmonitor.douyin.auth.dto;

import java.util.UUID;

public record DouyinOAuthStartView(
        UUID loginId,
        String mode,
        String authorizationUrl,
        String state,
        int expiresInSeconds
) {
}
