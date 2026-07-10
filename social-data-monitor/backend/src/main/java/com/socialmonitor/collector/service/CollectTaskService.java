package com.socialmonitor.collector.service;

import com.socialmonitor.collector.dto.CollectTaskCommand;
import com.socialmonitor.collector.dto.CollectTaskExecutionView;
import org.springframework.stereotype.Service;

@Service
public class CollectTaskService {

    private final CollectTaskExecutor collectTaskExecutor;

    public CollectTaskService(CollectTaskExecutor collectTaskExecutor) {
        this.collectTaskExecutor = collectTaskExecutor;
    }

    public CollectTaskExecutionView runOnce(CollectTaskCommand command) {
        return collectTaskExecutor.execute(command);
    }
}

