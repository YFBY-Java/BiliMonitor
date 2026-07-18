package com.socialmonitor.douyin.worker.dto;

import java.util.Map;

public record WorkerConsume(Map<String, Object> bundle, Map<String, Object> rawResult) {

    public WorkerConsume(Map<String, Object> bundle) {
        this(bundle, Map.of("bundle", bundle));
    }
}
