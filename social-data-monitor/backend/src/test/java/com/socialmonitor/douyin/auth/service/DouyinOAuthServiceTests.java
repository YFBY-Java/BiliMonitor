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
import org.springframework.transaction.annotation.Transactional;

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
        when(sessions.tryClaimForValidation(any(), any())).thenReturn(true);
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
    void repeatedSuccessfulCallbackReturnsTheOriginalCredentialWithoutOverwritingTheSession() {
        DouyinAuthSession session = session(
                java.util.UUID.randomUUID(),
                "completed-state",
                "live",
                "SUCCESS",
                Map.of("credentialId", 17L)
        );
        DouyinStoredCredential original = stored(17L, Map.of("accessToken", "original"));
        when(sessions.findByState("completed-state")).thenReturn(Optional.of(session));
        when(credentials.findById(17L)).thenReturn(Optional.of(original));
        DouyinOAuthService service = service("live");

        DouyinStoredCredential result = service.complete("completed-state", Map.of(
                "code", List.of("replayed-code"),
                "state", List.of("completed-state")
        ));

        assertThat(result).isSameAs(original);
        verify(sessions, never()).completeFailure(any(), anyString(), anyString(), any());
        verify(client, never()).exchangeCode(anyString());
        verify(credentials, never()).saveActive(anyString(), any(), any());
    }

    @Test
    void lateProviderFailureDoesNotOverwriteAConcurrentSuccess() {
        DouyinAuthSession waiting = session("concurrent-state", "live");
        DouyinAuthSession completed = session(
                waiting.loginId(),
                "concurrent-state",
                "live",
                "SUCCESS"
        );
        when(sessions.findByState("concurrent-state")).thenReturn(Optional.of(waiting));
        when(sessions.findByLoginId(waiting.loginId())).thenReturn(Optional.of(completed));
        when(sessions.tryClaimForValidation(eq(waiting.loginId()), any())).thenReturn(true);
        when(client.exchangeCode("provider-code")).thenThrow(
                new BusinessException(com.socialmonitor.common.error.ErrorCode.BUSINESS_ERROR, "provider failed")
        );
        DouyinOAuthService service = service("live");

        assertThatThrownBy(() -> service.complete("concurrent-state", Map.of(
                "code", List.of("provider-code"),
                "state", List.of("concurrent-state")
        ))).isInstanceOf(BusinessException.class).hasMessageContaining("provider failed");

        verify(sessions, never()).completeFailure(any(), anyString(), anyString(), any());
        verify(credentials, never()).saveActive(anyString(), any(), any());
    }

    @Test
    void callbackThatLosesTheAtomicClaimReturnsTheConcurrentSuccess() {
        DouyinAuthSession waiting = session("claim-state", "live");
        DouyinAuthSession completed = session(
                waiting.loginId(),
                "claim-state",
                "live",
                "SUCCESS",
                Map.of("credentialId", 17L)
        );
        DouyinStoredCredential original = stored(17L, Map.of("accessToken", "winner"));
        when(sessions.findByState("claim-state")).thenReturn(Optional.of(waiting));
        when(sessions.tryClaimForValidation(eq(waiting.loginId()), any())).thenReturn(false);
        when(sessions.findByLoginId(waiting.loginId())).thenReturn(Optional.of(completed));
        when(credentials.findById(17L)).thenReturn(Optional.of(original));
        DouyinOAuthService service = service("live");

        DouyinStoredCredential result = service.complete("claim-state", Map.of(
                "code", List.of("same-code"),
                "state", List.of("claim-state")
        ));

        assertThat(result).isSameAs(original);
        verify(client, never()).exchangeCode(anyString());
        verify(sessions, never()).completeFailure(any(), anyString(), anyString(), any());
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

    @Test
    void unexpectedRefreshTransactionFailureAlsoCompletesTheAuditSession() {
        Map<String, Object> activePayload = new LinkedHashMap<>();
        activePayload.put("accessToken", "old-access");
        activePayload.put("refreshToken", "old-refresh");
        activePayload.put("openId", "open-id");
        DouyinStoredCredential active = stored(
                activePayload,
                OffsetDateTime.parse("2026-07-19T12:00:00Z")
        );
        when(credentials.findActive(DouyinAuthConstants.OAUTH_AUTH_TYPE))
                .thenReturn(Optional.of(active));
        when(client.refreshAccessToken("old-refresh"))
                .thenThrow(new IllegalStateException("transaction infrastructure failed"));
        DouyinOAuthService service = service("live");

        assertThatThrownBy(service::refresh)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction infrastructure failed");

        verify(credentials, never()).saveActive(anyString(), any(), any());
        verify(sessions).completeFailure(
                any(),
                eq("OAUTH_TOKEN_EXCHANGE_FAILED"),
                eq("transaction infrastructure failed"),
                org.mockito.ArgumentMatchers.argThat(raw ->
                        "java.lang.IllegalStateException".equals(raw.get("errorType")))
        );
    }

    @Test
    void concurrentRefreshReturnsTheCredentialCreatedWhileWaitingForTheLock() {
        Map<String, Object> oldPayload = Map.of(
                "accessToken", "old-access",
                "refreshToken", "old-refresh"
        );
        Map<String, Object> newPayload = Map.of(
                "accessToken", "new-access",
                "refreshToken", "new-refresh"
        );
        DouyinStoredCredential oldCredential = stored(17L, oldPayload);
        DouyinStoredCredential newCredential = stored(18L, newPayload);
        when(credentials.findActive(DouyinAuthConstants.OAUTH_AUTH_TYPE))
                .thenReturn(Optional.of(oldCredential), Optional.of(newCredential));
        DouyinOAuthService service = service("live");

        DouyinStoredCredential result = service.refresh();

        assertThat(result).isSameAs(newCredential);
        verify(credentials).acquireOperationLock(DouyinAuthConstants.OAUTH_AUTH_TYPE, "refresh");
        verify(client, never()).refreshAccessToken(anyString());
        verify(credentials, never()).saveActive(anyString(), any(), any());
    }

    @Test
    void refreshFailureAuditRunsOutsideTheRollbackOnlyLockTransaction() throws Exception {
        Transactional orchestrationTransaction = DouyinOAuthService.class
                .getMethod("refresh")
                .getAnnotation(Transactional.class);
        Transactional lockTransaction = DouyinCredentialOperationTransaction.class
                .getMethod("execute", String.class, String.class, java.util.function.Supplier.class)
                .getAnnotation(Transactional.class);

        assertThat(orchestrationTransaction).isNull();
        assertThat(lockTransaction).isNotNull();
    }

    @Test
    void refreshCannotOverwriteANewerOAuthLoginThatCompletedDuringTheProviderCall() {
        DouyinStoredCredential oldCredential = stored(17L, Map.of(
                "accessToken", "old-access",
                "refreshToken", "old-refresh",
                "openId", "old-open"
        ));
        DouyinStoredCredential newerLogin = stored(18L, Map.of(
                "accessToken", "new-login-access",
                "refreshToken", "new-login-refresh",
                "openId", "new-login-open"
        ));
        when(credentials.findActive(DouyinAuthConstants.OAUTH_AUTH_TYPE))
                .thenReturn(Optional.of(oldCredential), Optional.of(oldCredential), Optional.of(newerLogin));
        when(client.refreshAccessToken("old-refresh")).thenReturn(Map.of("data", Map.of(
                "access_token", "refreshed-old-access",
                "refresh_token", "refreshed-old-refresh",
                "open_id", "old-open",
                "scope", "user_info",
                "expires_in", 1200,
                "refresh_expires_in", 2400,
                "error_code", 0
        )));
        when(credentials.saveActiveIfCurrent(
                eq(DouyinAuthConstants.OAUTH_AUTH_TYPE),
                eq(17L),
                any(),
                any()
        )).thenReturn(Optional.empty());
        DouyinOAuthService service = service("live");

        DouyinStoredCredential result = service.refresh();

        assertThat(result).isSameAs(newerLogin);
        verify(credentials, never()).saveActive(anyString(), any(), any());
        verify(sessions).completeSuccess(any(), org.mockito.ArgumentMatchers.argThat(raw ->
                Boolean.TRUE.equals(raw.get("coalesced")) && Long.valueOf(18L).equals(raw.get("credentialId"))
        ));
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
        return new DouyinOAuthService(
                properties(mode),
                sessions,
                credentials,
                client,
                new DouyinCredentialOperationTransaction(credentials),
                clock
        );
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
        return session(java.util.UUID.randomUUID(), state, mode, "WAITING");
    }

    private DouyinAuthSession session(String state, String mode, String status) {
        return session(java.util.UUID.randomUUID(), state, mode, status);
    }

    private DouyinAuthSession session(java.util.UUID loginId, String state, String mode, String status) {
        return session(loginId, state, mode, status, Map.of());
    }

    private DouyinAuthSession session(
            java.util.UUID loginId,
            String state,
            String mode,
            String status,
            Map<String, Object> rawResult
    ) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return new DouyinAuthSession(
                loginId,
                "OAUTH_LOGIN",
                mode,
                null,
                state,
                status,
                now.plusMinutes(3),
                null,
                null,
                null,
                rawResult,
                now,
                now
        );
    }

    private DouyinStoredCredential stored(Map<String, Object> payload, OffsetDateTime expiresAt) {
        return stored(17L, payload, expiresAt);
    }

    private DouyinStoredCredential stored(long id, Map<String, Object> payload) {
        return stored(id, payload, OffsetDateTime.parse("2026-07-19T12:00:00Z"));
    }

    private DouyinStoredCredential stored(long id, Map<String, Object> payload, OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return new DouyinStoredCredential(
                id,
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
