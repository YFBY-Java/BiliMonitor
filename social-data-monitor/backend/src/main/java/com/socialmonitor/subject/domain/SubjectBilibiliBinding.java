package com.socialmonitor.subject.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record SubjectBilibiliBinding(
        Long id,
        Long subjectId,
        Long bilibiliUserMonitorId,
        Long bilibiliLiveRoomMonitorId,
        Long mid,
        Long roomId,
        List<String> enabledCapabilities,
        Boolean danmuEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
