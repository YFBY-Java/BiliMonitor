package com.socialmonitor.bilibili.auth.controller;

import com.socialmonitor.bilibili.auth.dto.BilibiliAuthRefreshView;
import com.socialmonitor.bilibili.auth.dto.BilibiliAuthStatusView;
import com.socialmonitor.bilibili.auth.dto.BilibiliCredentialFullView;
import com.socialmonitor.bilibili.auth.dto.QrLoginStartView;
import com.socialmonitor.bilibili.auth.dto.QrLoginStatusView;
import com.socialmonitor.bilibili.auth.service.BilibiliAuthService;
import com.socialmonitor.common.response.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bilibili/auth")
@ConditionalOnProperty(prefix = "app.bilibili.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BilibiliAuthController {

    private final BilibiliAuthService authService;

    public BilibiliAuthController(BilibiliAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/qr/start")
    public ApiResponse<QrLoginStartView> startQrLogin() {
        return ApiResponse.ok(authService.startQrLogin());
    }

    @GetMapping("/qr/{loginId}/status")
    public ApiResponse<QrLoginStatusView> qrStatus(@PathVariable String loginId) {
        return ApiResponse.ok(authService.pollQrLogin(loginId));
    }

    @GetMapping("/status")
    public ApiResponse<BilibiliAuthStatusView> status() {
        return ApiResponse.ok(authService.currentStatus());
    }

    @PostMapping("/refresh")
    public ApiResponse<BilibiliAuthRefreshView> refresh() {
        return ApiResponse.ok(authService.refreshCurrentCredential());
    }

    @GetMapping("/credential")
    public ApiResponse<BilibiliCredentialFullView> credential() {
        return ApiResponse.ok(authService.currentCredential());
    }

    @DeleteMapping
    public ApiResponse<Void> revoke() {
        authService.revokeCurrentCredential();
        return ApiResponse.ok();
    }
}
