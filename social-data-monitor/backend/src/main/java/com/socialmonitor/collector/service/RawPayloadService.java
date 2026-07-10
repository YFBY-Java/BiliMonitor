package com.socialmonitor.collector.service;

import com.socialmonitor.collector.dto.CollectTaskCommand;
import com.socialmonitor.platform.dto.FetchResult;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RawPayloadService {

    public void storeIfPresent(CollectTaskCommand command, FetchResult<Map<String, Object>> result) {
        // Reserved for raw_payload persistence before normalization.
    }
}

