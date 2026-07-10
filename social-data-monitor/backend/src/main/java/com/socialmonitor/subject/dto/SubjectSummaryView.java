package com.socialmonitor.subject.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SubjectSummaryView(
        Long followerCount,
        Long followerDelta24h,
        Integer liveStatus,
        Long onlineCount,
        Long onlineDelta24h,
        Long onlinePeak24h,
        Integer danmuPerMinute,
        Integer danmuLast5Minutes,
        BigDecimal healthScore,
        Integer enabledModuleCount,
        Integer totalModuleCount,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime nextCollectAt
) {
}
