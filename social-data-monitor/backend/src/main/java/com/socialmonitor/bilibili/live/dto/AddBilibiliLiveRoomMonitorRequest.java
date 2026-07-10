package com.socialmonitor.bilibili.live.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AddBilibiliLiveRoomMonitorRequest(
        @Min(1) Long uid,
        @Min(1) Long roomId,
        @Min(1) @Max(2_592_000) Integer intervalSeconds
) {
}
