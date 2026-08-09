package com.socialmonitor.douyin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import com.socialmonitor.douyin.auth.domain.DouyinAuthSession;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.repository.DouyinAuthSessionRepository;
import com.socialmonitor.douyin.worker.client.DouyinWorkerClient;
import com.socialmonitor.douyin.worker.dto.WorkerConsume;
import com.socialmonitor.douyin.worker.dto.WorkerQrImage;
import com.socialmonitor.douyin.worker.dto.WorkerSessionStart;
import com.socialmonitor.douyin.worker.dto.WorkerStatus;
import com.socialmonitor.douyin.worker.dto.WorkerValidation;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DouyinWebAuthServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");
    private static final Map<String, Object> RAW_BUNDLE = Map.of(
            "version", 1,
            "authType", "DOUYIN_WEB_SESSION",
            "cookies", List.of(Map.of(
                    "name", "future_cookie",
                    "value", "raw-value",
                    "futureAttribute", "keep-me"
            )),
            "rawWorkerResult", Map.of("unknownProviderField", "keep-me")
    );

    @Mock
    private DouyinWorkerClient worker;
    @Mock
    private DouyinAuthSessionRepository sessions;
    @Mock
    private DouyinCredentialService credentials;

    private DouyinWebAuthService service;

    @BeforeEach
    void setUp() {
        service = new DouyinWebAuthService(
                properties(), worker, sessions, credentials,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void startsDatabaseSessionThenAttachesWorkerSession() {
        when(sessions.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(worker.start(180)).thenReturn(new WorkerSessionStart(
                "worker-1", "STARTING", OffsetDateTime.parse("2026-07-18T12:03:00Z"), Map.of("raw", "keep")
        ));

        var result = service.start();

        assertThat(result.status()).isEqualTo("STARTING");
        assertThat(result.imageUrl()).isEqualTo("/api/douyin/auth/web/qr/" + result.loginId() + "/image");
        assertThat(result.pollIntervalMs()).isEqualTo(1500);
        verify(sessions).attachWorkerSession(eq(result.loginId()), eq("worker-1"), any());
    }

    @Test
    void pollingAdvancesStartingSessionToWaiting() {
        UUID loginId = UUID.randomUUID();
        when(sessions.findByLoginId(loginId))
                .thenReturn(Optional.of(session(loginId, "STARTING", NOW.plusSeconds(180))));
        when(worker.status("worker-1"))
                .thenReturn(new WorkerStatus("WAITING", "scan", Map.of("qrAvailable", true)));

        var result = service.poll(loginId);

        assertThat(result.status()).isEqualTo("WAITING");
        verify(sessions).updateStatus(loginId, "WAITING", Map.of("qrAvailable", true));
    }

    @Test
    void deletesWorkerSessionWhenDatabaseAttachmentFails() {
        when(sessions.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(worker.start(180)).thenReturn(new WorkerSessionStart(
                "worker-1", "STARTING", OffsetDateTime.parse("2026-07-18T12:03:00Z"), Map.of()
        ));
        doThrow(new RuntimeException("db attach failed"))
                .when(sessions).attachWorkerSession(any(), eq("worker-1"), any());

        assertThatThrownBy(() -> service.start())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db attach failed");

        verify(worker).delete("worker-1");
    }

    @Test
    void proxiesQrImageBytesWithoutChangingThem() {
        UUID loginId = UUID.randomUUID();
        when(sessions.findByLoginId(loginId)).thenReturn(Optional.of(session(loginId, "WAITING", NOW.plusSeconds(180))));
        byte[] rawPng = new byte[] {0x01, 0x02, (byte) 0xFF};
        when(worker.qr("worker-1")).thenReturn(new WorkerQrImage(rawPng, "image/png"));

        var result = service.qr(loginId);

        assertThat(result.bytes()).containsExactly(rawPng);
        assertThat(result.contentType()).isEqualTo("image/png");
    }

    @Test
    void successfulWorkerSessionPersistsUnmodifiedBundleOnce() {
        UUID loginId = UUID.randomUUID();
        when(sessions.findByLoginId(loginId)).thenReturn(Optional.of(session(loginId, "WAITING", NOW.plusSeconds(180))));
        when(worker.status("worker-1")).thenReturn(new WorkerStatus("SUCCESS", "validated", Map.of("futureStatus", "keep")));
        when(worker.consume("worker-1")).thenReturn(new WorkerConsume(RAW_BUNDLE, Map.of("futureConsume", "keep")));
        DouyinStoredCredential stored = stored(RAW_BUNDLE, 42L);
        when(credentials.completeWebLogin(eq(loginId), eq(RAW_BUNDLE), any())).thenReturn(stored);

        var result = service.poll(loginId);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.credential().payload()).isEqualTo(RAW_BUNDLE);
        verify(credentials).completeWebLogin(eq(loginId), eq(RAW_BUNDLE), any());
        verify(worker).consume("worker-1");
    }

    @Test
    void userActionRequiredOnlyUpdatesRawSessionAndCanBePolledAgain() {
        UUID loginId = UUID.randomUUID();
        when(sessions.findByLoginId(loginId)).thenReturn(Optional.of(session(loginId, "WAITING", NOW.plusSeconds(180))));
        when(worker.status("worker-1")).thenReturn(new WorkerStatus(
                "USER_ACTION_REQUIRED", "solve in browser", Map.of("challenge", "raw")
        ));

        var result = service.poll(loginId);

        assertThat(result.status()).isEqualTo("USER_ACTION_REQUIRED");
        verify(sessions).updateStatus(loginId, "USER_ACTION_REQUIRED", Map.of("challenge", "raw"));
        verify(worker, never()).consume(any());
        verifyNoInteractions(credentials);
    }

    @Test
    void workerFailurePersistsActionableDetailsForSubsequentPolls() {
        UUID loginId = UUID.randomUUID();
        String message = "Chromium failed to start: executable is missing";
        Map<String, Object> rawResult = Map.of(
                "status", "FAILED",
                "message", message,
                "errorName", "ExecutableDoesNotExist"
        );
        DouyinAuthSession waiting = session(loginId, "STARTING", NOW.plusSeconds(180));
        DouyinAuthSession failed = failedSession(loginId, message, rawResult);
        when(sessions.findByLoginId(loginId))
                .thenReturn(Optional.of(waiting), Optional.of(failed));
        when(worker.status("worker-1"))
                .thenReturn(new WorkerStatus("FAILED", message, rawResult));

        var first = service.poll(loginId);
        var subsequent = service.poll(loginId);

        assertThat(first.status()).isEqualTo("FAILED");
        assertThat(first.message()).isEqualTo(message);
        assertThat(subsequent.status()).isEqualTo("FAILED");
        assertThat(subsequent.message()).isEqualTo(message);
        verify(sessions).completeFailure(loginId, "WORKER_SESSION_FAILED", message, rawResult);
        verify(sessions, never()).updateStatus(loginId, "FAILED", rawResult);
        verify(worker).status("worker-1");
        verify(worker).delete("worker-1");
        verifyNoInteractions(credentials);
    }

    @Test
    void consumeFailureLeavesCurrentCredentialUntouchedAndStoresFailureResult() {
        UUID loginId = UUID.randomUUID();
        when(sessions.findByLoginId(loginId)).thenReturn(Optional.of(session(loginId, "WAITING", NOW.plusSeconds(180))));
        when(worker.status("worker-1")).thenReturn(new WorkerStatus("SUCCESS", "validated", Map.of("raw", "status")));
        when(worker.consume("worker-1")).thenThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "raw worker failure"));

        assertThatThrownBy(() -> service.poll(loginId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("raw worker failure");

        verify(sessions).completeFailure(eq(loginId), eq("WEB_SESSION_CAPTURE_FAILED"), any(), any());
        verifyNoInteractions(credentials);
    }

    @Test
    void lateConsumeFailureReturnsTheSuccessPersistedByAnotherPoll() {
        UUID loginId = UUID.randomUUID();
        DouyinAuthSession waiting = session(loginId, "WAITING", NOW.plusSeconds(180));
        DouyinAuthSession completed = session(loginId, "SUCCESS", NOW.plusSeconds(180));
        DouyinStoredCredential stored = stored(RAW_BUNDLE, 42L);
        when(sessions.findByLoginId(loginId))
                .thenReturn(Optional.of(waiting), Optional.of(completed));
        when(worker.status("worker-1"))
                .thenReturn(new WorkerStatus("SUCCESS", "validated", Map.of("raw", "status")));
        when(worker.consume("worker-1"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "worker session already consumed"));
        when(credentials.requireActiveWeb()).thenReturn(stored);

        var result = service.poll(loginId);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.credential().credentialId()).isEqualTo(42L);
        verify(sessions, never()).completeFailure(eq(loginId), any(), any(), any());
    }

    @Test
    void expiredQrSessionNeverCallsWorkerStatusOrTouchesCredentials() {
        UUID loginId = UUID.randomUUID();
        when(sessions.findByLoginId(loginId)).thenReturn(Optional.of(session(loginId, "WAITING", NOW.minusSeconds(1))));

        var result = service.poll(loginId);

        assertThat(result.status()).isEqualTo("EXPIRED");
        assertThat(result.expiresInSeconds()).isZero();
        verify(sessions).updateStatus(eq(loginId), eq("EXPIRED"), any());
        verify(worker, never()).status(any());
        verifyNoInteractions(credentials);
    }

    @Test
    void validatingCurrentBundleCreatesANewActiveHistoryRowOnlyWhenWorkerAcceptsIt() {
        DouyinStoredCredential current = stored(RAW_BUNDLE, 41L);
        Map<String, Object> refreshedBundle = Map.of(
                "cookies", List.of(Map.of("name", "future_cookie", "value", "refreshed")),
                "lastValidatedAt", "2026-07-18T12:01:00Z"
        );
        when(credentials.requireActiveWeb()).thenReturn(current);
        when(worker.validate(RAW_BUNDLE)).thenReturn(new WorkerValidation(
                true, "reusable", refreshedBundle, Map.of("details", Map.of("raw", "keep"))
        ));
        when(credentials.replaceActiveWeb(41L, refreshedBundle)).thenReturn(stored(refreshedBundle, 42L));

        var result = service.validateCurrent();

        assertThat(result.valid()).isTrue();
        assertThat(result.credential().credentialId()).isEqualTo(42L);
        assertThat(result.credential().payload()).isEqualTo(refreshedBundle);
        verify(credentials).replaceActiveWeb(41L, refreshedBundle);
        verify(credentials, never()).markWebInvalid(any());
    }

    @Test
    void failedValidationMarksOnlyTheCurrentWebCredentialInvalid() {
        DouyinStoredCredential current = stored(RAW_BUNDLE, 41L);
        when(credentials.requireActiveWeb()).thenReturn(current);
        when(worker.validate(RAW_BUNDLE)).thenReturn(new WorkerValidation(
                false, "expired", null, Map.of("details", Map.of("reason", "raw-expired"))
        ));

        var result = service.validateCurrent();

        assertThat(result.valid()).isFalse();
        assertThat(result.rawResult()).containsKey("details");
        verify(credentials).markWebInvalid(41L);
        verify(credentials, never()).replaceActiveWeb(any(), any());
    }

    private DouyinAuthProperties properties() {
        return new DouyinAuthProperties(
                true, "disabled", "", "", "", "user_info", "",
                "http://127.0.0.1:8787", "", 180, 1500, 2000, 5000
        );
    }

    private DouyinAuthSession session(UUID loginId, String status, Instant expiresAt) {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return new DouyinAuthSession(
                loginId, "WEB_QR", "live", "worker-1", null, status,
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC), null, null, null,
                Map.of(), now, now
        );
    }

    private DouyinAuthSession failedSession(
            UUID loginId,
            String errorMessage,
            Map<String, Object> rawResult
    ) {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return new DouyinAuthSession(
                loginId, "WEB_QR", "live", "worker-1", null, "FAILED",
                now.plusSeconds(180), now, "WORKER_SESSION_FAILED", errorMessage,
                rawResult, now, now
        );
    }

    private DouyinStoredCredential stored(Map<String, Object> payload, long id) {
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return new DouyinStoredCredential(id, 2L, "DOUYIN_WEB_SESSION", "ACTIVE", payload, null, now, now);
    }
}
