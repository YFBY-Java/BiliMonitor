package com.socialmonitor.bilibili.live.session.controller;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionUserView;
import com.socialmonitor.bilibili.live.session.query.BilibiliLiveSessionQueryService;
import com.socialmonitor.common.response.ApiResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bilibili/live-monitor")
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSessionController {

    private final BilibiliLiveSessionQueryService queryService;

    public BilibiliLiveSessionController(BilibiliLiveSessionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/rooms/{monitorId}/sessions")
    public ApiResponse<List<BilibiliLiveSessionSummaryView>> sessions(
            @PathVariable Long monitorId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(queryService.sessions(monitorId, limit));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<BilibiliLiveSessionSummaryView> session(@PathVariable Long sessionId) {
        return ApiResponse.ok(queryService.session(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/users")
    public ApiResponse<List<BilibiliLiveSessionUserView>> users(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.ok(queryService.users(sessionId, limit));
    }
}
