package com.socialmonitor.douyin.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.douyin.auth.domain.DouyinAuthConstants;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.service.DouyinCredentialCipher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

class DouyinCredentialRepositoryTests {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final DouyinCredentialCipher cipher = mock(DouyinCredentialCipher.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DouyinCredentialRepository repository =
            new DouyinCredentialRepository(jdbcTemplate, objectMapper, cipher);

    @Test
    void locksAndRevokesBeforeInsertingTheNewActiveCredential() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-07-21T12:00:00+08:00");
        OffsetDateTime now = OffsetDateTime.parse("2026-07-18T12:00:00+08:00");
        Map<String, Object> raw = Map.of(
                "accessToken", "second",
                "rawTokenResponse", Map.of("sequence", 2)
        );
        Map<String, Object> encrypted = Map.of(
                "alg", "AES-256-GCM",
                "iv", "iv",
                "ciphertext", "ciphertext"
        );
        DouyinStoredCredential stored = new DouyinStoredCredential(
                11L, 2L, DouyinAuthConstants.OAUTH_AUTH_TYPE, "ACTIVE", raw, expiresAt, now, now
        );
        when(jdbcTemplate.queryForList(
                anyString(),
                ArgumentMatchers.<Map<String, ?>>any(),
                eq(Long.class)
        )).thenReturn(List.of(2L));
        when(cipher.encrypt(raw)).thenReturn(encrypted);
        when(jdbcTemplate.queryForObject(
                contains("INSERT INTO platform_credential"),
                any(MapSqlParameterSource.class),
                ArgumentMatchers.<RowMapper<DouyinStoredCredential>>any()
        )).thenReturn(stored);

        DouyinStoredCredential result = repository.saveActive(
                DouyinAuthConstants.OAUTH_AUTH_TYPE,
                raw,
                expiresAt
        );

        assertThat(result).isSameAs(stored);
        InOrder order = inOrder(jdbcTemplate, cipher);
        order.verify(jdbcTemplate).query(
                contains("pg_advisory_xact_lock"),
                eq(Map.of("lockKey", "2:" + DouyinAuthConstants.OAUTH_AUTH_TYPE)),
                ArgumentMatchers.<ResultSetExtractor<Object>>any()
        );
        order.verify(jdbcTemplate).update(
                contains("SET status = 'REVOKED'"),
                eq(Map.of("platformId", 2L, "authType", DouyinAuthConstants.OAUTH_AUTH_TYPE))
        );
        order.verify(cipher).encrypt(raw);
        order.verify(jdbcTemplate).queryForObject(
                contains("INSERT INTO platform_credential"),
                any(MapSqlParameterSource.class),
                ArgumentMatchers.<RowMapper<DouyinStoredCredential>>any()
        );
    }

    @Test
    void declaresTheCredentialSwitchAsATransactionBoundary() throws Exception {
        Transactional transactional = DouyinCredentialRepository.class
                .getMethod("saveActive", String.class, Map.class, OffsetDateTime.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    void revokeUsesTheSamePerAuthTypeLockAndNeverDeletesHistory() {
        when(jdbcTemplate.queryForList(
                anyString(),
                ArgumentMatchers.<Map<String, ?>>any(),
                eq(Long.class)
        )).thenReturn(List.of(2L));

        repository.revokeActive(DouyinAuthConstants.WEB_AUTH_TYPE);

        verify(jdbcTemplate).query(
                contains("pg_advisory_xact_lock"),
                eq(Map.of("lockKey", "2:" + DouyinAuthConstants.WEB_AUTH_TYPE)),
                ArgumentMatchers.<ResultSetExtractor<Object>>any()
        );
        verify(jdbcTemplate).update(
                contains("SET status = 'REVOKED'"),
                eq(Map.of("platformId", 2L, "authType", DouyinAuthConstants.WEB_AUTH_TYPE))
        );
    }
}
