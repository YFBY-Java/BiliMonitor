package com.socialmonitor.bilibili.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateBilibiliMonitorStatusRequest(@NotNull Boolean enabled) {
}
