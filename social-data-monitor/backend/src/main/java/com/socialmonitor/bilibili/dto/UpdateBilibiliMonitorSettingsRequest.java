package com.socialmonitor.bilibili.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateBilibiliMonitorSettingsRequest(
        @NotNull @Min(1) Integer intervalSeconds
) {
}

