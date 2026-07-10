package com.socialmonitor.collector.dto;

import com.socialmonitor.collector.enums.TaskStatus;
import com.socialmonitor.platform.dto.FetchResult;
import java.util.Map;

public record CollectTaskExecutionView(
        TaskStatus status,
        String platformCode,
        String dataType,
        FetchResult<Map<String, Object>> fetchResult
) {
}

