package com.socialmonitor.bilibili.live.controller;

import com.socialmonitor.bilibili.live.dto.AddBilibiliLiveRoomMonitorRequest;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveCollectResultView;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveRoomTrendView;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveRoomView;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveStatusEventView;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveSummaryView;
import com.socialmonitor.bilibili.live.dto.UpdateBilibiliLiveRoomMonitorRequest;
import com.socialmonitor.bilibili.live.service.BilibiliLiveMonitorService;
import com.socialmonitor.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bilibili/live-monitor")
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveMonitorController {

    private final BilibiliLiveMonitorService monitorService;

    public BilibiliLiveMonitorController(BilibiliLiveMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/rooms")
    public ApiResponse<List<BilibiliLiveRoomView>> rooms() {
        return ApiResponse.ok(monitorService.listRooms());
    }

    @PostMapping("/rooms")
    public ApiResponse<BilibiliLiveRoomView> addRoom(@Valid @RequestBody AddBilibiliLiveRoomMonitorRequest request) {
        return ApiResponse.ok(monitorService.addRoom(request));
    }

    @PatchMapping("/rooms/{roomMonitorId}")
    public ApiResponse<BilibiliLiveRoomView> updateRoom(
            @PathVariable Long roomMonitorId,
            @Valid @RequestBody UpdateBilibiliLiveRoomMonitorRequest request
    ) {
        return ApiResponse.ok(monitorService.updateRoom(roomMonitorId, request));
    }

    @DeleteMapping("/rooms/{roomMonitorId}")
    public ApiResponse<Void> deleteRoom(@PathVariable Long roomMonitorId) {
        monitorService.deleteRoom(roomMonitorId);
        return ApiResponse.ok();
    }

    @PostMapping("/rooms/{roomMonitorId}/refresh")
    public ApiResponse<BilibiliLiveCollectResultView> refreshNow(@PathVariable Long roomMonitorId) {
        return ApiResponse.ok(monitorService.refreshNow(roomMonitorId));
    }

    @GetMapping("/summary")
    public ApiResponse<BilibiliLiveSummaryView> summary() {
        return ApiResponse.ok(monitorService.summary());
    }

    @GetMapping("/rooms/{roomMonitorId}/trends")
    public ApiResponse<BilibiliLiveRoomTrendView> history(
            @PathVariable Long roomMonitorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "500") int limit
    ) {
        return ApiResponse.ok(monitorService.trend(roomMonitorId, from, to, limit));
    }

    @GetMapping("/trends")
    public ApiResponse<List<BilibiliLiveRoomTrendView>> trends(
            @RequestParam(required = false) List<Long> roomIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "500") int limitPerRoom
    ) {
        return ApiResponse.ok(monitorService.trends(roomIds, from, to, limitPerRoom));
    }

    @GetMapping("/events")
    public ApiResponse<List<BilibiliLiveStatusEventView>> events(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(monitorService.events(limit));
    }
}
