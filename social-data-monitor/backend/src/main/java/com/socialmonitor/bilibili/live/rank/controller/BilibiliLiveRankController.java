package com.socialmonitor.bilibili.live.rank.controller;

import com.socialmonitor.bilibili.live.rank.dto.BilibiliLiveRankRefreshRequest;
import com.socialmonitor.bilibili.live.rank.dto.BilibiliLiveRankRefreshResultView;
import com.socialmonitor.bilibili.live.rank.dto.BilibiliLiveRankSnapshotView;
import com.socialmonitor.bilibili.live.rank.dto.BilibiliLiveRankSummaryView;
import com.socialmonitor.bilibili.live.rank.service.BilibiliLiveRankService;
import com.socialmonitor.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bilibili/live-monitor/rooms/{roomMonitorId}/ranks")
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = {"storage-enabled", "rank.enabled"}, matchIfMissing = true)
public class BilibiliLiveRankController {

    private final BilibiliLiveRankService rankService;

    public BilibiliLiveRankController(BilibiliLiveRankService rankService) {
        this.rankService = rankService;
    }

    @GetMapping("/summary")
    public ApiResponse<BilibiliLiveRankSummaryView> summary(@PathVariable Long roomMonitorId) {
        return ApiResponse.ok(rankService.summary(roomMonitorId));
    }

    @GetMapping("/latest")
    public ApiResponse<BilibiliLiveRankSnapshotView> latest(
            @PathVariable Long roomMonitorId,
            @RequestParam(required = false) String family,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String rankSwitch,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ApiResponse.ok(rankService.latest(roomMonitorId, family, type, rankSwitch, limit));
    }

    @PostMapping("/refresh")
    public ApiResponse<BilibiliLiveRankRefreshResultView> refresh(
            @PathVariable Long roomMonitorId,
            @Valid @RequestBody(required = false) BilibiliLiveRankRefreshRequest request
    ) {
        return ApiResponse.ok(rankService.refresh(roomMonitorId, request));
    }
}
