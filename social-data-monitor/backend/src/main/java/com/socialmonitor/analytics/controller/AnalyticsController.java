package com.socialmonitor.analytics.controller;

import com.socialmonitor.common.response.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(Map.of(
                "message", "Analytics summary placeholder",
                "source", "metric snapshots and summaries"
        ));
    }
}

