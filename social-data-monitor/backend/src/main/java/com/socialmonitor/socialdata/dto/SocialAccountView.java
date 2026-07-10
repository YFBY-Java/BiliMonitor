package com.socialmonitor.socialdata.dto;

public record SocialAccountView(
        String platformCode,
        String externalId,
        String displayName,
        Long followerCount
) {
}

