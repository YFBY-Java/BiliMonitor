package com.socialmonitor.douyin.worker.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record WorkerSessionStart(
        String workerSessionId,
        String status,
        OffsetDateTime expiresAt,
        Map<String, Object> rawResult
) {
}
