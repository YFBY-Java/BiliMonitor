package com.socialmonitor.collector.service;

import com.socialmonitor.collector.dto.CollectTaskCommand;
import org.springframework.stereotype.Service;

@Service
public class TaskCheckpointService {

    public void saveCursor(CollectTaskCommand command, String nextCursor) {
        // Reserved for task_checkpoint persistence.
    }
}

