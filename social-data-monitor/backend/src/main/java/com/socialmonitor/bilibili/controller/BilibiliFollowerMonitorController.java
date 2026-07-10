package com.socialmonitor.bilibili.controller;

import com.socialmonitor.bilibili.dto.AddBilibiliMonitorUserRequest;
import com.socialmonitor.bilibili.dto.BilibiliCollectResultView;
import com.socialmonitor.bilibili.dto.BilibiliMonitorUserView;
import com.socialmonitor.bilibili.dto.BilibiliUserTrendView;
import com.socialmonitor.bilibili.dto.UpdateBilibiliMonitorSettingsRequest;
import com.socialmonitor.bilibili.dto.UpdateBilibiliMonitorStatusRequest;
import com.socialmonitor.bilibili.service.BilibiliFollowerMonitorService;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bilibili/follower-monitor")
@ConditionalOnProperty(prefix = "app.bilibili.follower-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliFollowerMonitorController {

    private final BilibiliFollowerMonitorService monitorService;

    public BilibiliFollowerMonitorController(BilibiliFollowerMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/users")
    public ApiResponse<List<BilibiliMonitorUserView>> users() {
        return ApiResponse.ok(monitorService.listUsers());
    }

    @PostMapping("/users")
    public ApiResponse<BilibiliMonitorUserView> addUser(@Valid @RequestBody AddBilibiliMonitorUserRequest request) {
        return ApiResponse.ok(monitorService.addUser(request));
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<BilibiliMonitorUserView> updateStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateBilibiliMonitorStatusRequest request
    ) {
        return ApiResponse.ok(monitorService.updateStatus(userId, request.enabled()));
    }

    @RequestMapping(value = "/users/{userId}/settings", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ApiResponse<BilibiliMonitorUserView> updateSettings(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateBilibiliMonitorSettingsRequest request
    ) {
        return ApiResponse.ok(monitorService.updateInterval(userId, request.intervalSeconds()));
    }

    @PostMapping("/users/{userId}/refresh")
    public ApiResponse<BilibiliCollectResultView> refreshNow(@PathVariable Long userId) {
        return ApiResponse.ok(monitorService.refreshNow(userId));
    }

    @DeleteMapping("/users/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        monitorService.deleteUser(userId);
        return ApiResponse.ok();
    }

    @GetMapping("/users/{userId}/history")
    public ApiResponse<BilibiliUserTrendView> history(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "500") int limit
    ) {
        return ApiResponse.ok(monitorService.trend(userId, from, to, limit));
    }

    @GetMapping("/trends")
    public ApiResponse<List<BilibiliUserTrendView>> trends(
            @RequestParam(required = false) List<Long> userIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "500") int limitPerUser
    ) {
        return ApiResponse.ok(monitorService.trends(userIds, from, to, limitPerUser));
    }
}
