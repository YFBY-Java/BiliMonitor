package com.socialmonitor.douyin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import com.socialmonitor.douyin.auth.domain.DouyinAuthSession;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.repository.DouyinAuthSessionRepository;
import com.socialmonitor.douyin.auth.repository.DouyinCredentialRepository;
import java.time.OffsetDateTime;
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
class DouyinCredentialServiceTests {

    @Mock
    private DouyinCredentialRepository credentials;
    @Mock
    private DouyinAuthSessionRepository sessions;

    private DouyinCredentialService service;

    @BeforeEach
    void setUp() {
        service = new DouyinCredentialService(properties(), credentials, sessions);
    }

    @Test
    void locksTheLoginRowAndAtomicallyCompletesItWithTheUnmodifiedBundle() {
        UUID loginId = UUID.randomUUID();
        Map<String, Object> bundle = Map.of(
                "authType", "DOUYIN_WEB_SESSION",
                "cookies", List.of(Map.of(
                        "name", "sessionid_ss",
                        "value", "raw",
                        "expires", 2_000_000_000L,
                        "unknownAttribute", "keep-me"
                )),
                "unknownBundleField", "keep-me"
        );
        Map<String, Object> workerRaw = Map.of("futureWorkerField", "keep-me");
        DouyinAuthSession session = session(loginId, "WAITING");
        DouyinStoredCredential stored = stored(42L, bundle);
        when(sessions.findByLoginIdForUpdate(loginId)).thenReturn(Optional.of(session));
        when(credentials.saveActive(
                "DOUYIN_WEB_SESSION",
                bundle,
                OffsetDateTime.parse("2033-05-18T03:33:20Z")
        )).thenReturn(stored);

        var result = service.completeWebLogin(loginId, bundle, workerRaw);

        assertThat(result).isSameAs(stored);
        verify(credentials).saveActive(
                "DOUYIN_WEB_SESSION",
                bundle,
                OffsetDateTime.parse("2033-05-18T03:33:20Z")
        );
        verify(sessions).completeSuccess(
                org.mockito.ArgumentMatchers.eq(loginId),
                org.mockito.ArgumentMatchers.argThat(raw ->
                        "keep-me".equals(raw.get("futureWorkerField")) && Long.valueOf(42L).equals(raw.get("credentialId")))
        );
    }

    @Test
    void alreadyCompletedSessionReturnsCurrentCredentialWithoutCreatingAnotherHistoryRow() {
        UUID loginId = UUID.randomUUID();
        Map<String, Object> bundle = Map.of("authType", "DOUYIN_WEB_SESSION", "cookies", List.of());
        DouyinStoredCredential existing = stored(42L, bundle);
        when(sessions.findByLoginIdForUpdate(loginId)).thenReturn(Optional.of(session(loginId, "SUCCESS")));
        when(credentials.findActive("DOUYIN_WEB_SESSION")).thenReturn(Optional.of(existing));

        var result = service.completeWebLogin(loginId, bundle, Map.of());

        assertThat(result).isSameAs(existing);
        verify(credentials, never()).saveActive(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(sessions, never()).completeSuccess(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void revalidationCannotReplaceAWebCredentialThatChangedWhileTheWorkerWasRunning() {
        Map<String, Object> bundle = Map.of(
                "authType", "DOUYIN_WEB_SESSION",
                "cookies", List.of()
        );
        when(credentials.saveActiveIfCurrent(
                "DOUYIN_WEB_SESSION",
                41L,
                bundle,
                null
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceActiveWeb(41L, bundle))
                .hasMessageContaining("changed while validation");

        verify(credentials, never()).saveActive(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private DouyinAuthSession session(UUID loginId, String status) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-18T12:00:00Z");
        return new DouyinAuthSession(
                loginId, "WEB_QR", "live", "worker-1", null, status,
                now.plusMinutes(3), null, null, null, Map.of(), now, now
        );
    }

    private DouyinStoredCredential stored(long id, Map<String, Object> bundle) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-18T12:00:00Z");
        return new DouyinStoredCredential(id, 2L, "DOUYIN_WEB_SESSION", "ACTIVE", bundle, null, now, now);
    }

    private DouyinAuthProperties properties() {
        return new DouyinAuthProperties(
                true, "disabled", "", "", "", "user_info", "",
                "http://127.0.0.1:8787", "", 180, 1500, 2000, 5000
        );
    }
}
