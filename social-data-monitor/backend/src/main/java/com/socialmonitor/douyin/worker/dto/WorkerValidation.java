package com.socialmonitor.douyin.worker.dto;

import java.util.Map;

public record WorkerValidation(
        boolean valid,
        String message,
        Map<String, Object> bundle,
        Map<String, Object> rawResult
) {
}
