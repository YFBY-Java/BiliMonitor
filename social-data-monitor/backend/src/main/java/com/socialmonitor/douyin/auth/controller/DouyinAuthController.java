package com.socialmonitor.douyin.auth.controller;

import com.socialmonitor.common.response.ApiResponse;
import com.socialmonitor.douyin.auth.domain.DouyinAuthConstants;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.dto.DouyinAuthStatusView;
import com.socialmonitor.douyin.auth.dto.DouyinCredentialFullView;
import com.socialmonitor.douyin.auth.dto.DouyinOAuthStartView;
import com.socialmonitor.douyin.auth.dto.DouyinQrStartView;
import com.socialmonitor.douyin.auth.dto.DouyinQrStatusView;
import com.socialmonitor.douyin.auth.dto.DouyinValidationView;
import com.socialmonitor.douyin.auth.service.DouyinCredentialService;
import com.socialmonitor.douyin.auth.service.DouyinOAuthService;
import com.socialmonitor.douyin.auth.service.DouyinWebAuthService;
import com.socialmonitor.douyin.worker.dto.WorkerQrImage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final DouyinWebAuthService webAuthService;
    private final DouyinCredentialService credentialService;

    public DouyinAuthController(
            DouyinOAuthService oauthService,
            DouyinWebAuthService webAuthService,
            DouyinCredentialService credentialService
    ) {
        this.oauthService = oauthService;
        this.webAuthService = webAuthService;
        this.credentialService = credentialService;
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

    @PostMapping("/web/qr/start")
    public ApiResponse<DouyinQrStartView> startWebQr() {
        return ApiResponse.ok(webAuthService.start());
    }

    @GetMapping("/web/qr/{loginId}/image")
    public ResponseEntity<byte[]> webQrImage(@PathVariable UUID loginId) {
        WorkerQrImage image = webAuthService.qr(loginId);
        return ResponseEntity.ok()
                .contentType(mediaType(image.contentType(), MediaType.IMAGE_PNG))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(image.bytes());
    }

    @GetMapping("/web/qr/{loginId}/status")
    public ApiResponse<DouyinQrStatusView> webQrStatus(@PathVariable UUID loginId) {
        return ApiResponse.ok(webAuthService.poll(loginId));
    }

    @PostMapping("/web/validate")
    public ApiResponse<DouyinValidationView> validateWebCredential() {
        return ApiResponse.ok(webAuthService.validateCurrent());
    }

    @GetMapping("/status")
    public ApiResponse<DouyinAuthStatusView> status() {
        return ApiResponse.ok(credentialService.status(webAuthService.workerHealth()));
    }

    @GetMapping("/credentials/oauth")
    public ApiResponse<DouyinCredentialFullView> oauthCredential() {
        return ApiResponse.ok(credentialService.current(DouyinAuthConstants.OAUTH_AUTH_TYPE));
    }

    @GetMapping("/credentials/web")
    public ApiResponse<DouyinCredentialFullView> webCredential() {
        return ApiResponse.ok(credentialService.current(DouyinAuthConstants.WEB_AUTH_TYPE));
    }

    @GetMapping(value = "/credentials/oauth/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DouyinCredentialFullView> exportOAuthCredential() {
        return downloadable(
                credentialService.current(DouyinAuthConstants.OAUTH_AUTH_TYPE),
                "douyin-oauth-credential.json"
        );
    }

    @GetMapping(value = "/credentials/web/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DouyinCredentialFullView> exportWebCredential() {
        return downloadable(
                credentialService.current(DouyinAuthConstants.WEB_AUTH_TYPE),
                "douyin-web-credential.json"
        );
    }

    @DeleteMapping("/credentials/oauth")
    public ApiResponse<Void> revokeOAuthCredential() {
        credentialService.revoke(DouyinAuthConstants.OAUTH_AUTH_TYPE);
        return ApiResponse.ok();
    }

    @DeleteMapping("/credentials/web")
    public ApiResponse<Void> revokeWebCredential() {
        credentialService.revoke(DouyinAuthConstants.WEB_AUTH_TYPE);
        return ApiResponse.ok();
    }

    private ResponseEntity<DouyinCredentialFullView> downloadable(
            DouyinCredentialFullView credential,
            String filename
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(credential);
    }

    private MediaType mediaType(String value, MediaType fallback) {
        try {
            return value == null || value.isBlank() ? fallback : MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static final class LinkedMultiValueMapCopy extends java.util.LinkedHashMap<String, List<String>> {

        private LinkedMultiValueMapCopy(MultiValueMap<String, String> source) {
            source.forEach((key, value) -> put(key, List.copyOf(value)));
        }
    }
}
