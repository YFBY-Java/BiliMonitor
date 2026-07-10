package com.socialmonitor.bilibili.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddBilibiliMonitorUserRequest(
        @NotNull @Min(1) Long mid,
        @Min(1) Integer intervalSeconds
) {
}
