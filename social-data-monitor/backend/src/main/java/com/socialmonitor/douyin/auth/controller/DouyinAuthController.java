package com.socialmonitor.douyin.auth.controller;

import com.socialmonitor.common.response.ApiResponse;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.dto.DouyinOAuthStartView;
import com.socialmonitor.douyin.auth.service.DouyinOAuthService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/douyin/auth")
@ConditionalOnProperty(prefix = "app.douyin.auth", name = "enabled", havingValue = "true")
public class DouyinAuthController {

    private final DouyinOAuthService oauthService;

    public DouyinAuthController(DouyinOAuthService oauthService) {
        this.oauthService = oauthService;
    }

    @PostMapping("/oauth/start")
    public ApiResponse<DouyinOAuthStartView> startOAuth() {
        return ApiResponse.ok(oauthService.start());
    }

    @GetMapping("/oauth/callback")
    public RedirectView oauthCallback(@RequestParam MultiValueMap<String, String> parameters) {
        String state = parameters.getFirst("state");
        Map<String, List<String>> rawParameters = new LinkedMultiValueMapCopy(parameters);
        oauthService.complete(state, rawParameters);
        return new RedirectView("/douyin?oauth=success");
    }

    @GetMapping("/oauth/mock/authorize")
    public RedirectView mockAuthorize(@RequestParam UUID loginId, @RequestParam String state) {
        return new RedirectView(oauthService.mockAuthorizationRedirect(loginId, state));
    }

    @PostMapping("/oauth/refresh")
    public ApiResponse<DouyinStoredCredential> refreshOAuth() {
        return ApiResponse.ok(oauthService.refresh());
    }

    private static final class LinkedMultiValueMapCopy extends java.util.LinkedHashMap<String, List<String>> {

        private LinkedMultiValueMapCopy(MultiValueMap<String, String> source) {
            source.forEach((key, value) -> put(key, List.copyOf(value)));
        }
    }
}
