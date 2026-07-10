package com.socialmonitor.bilibili.live.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateBilibiliLiveRoomMonitorRequest(
        @Min(1) @Max(2_592_000) Integer intervalSeconds,
        Boolean enabled
) {
}
