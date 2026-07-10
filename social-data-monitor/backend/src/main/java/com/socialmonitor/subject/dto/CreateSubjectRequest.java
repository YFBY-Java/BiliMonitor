package com.socialmonitor.subject.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateSubjectRequest(
        @Size(max = 160) String displayName,
        String avatarUrl,
        String remark,
        List<String> tags,
        @Min(1) Long bilibiliUserMonitorId,
        @Min(1) Long bilibiliLiveRoomMonitorId,
        @Min(1) Long mid,
        @Min(1) Long roomId,
        Boolean danmuEnabled
) {
}
