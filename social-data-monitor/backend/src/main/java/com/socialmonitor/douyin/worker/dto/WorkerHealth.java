package com.socialmonitor.douyin.worker.dto;

import java.util.Map;

public record WorkerHealth(String status, Map<String, Object> rawResult) {
}
