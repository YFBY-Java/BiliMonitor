package com.socialmonitor.collector.service;

import com.socialmonitor.collector.dto.CollectTaskCommand;
import com.socialmonitor.platform.dto.FetchResult;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ApiCallLogService {

    public void record(CollectTaskCommand command, FetchResult<Map<String, Object>> result) {
        // Reserved for api_call_log persistence and metrics.
    }
}

