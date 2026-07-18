package com.socialmonitor.douyin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.client.DouyinOAuthClient;
import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import com.socialmonitor.douyin.auth.domain.DouyinAuthConstants;
import com.socialmonitor.douyin.auth.domain.DouyinAuthSession;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.repository.DouyinAuthSessionRepository;
import com.socialmonitor.douyin.auth.repository.DouyinCredentialRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("unchecked")
class DouyinOAuthServiceTests {

    private final DouyinAuthSessionRepository sessions = mock(DouyinAuthSessionRepository.class);
    private final DouyinCredentialRepository credentials = mock(DouyinCredentialRepository.class);
    private final DouyinOAuthClient client = mock(DouyinOAuthClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-18T04:00:00Z"), ZoneOffset.UTC);

    @Test
    void disabledModeRejectsStartWithoutCreatingSession() {
        DouyinOAuthService service = service("disabled");

        assertThatThrownBy(service::start)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("disabled");
        verify(sessions, never()).create(any());
    }

    @Test
    void liveStartBuildsOfficialAuthorizationUrlWithStateAndConfiguredScope() {
        DouyinOAuthService service = service("live");

        var result = service.start();

        assertThat(result.mode()).isEqualTo("live");
        assertThat(result.authorizationUrl()).startsWith("https://open.douyin.com/platform/oauth/connect/");
        assertThat(result.authorizationUrl()).contains("client_key=client-key");
        assertThat(result.authorizationUrl()).contains("response_type=code");
        assertThat(result.authorizationUrl()).contains("scope=user_info%2Cvideo.list");
        assertThat(result.authorizationUrl()).contains("state=" + result.state());
        assertThat(result.authorizationUrl()).contains("redirect_uri=https%3A%2F%2Fexample.test%2Fcallback");
        verify(sessions).create(any(DouyinAuthSession.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mockCallbackPersistsEveryCallbackAndRawTokenField() {
        AtomicReference<DouyinAuthSession> created = captureCreatedSession();
        when(sessions.findByState(anyString())).thenAnswer(invocation -> Optional.of(created.get()));
        when(credentials.saveActive(eq(DouyinAuthConstants.OAUTH_AUTH_TYPE), any(), any()))
                .thenAnswer(invocation -> stored(invocation.getArgument(1), invocation.getArgument(2)));
        DouyinOAuthService service = service("mock");
        var start = service.start();

        Map<String, List<String>> callback = new LinkedHashMap<>();
        callback.put("code", List.of("raw-code"));
        callback.put("state", List.of(start.state()));
        callback.put("provider_extra", List.of("keep-me", "keep-second"));
        DouyinStoredCredential result = service.complete(start.state(), callback);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(credentials).saveActive(
                eq(DouyinAuthConstants.OAUTH_AUTH_TYPE),
                payloadCaptor.capture(),
                any(OffsetDateTime.class)
        );
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsEntry("authorizationCode", "raw-code");
        assertThat(((Map<?, ?>) payload.get("callbackParameters")).get("provider_extra"))
                .isEqualTo(List.of("keep-me", "keep-second"));
        assertThat(payload).containsKeys("rawTokenResponse", "account", "accessToken", "refreshToken");
        assertThat(((Map<?, ?>) payload.get("rawTokenResponse")).containsKey("data")).isTrue();
        assertThat(result.payload()).isEqualTo(payload);
        verify(sessions).completeSuccess(eq(start.loginId()), any(Map.class));
    }

    @Test
    void rejectsCallbackWhoseStateDoesNotMatchTheStoredSession() {
        DouyinAuthSession session = session("expected-state", "live");
        when(sessions.findByState("expected-state")).thenReturn(Optional.of(session));
        DouyinOAuthService service = service("live");

        assertThatThrownBy(() -> service.complete("expected-state", Map.of(
                "code", List.of("raw-code"),
                "state", List.of("different-state")
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("state");
        verify(client, never()).exchangeCode(anyString());
        verify(credentials, never()).saveActive(anyString(), any(), any());
    }

    @Test
    void refreshFailureKeepsTheCurrentActiveCredential() {
        Map<String, Object> activePayload = new LinkedHashMap<>();
        activePayload.put("accessToken", "old-access");
        activePayload.put("refreshToken", "old-refresh");
        activePayload.put("openId", "open-id");
        when(credentials.findActive(DouyinAuthConstants.OAUTH_AUTH_TYPE))
                .thenReturn(Optional.of(stored(activePayload, OffsetDateTime.parse("2026-07-19T12:00:00Z"))));
        when(client.refreshAccessToken("old-refresh"))
                .thenThrow(new BusinessException(com.socialmonitor.common.error.ErrorCode.BUSINESS_ERROR, "provider failed"));
        DouyinOAuthService service = service("live");

        assertThatThrownBy(service::refresh).hasMessageContaining("provider failed");

        verify(credentials, never()).saveActive(anyString(), any(), any());
        verify(sessions).completeFailure(any(), eq("OAUTH_TOKEN_EXCHANGE_FAILED"), anyString(), any(Map.class));
    }

    private AtomicReference<DouyinAuthSession> captureCreatedSession() {
        AtomicReference<DouyinAuthSession> created = new AtomicReference<>();
        doAnswer(invocation -> {
            DouyinAuthSession value = invocation.getArgument(0);
            created.set(value);
            return value;
        }).when(sessions).create(any(DouyinAuthSession.class));
        return created;
    }

    private DouyinOAuthService service(String mode) {
        return new DouyinOAuthService(properties(mode), sessions, credentials, client, clock);
    }

    private DouyinAuthProperties properties(String mode) {
        return new DouyinAuthProperties(
                true,
                mode,
                "client-key",
                "client-secret",
                "https://example.test/callback",
                "user_info,video.list",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "http://127.0.0.1:8787",
                "",
                180,
                1500,
                5000,
                30000
        );
    }

    private DouyinAuthSession session(String state, String mode) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return new DouyinAuthSession(
                java.util.UUID.randomUUID(),
                "OAUTH_LOGIN",
                mode,
                null,
                state,
                "WAITING",
                now.plusMinutes(3),
                null,
                null,
                null,
                Map.of(),
                now,
                now
        );
    }

    private DouyinStoredCredential stored(Map<String, Object> payload, OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return new DouyinStoredCredential(
                17L,
                2L,
                DouyinAuthConstants.OAUTH_AUTH_TYPE,
                "ACTIVE",
                payload,
                expiresAt,
                now,
                now
        );
    }
}
