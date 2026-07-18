package com.socialmonitor.douyin.worker.dto;

import java.util.Map;

public record WorkerStatus(String status, String message, Map<String, Object> rawResult) {
}
