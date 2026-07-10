package com.socialmonitor.ai.controller;

import com.socialmonitor.ai.dto.AiAnalysisRequest;
import com.socialmonitor.ai.dto.AiAnalysisResult;
import com.socialmonitor.ai.port.AiAnalysisPort;
import com.socialmonitor.common.response.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAnalysisPort aiAnalysisPort;

    public AiController(AiAnalysisPort aiAnalysisPort) {
        this.aiAnalysisPort = aiAnalysisPort;
    }

    @PostMapping("/mock-summary")
    public ApiResponse<AiAnalysisResult> mockSummary(@RequestBody AiAnalysisRequest request) {
        return ApiResponse.ok(aiAnalysisPort.summarizeContent(request));
    }
}

