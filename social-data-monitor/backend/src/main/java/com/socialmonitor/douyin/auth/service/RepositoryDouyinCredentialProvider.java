package com.socialmonitor.douyin.auth.service;

import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.domain.DouyinAuthConstants;
import com.socialmonitor.douyin.auth.domain.DouyinOAuthCredential;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.domain.DouyinWebSessionCredential;
import com.socialmonitor.douyin.auth.repository.DouyinCredentialRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.douyin.auth", name = "enabled", havingValue = "true")
public class RepositoryDouyinCredentialProvider implements DouyinCredentialProvider {

    private final DouyinCredentialRepository repository;

    public RepositoryDouyinCredentialProvider(DouyinCredentialRepository repository) {
        this.repository = repository;
    }

    @Override
    public DouyinOAuthCredential requireActiveOAuth() {
        DouyinStoredCredential stored = repository.findActive(DouyinAuthConstants.OAUTH_AUTH_TYPE)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "No active Douyin OAuth credential."
                ));
        Map<String, Object> payload = stored.payload();
        String accessToken = string(payload.get("accessToken"));
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Active Douyin OAuth credential does not contain accessToken.");
        }
        return new DouyinOAuthCredential(
                accessToken,
                string(payload.get("refreshToken")),
                string(payload.get("openId")),
                string(payload.get("unionId")),
                stringList(payload.get("scope")),
                dateTime(payload.get("expiresAt"), stored.expiresAt()),
                payload
        );
    }

    @Override
    public DouyinWebSessionCredential requireActiveWebSession() {
        DouyinStoredCredential stored = repository.findActive(DouyinAuthConstants.WEB_AUTH_TYPE)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "No active Douyin Web session credential."
                ));
        Map<String, Object> payload = stored.payload();
        List<Map<String, Object>> cookies = mapList(payload.get("cookies"));
        if (cookies.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Active Douyin Web session credential does not contain cookies.");
        }
        return new DouyinWebSessionCredential(
                cookies,
                stringMap(payload.get("cookieHeadersByOrigin")),
                objectMap(payload.get("storageState")),
                objectMap(payload.get("browserContext")),
                payload
        );
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> mapped = objectMap(item);
            if (!mapped.isEmpty()) {
                result.add(mapped);
            }
        }
        return List.copyOf(result);
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item == null ? null : String.valueOf(item)));
        return result;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        return List.of(String.valueOf(value).split(","));
    }

    private OffsetDateTime dateTime(Object value, OffsetDateTime fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
