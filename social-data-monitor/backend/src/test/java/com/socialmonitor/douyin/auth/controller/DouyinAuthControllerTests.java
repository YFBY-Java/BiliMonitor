package com.socialmonitor.douyin.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialmonitor.douyin.auth.dto.DouyinCredentialFullView;
import com.socialmonitor.douyin.auth.dto.DouyinOAuthStartView;
import com.socialmonitor.douyin.auth.service.DouyinCredentialService;
import com.socialmonitor.douyin.auth.service.DouyinOAuthService;
import com.socialmonitor.douyin.auth.service.DouyinWebAuthService;
import com.socialmonitor.douyin.worker.dto.WorkerQrImage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SuppressWarnings("unchecked")
class DouyinAuthControllerTests {

    private final DouyinOAuthService service = mock(DouyinOAuthService.class);
    private final DouyinWebAuthService webAuthService = mock(DouyinWebAuthService.class);
    private final DouyinCredentialService credentialService = mock(DouyinCredentialService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new DouyinAuthController(service, webAuthService, credentialService)
    ).build();

    @Test
    void startsOAuthThroughTheStableApiEnvelope() throws Exception {
        UUID loginId = UUID.randomUUID();
        when(service.start()).thenReturn(new DouyinOAuthStartView(
                loginId,
                "mock",
                "/api/douyin/auth/oauth/mock/authorize?loginId=" + loginId,
                "state-value",
                180
        ));

        mvc.perform(post("/api/douyin/auth/oauth/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loginId").value(loginId.toString()))
                .andExpect(jsonPath("$.data.mode").value("mock"));
    }

    @Test
    void callbackPreservesRepeatedAndUnknownProviderParametersThenReturnsToDouyinPage() throws Exception {
        mvc.perform(get("/api/douyin/auth/oauth/callback")
                        .queryParam("code", "raw-code")
                        .queryParam("state", "state-value")
                        .queryParam("provider_extra", "one", "two"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/douyin?oauth=success"));

        ArgumentCaptor<Map<String, List<String>>> callback = ArgumentCaptor.forClass(Map.class);
        verify(service).complete(eq("state-value"), callback.capture());
        org.assertj.core.api.Assertions.assertThat(callback.getValue())
                .containsEntry("provider_extra", List.of("one", "two"));
    }

    @Test
    void mockAuthorizeRedirectsThroughTheSameCallbackContract() throws Exception {
        UUID loginId = UUID.randomUUID();
        when(service.mockAuthorizationRedirect(loginId, "state-value"))
                .thenReturn("/api/douyin/auth/oauth/callback?code=mock-code&state=state-value");

        mvc.perform(get("/api/douyin/auth/oauth/mock/authorize")
                        .queryParam("loginId", loginId.toString())
                        .queryParam("state", "state-value"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/douyin/auth/oauth/callback?code=mock-code&state=state-value"));
    }

    @Test
    void proxiesTheExactWorkerQrImageThroughSpring() throws Exception {
        UUID loginId = UUID.randomUUID();
        byte[] png = new byte[] {0x01, 0x02, (byte) 0xFF};
        when(webAuthService.qr(loginId)).thenReturn(new WorkerQrImage(png, "image/png"));

        mvc.perform(get("/api/douyin/auth/web/qr/{loginId}/image", loginId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
    }

    @Test
    void exportsTheCompleteDecryptedCredentialAsDownloadableJson() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-18T12:00:00Z");
        when(credentialService.current("DOUYIN_WEB_SESSION")).thenReturn(new DouyinCredentialFullView(
                42L,
                "DOUYIN_WEB_SESSION",
                "ACTIVE",
                null,
                now,
                now,
                Map.of("unknownBundleField", "keep-me", "cookies", List.of(Map.of("name", "raw")))
        ));

        mvc.perform(get("/api/douyin/auth/credentials/web/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"douyin-web-credential.json\""))
                .andExpect(jsonPath("$.payload.unknownBundleField").value("keep-me"))
                .andExpect(jsonPath("$.payload.cookies[0].name").value("raw"));
    }
}
