package com.socialmonitor.collector.controller;

import com.socialmonitor.collector.dto.CollectTaskCommand;
import com.socialmonitor.collector.dto.CollectTaskExecutionView;
import com.socialmonitor.collector.service.CollectTaskService;
import com.socialmonitor.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/collect/tasks")
public class CollectTaskController {

    private final CollectTaskService collectTaskService;

    public CollectTaskController(CollectTaskService collectTaskService) {
        this.collectTaskService = collectTaskService;
    }

    @PostMapping("/run-once")
    public ApiResponse<CollectTaskExecutionView> runOnce(@Valid @RequestBody CollectTaskCommand command) {
        return ApiResponse.ok(collectTaskService.runOnce(command));
    }
}

