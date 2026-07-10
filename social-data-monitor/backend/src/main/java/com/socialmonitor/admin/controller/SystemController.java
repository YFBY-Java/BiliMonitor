package com.socialmonitor.admin.controller;

import com.socialmonitor.admin.dto.SystemOverview;
import com.socialmonitor.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev")
public class SystemController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.ok("Social Data Monitor backend is running");
    }

    @GetMapping("/overview")
    public ApiResponse<SystemOverview> overview() {
        return ApiResponse.ok(new SystemOverview(
                1,
                0,
                0,
                0,
                List.of(
                        new SystemOverview.TrendPoint("Mon", 1200),
                        new SystemOverview.TrendPoint("Tue", 1280),
                        new SystemOverview.TrendPoint("Wed", 1330),
                        new SystemOverview.TrendPoint("Thu", 1410),
                        new SystemOverview.TrendPoint("Fri", 1520)
                )
        ));
    }
}

