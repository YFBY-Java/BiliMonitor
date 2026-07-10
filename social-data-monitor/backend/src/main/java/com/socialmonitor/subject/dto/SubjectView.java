package com.socialmonitor.subject.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record SubjectView(
        Long id,
        String displayName,
        String avatarUrl,
        String remark,
        List<String> tags,
        String monitorStatus,
        BigDecimal healthScore,
        OffsetDateTime lastSuccessAt,
        OffsetDateTime lastEventAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        SubjectBilibiliBindingView bilibiliBinding
) {
}
