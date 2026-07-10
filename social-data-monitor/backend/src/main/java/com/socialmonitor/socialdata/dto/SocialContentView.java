package com.socialmonitor.socialdata.dto;

import java.time.OffsetDateTime;

public record SocialContentView(
        String platformCode,
        String externalId,
        String contentType,
        String title,
        OffsetDateTime publishedAt
) {
}

