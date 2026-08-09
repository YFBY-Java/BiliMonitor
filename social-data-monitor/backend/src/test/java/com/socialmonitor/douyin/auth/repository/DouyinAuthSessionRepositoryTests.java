package com.socialmonitor.douyin.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class DouyinAuthSessionRepositoryTests {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final DouyinAuthSessionRepository repository =
            new DouyinAuthSessionRepository(jdbcTemplate, new ObjectMapper());

    @Test
    void oauthValidationClaimIsACompareAndSetFromWaiting() {
        when(jdbcTemplate.update(
                contains("AND status = 'WAITING'"),
                any(MapSqlParameterSource.class)
        )).thenReturn(1);

        boolean claimed = repository.tryClaimForValidation(UUID.randomUUID(), Map.of("callback", "raw"));

        assertThat(claimed).isTrue();
    }

    @Test
    void lateFailureAndProgressUpdatesCannotOverwriteTerminalSuccess() {
        UUID loginId = UUID.randomUUID();

        repository.completeFailure(loginId, "FAILED", "late", Map.of());
        repository.updateStatus(loginId, "WAITING", Map.of());

        verify(jdbcTemplate).update(
                contains("AND status <> 'SUCCESS'"),
                any(MapSqlParameterSource.class)
        );
        verify(jdbcTemplate).update(
                contains("AND status NOT IN ('SUCCESS', 'FAILED', 'EXPIRED')"),
                any(MapSqlParameterSource.class)
        );
    }

    @Test
    void attachingWorkerSessionPreservesStartingStatus() {
        UUID loginId = UUID.randomUUID();

        repository.attachWorkerSession(loginId, "worker-1", Map.of("status", "STARTING"));

        verify(jdbcTemplate).update(
                contains("worker_session_id = :workerSessionId"),
                any(MapSqlParameterSource.class)
        );
        verify(jdbcTemplate, never()).update(
                contains("status = 'WAITING'"),
                any(MapSqlParameterSource.class)
        );
    }
}
