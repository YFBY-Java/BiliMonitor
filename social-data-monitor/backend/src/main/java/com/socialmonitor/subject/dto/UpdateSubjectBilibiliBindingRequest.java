package com.socialmonitor.subject.dto;

import jakarta.validation.constraints.Min;
import java.util.List;

public record UpdateSubjectBilibiliBindingRequest(
        @Min(1) Long bilibiliUserMonitorId,
        @Min(1) Long bilibiliLiveRoomMonitorId,
        @Min(1) Long mid,
        @Min(1) Long roomId,
        List<String> enabledCapabilities,
        Boolean danmuEnabled
) {
}
