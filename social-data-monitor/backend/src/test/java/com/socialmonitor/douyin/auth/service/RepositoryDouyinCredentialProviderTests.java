package com.socialmonitor.douyin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.domain.DouyinAuthConstants;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.repository.DouyinCredentialRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryDouyinCredentialProviderTests {

    private final DouyinCredentialRepository repository = mock(DouyinCredentialRepository.class);
    private final RepositoryDouyinCredentialProvider provider = new RepositoryDouyinCredentialProvider(repository);

    @Test
    void returnsStrongOAuthCredentialAndPreservesRawPayload() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-08-01T12:00:00+08:00");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("accessToken", "access-raw");
        raw.put("refreshToken", "refresh-raw");
        raw.put("openId", "open-id");
        raw.put("unionId", "union-id");
        raw.put("scope", List.of("user_info", "video.list"));
        raw.put("expiresAt", expiresAt.toString());
        raw.put("rawTokenResponse", Map.of("provider_extra", "keep-me"));
        when(repository.findActive(DouyinAuthConstants.OAUTH_AUTH_TYPE))
                .thenReturn(Optional.of(stored(DouyinAuthConstants.OAUTH_AUTH_TYPE, raw, expiresAt)));

        var result = provider.requireActiveOAuth();

        assertThat(result.accessToken()).isEqualTo("access-raw");
        assertThat(result.scope()).containsExactly("user_info", "video.list");
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        assertThat(result.rawPayload()).isEqualTo(raw);
    }

    @Test
    void returnsStrongWebCredentialWithoutFilteringCookiesOrStorage() {
        List<Map<String, Object>> cookies = List.of(Map.of(
                "name", "future_cookie_name",
                "value", "raw-cookie",
                "partitionKey", "future-attribute"
        ));
        Map<String, Object> storageState = Map.of("cookies", cookies, "origins", List.of());
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("cookies", cookies);
        raw.put("cookieHeadersByOrigin", Map.of("https://www.douyin.com", "future_cookie_name=raw-cookie"));
        raw.put("storageState", storageState);
        raw.put("browserContext", Map.of("locale", "zh-CN", "unknownContextField", true));
        raw.put("rawWorkerResult", Map.of("provider_extra", "keep-me"));
        when(repository.findActive(DouyinAuthConstants.WEB_AUTH_TYPE))
                .thenReturn(Optional.of(stored(DouyinAuthConstants.WEB_AUTH_TYPE, raw, null)));

        var result = provider.requireActiveWebSession();

        assertThat(result.cookies()).isEqualTo(cookies);
        assertThat(result.cookieHeadersByOrigin())
                .containsEntry("https://www.douyin.com", "future_cookie_name=raw-cookie");
        assertThat(result.storageState()).isEqualTo(storageState);
        assertThat(result.rawPayload()).isEqualTo(raw);
    }

    @Test
    void reportsMissingCredentialInsteadOfReturningEmptyValues() {
        when(repository.findActive(DouyinAuthConstants.OAUTH_AUTH_TYPE)).thenReturn(Optional.empty());

        assertThatThrownBy(provider::requireActiveOAuth)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No active Douyin OAuth credential");
    }

    private DouyinStoredCredential stored(
            String authType,
            Map<String, Object> payload,
            OffsetDateTime expiresAt
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-18T12:00:00+08:00");
        return new DouyinStoredCredential(9L, 2L, authType, "ACTIVE", payload, expiresAt, now, now);
    }
}
