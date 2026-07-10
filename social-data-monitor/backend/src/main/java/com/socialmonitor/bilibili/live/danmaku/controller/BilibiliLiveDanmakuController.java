package com.socialmonitor.bilibili.live.danmaku.controller;

import com.socialmonitor.bilibili.live.danmaku.dto.BilibiliLiveDanmakuMetricBucketView;
import com.socialmonitor.bilibili.live.danmaku.dto.BilibiliLiveDanmakuRecentView;
import com.socialmonitor.bilibili.live.danmaku.dto.BilibiliLiveDanmakuStatusView;
import com.socialmonitor.bilibili.live.danmaku.service.BilibiliLiveDanmakuService;
import com.socialmonitor.common.response.ApiResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bilibili/live-monitor/rooms/{roomMonitorId}/danmaku")
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveDanmakuController {

    private final BilibiliLiveDanmakuService danmakuService;

    public BilibiliLiveDanmakuController(BilibiliLiveDanmakuService danmakuService) {
        this.danmakuService = danmakuService;
    }

    @PostMapping("/start")
    public ApiResponse<BilibiliLiveDanmakuStatusView> start(
            @PathVariable Long roomMonitorId,
            @RequestParam(required = false) Integer protocolVersion
    ) {
        return ApiResponse.ok(danmakuService.start(roomMonitorId, protocolVersion));
    }

    @PostMapping("/stop")
    public ApiResponse<BilibiliLiveDanmakuStatusView> stop(@PathVariable Long roomMonitorId) {
        return ApiResponse.ok(danmakuService.stop(roomMonitorId));
    }

    @GetMapping("/status")
    public ApiResponse<BilibiliLiveDanmakuStatusView> status(@PathVariable Long roomMonitorId) {
        return ApiResponse.ok(danmakuService.status(roomMonitorId));
    }

    @GetMapping("/recent")
    public ApiResponse<List<BilibiliLiveDanmakuRecentView>> recent(
            @PathVariable Long roomMonitorId,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return ApiResponse.ok(danmakuService.recent(roomMonitorId, limit));
    }

    @GetMapping("/metrics")
    public ApiResponse<List<BilibiliLiveDanmakuMetricBucketView>> metrics(
            @PathVariable Long roomMonitorId,
            @RequestParam(defaultValue = "1h") String range
    ) {
        return ApiResponse.ok(danmakuService.metrics(roomMonitorId, range));
    }
}
