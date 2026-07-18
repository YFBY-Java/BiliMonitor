package com.socialmonitor.douyin.auth.client;

import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(prefix = "app.douyin.auth", name = "enabled", havingValue = "true")
public class HttpDouyinOAuthClient implements DouyinOAuthClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final DouyinAuthProperties properties;
    private final RestClient restClient;

    public HttpDouyinOAuthClient(DouyinAuthProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl("https://open.douyin.com").build();
    }

    @Override
    public Map<String, Object> exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_key", properties.oauthClientKey());
        form.add("client_secret", properties.oauthClientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        return postForm("/oauth/access_token/", form);
    }

    @Override
    public Map<String, Object> refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_key", properties.oauthClientKey());
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return postForm("/oauth/refresh_token/", form);
    }

    @Override
    public Map<String, Object> renewRefreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_key", properties.oauthClientKey());
        form.add("refresh_token", refreshToken);
        return postForm("/oauth/renew_refresh_token/", form);
    }

    @Override
    public Map<String, Object> fetchUserInfo(String accessToken, String openId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", accessToken);
        form.add("open_id", openId);
        return postForm("/oauth/userinfo/", form);
    }

    private Map<String, Object> postForm(String path, MultiValueMap<String, String> form) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
            return response == null ? Map.of() : response;
        } catch (RestClientResponseException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Douyin OAuth HTTP " + exception.getStatusCode().value()
                            + ": " + exception.getResponseBodyAsString());
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Douyin OAuth request failed: " + exception.getMessage());
        }
    }
}
