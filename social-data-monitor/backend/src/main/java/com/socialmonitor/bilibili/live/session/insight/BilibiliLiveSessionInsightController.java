package com.socialmonitor.bilibili.live.session.insight;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionInsightView;
import com.socialmonitor.common.response.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bilibili/live-monitor/sessions")
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSessionInsightController {

    private final BilibiliLiveSessionInsightService service;

    public BilibiliLiveSessionInsightController(BilibiliLiveSessionInsightService service) {
        this.service = service;
    }

    @GetMapping("/{sessionId}/insights")
    public ApiResponse<BilibiliLiveSessionInsightView> insight(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "300") int bucketSeconds
    ) {
        return ApiResponse.ok(service.insight(sessionId, bucketSeconds));
    }
}
