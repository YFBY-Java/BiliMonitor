package com.socialmonitor.douyin.auth.service;

import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.client.DouyinOAuthClient;
import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import com.socialmonitor.douyin.auth.domain.DouyinAuthConstants;
import com.socialmonitor.douyin.auth.domain.DouyinAuthSession;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.dto.DouyinOAuthStartView;
import com.socialmonitor.douyin.auth.repository.DouyinAuthSessionRepository;
import com.socialmonitor.douyin.auth.repository.DouyinCredentialRepository;
import java.security.SecureRandom;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@ConditionalOnProperty(prefix = "app.douyin.auth", name = "enabled", havingValue = "true")
public class DouyinOAuthService {

    private static final String AUTHORIZATION_URL = "https://open.douyin.com/platform/oauth/connect/";

    private final DouyinAuthProperties properties;
    private final DouyinAuthSessionRepository sessions;
    private final DouyinCredentialRepository credentials;
    private final DouyinOAuthClient client;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public DouyinOAuthService(
            DouyinAuthProperties properties,
            DouyinAuthSessionRepository sessions,
            DouyinCredentialRepository credentials,
            DouyinOAuthClient client
    ) {
        this(properties, sessions, credentials, client, Clock.systemUTC());
    }

    DouyinOAuthService(
            DouyinAuthProperties properties,
            DouyinAuthSessionRepository sessions,
            DouyinCredentialRepository credentials,
            DouyinOAuthClient client,
            Clock clock
    ) {
        this.properties = properties;
        this.sessions = sessions;
        this.credentials = credentials;
        this.client = client;
        this.clock = clock;
    }

    public DouyinOAuthStartView start() {
        String mode = mode();
        if ("disabled".equals(mode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Douyin OAuth is disabled.");
        }
        if ("live".equals(mode)) {
            requireLiveConfiguration();
        }

        OffsetDateTime now = now();
        UUID loginId = UUID.randomUUID();
        String state = newState();
        DouyinAuthSession session = new DouyinAuthSession(
                loginId,
                "OAUTH_LOGIN",
                mode,
                null,
                state,
                "WAITING",
                now.plusSeconds(properties.qrExpireSeconds()),
                null,
                null,
                null,
                Map.of(),
                now,
                now
        );
        sessions.create(session);
        String authorizationUrl = "mock".equals(mode)
                ? mockAuthorizationUrl(loginId, state)
                : liveAuthorizationUrl(state);
        return new DouyinOAuthStartView(
                loginId,
                mode,
                authorizationUrl,
                state,
                properties.qrExpireSeconds()
        );
    }

    public String mockAuthorizationRedirect(UUID loginId, String state) {
        DouyinAuthSession session = sessions.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Douyin OAuth session not found."));
        if (!"mock".equals(session.providerMode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Douyin OAuth session is not in mock mode.");
        }
        requireSessionState(session, state);
        requireNotExpired(session);
        return UriComponentsBuilder.fromPath("/api/douyin/auth/oauth/callback")
                .queryParam("code", "mock-code-" + loginId)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    public DouyinStoredCredential complete(String state, Map<String, List<String>> callbackParameters) {
        DouyinAuthSession session = sessions.findByState(state)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Douyin OAuth state is invalid."));
        try {
            requireSessionState(session, first(callbackParameters, "state"));
            requireNotExpired(session);
            if ("SUCCESS".equals(session.status())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Douyin OAuth session is already consumed.");
            }
            String code = first(callbackParameters, "code");
            if (code == null || code.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Douyin OAuth callback does not contain code.");
            }
            sessions.updateStatus(session.loginId(), "VALIDATING", rawCallback(callbackParameters));

            Map<String, Object> tokenResponse = "mock".equals(session.providerMode())
                    ? mockTokenResponse(session.loginId())
                    : client.exchangeCode(code);
            Map<String, Object> tokenData = successfulData(tokenResponse, "Douyin token exchange failed.");
            String accessToken = requiredString(tokenData, "access_token", "Douyin token response has no access_token.");
            String refreshToken = string(tokenData.get("refresh_token"));
            String openId = string(tokenData.get("open_id"));
            List<String> scope = scope(tokenData.get("scope"));
            Map<String, Object> userInfoResponse = "mock".equals(session.providerMode())
                    ? mockUserInfoResponse(openId)
                    : fetchUserInfoWhenAuthorized(scope, accessToken, openId);

            OffsetDateTime now = now();
            OffsetDateTime expiresAt = plusSeconds(now, tokenData.get("expires_in"));
            OffsetDateTime refreshExpiresAt = plusSeconds(now, tokenData.get("refresh_expires_in"));
            Map<String, Object> payload = initialPayload(
                    session,
                    code,
                    callbackParameters,
                    tokenResponse,
                    tokenData,
                    userInfoResponse,
                    accessToken,
                    refreshToken,
                    openId,
                    scope,
                    expiresAt,
                    refreshExpiresAt,
                    now
            );
            DouyinStoredCredential stored = credentials.saveActive(
                    DouyinAuthConstants.OAUTH_AUTH_TYPE,
                    payload,
                    expiresAt
            );
            Map<String, Object> rawResult = new LinkedHashMap<>();
            rawResult.put("callbackParameters", callbackParameters);
            rawResult.put("rawTokenResponse", tokenResponse);
            rawResult.put("rawUserInfo", userInfoResponse);
            rawResult.put("credentialId", stored.credentialId());
            sessions.completeSuccess(session.loginId(), rawResult);
            return stored;
        } catch (BusinessException exception) {
            sessions.completeFailure(
                    session.loginId(),
                    "OAUTH_TOKEN_EXCHANGE_FAILED",
                    exception.getMessage(),
                    rawCallback(callbackParameters)
            );
            throw exception;
        }
    }

    public DouyinStoredCredential refresh() {
        String mode = mode();
        if ("disabled".equals(mode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Douyin OAuth is disabled.");
        }
        DouyinStoredCredential active = credentials.findActive(DouyinAuthConstants.OAUTH_AUTH_TYPE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "No active Douyin OAuth credential."));
        String refreshToken = requiredString(
                active.payload(),
                "refreshToken",
                "Active Douyin OAuth credential has no refreshToken."
        );
        OffsetDateTime now = now();
        UUID loginId = UUID.randomUUID();
        sessions.create(new DouyinAuthSession(
                loginId,
                "OAUTH_REFRESH",
                mode,
                null,
                null,
                "VALIDATING",
                now.plusSeconds(properties.qrExpireSeconds()),
                null,
                null,
                null,
                Map.of("credentialId", active.credentialId()),
                now,
                now
        ));
        try {
            Map<String, Object> refreshResponse = "mock".equals(mode)
                    ? mockRefreshResponse(active)
                    : client.refreshAccessToken(refreshToken);
            Map<String, Object> tokenData = successfulData(refreshResponse, "Douyin access token refresh failed.");
            String accessToken = requiredString(tokenData, "access_token", "Refresh response has no access_token.");
            String nextRefreshToken = Optional.ofNullable(string(tokenData.get("refresh_token")))
                    .filter(value -> !value.isBlank())
                    .orElse(refreshToken);
            OffsetDateTime expiresAt = plusSeconds(now, tokenData.get("expires_in"));
            OffsetDateTime refreshExpiresAt = plusSeconds(now, tokenData.get("refresh_expires_in"));

            Map<String, Object> payload = new LinkedHashMap<>(active.payload());
            payload.put("accessToken", accessToken);
            payload.put("refreshToken", nextRefreshToken);
            payload.put("openId", Optional.ofNullable(string(tokenData.get("open_id")))
                    .orElse(string(active.payload().get("openId"))));
            payload.put("scope", tokenData.containsKey("scope")
                    ? scope(tokenData.get("scope"))
                    : active.payload().get("scope"));
            payload.put("expiresAt", text(expiresAt));
            payload.put("refreshExpiresAt", text(refreshExpiresAt));
            payload.put("lastRefreshedAt", now.toString());
            payload.put("refreshedFromCredentialId", active.credentialId());
            payload.put("rawRefreshResponse", refreshResponse);

            DouyinStoredCredential stored = credentials.saveActive(
                    DouyinAuthConstants.OAUTH_AUTH_TYPE,
                    payload,
                    expiresAt
            );
            sessions.completeSuccess(loginId, Map.of(
                    "credentialId", stored.credentialId(),
                    "rawRefreshResponse", refreshResponse
            ));
            return stored;
        } catch (BusinessException exception) {
            sessions.completeFailure(
                    loginId,
                    "OAUTH_TOKEN_EXCHANGE_FAILED",
                    exception.getMessage(),
                    Map.of("credentialId", active.credentialId(), "error", exception.getMessage())
            );
            throw exception;
        }
    }

    private Map<String, Object> initialPayload(
            DouyinAuthSession session,
            String code,
            Map<String, List<String>> callbackParameters,
            Map<String, Object> tokenResponse,
            Map<String, Object> tokenData,
            Map<String, Object> userInfoResponse,
            String accessToken,
            String refreshToken,
            String openId,
            List<String> scope,
            OffsetDateTime expiresAt,
            OffsetDateTime refreshExpiresAt,
            OffsetDateTime authorizedAt
    ) {
        Map<String, Object> userData = objectMap(userInfoResponse.get("data"));
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("nickname", userData.get("nickname"));
        account.put("avatarUrl", userData.get("avatar"));
        account.put("rawUserInfo", userInfoResponse);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", 1);
        payload.put("authType", DouyinAuthConstants.OAUTH_AUTH_TYPE);
        payload.put("source", "mock".equals(session.providerMode()) ? "MOCK_OAUTH" : "OFFICIAL_WEB_OAUTH");
        payload.put("providerMode", session.providerMode());
        payload.put("authorizationCode", code);
        payload.put("callbackParameters", callbackParameters);
        payload.put("accessToken", accessToken);
        payload.put("refreshToken", refreshToken);
        payload.put("openId", openId);
        payload.put("unionId", Optional.ofNullable(string(tokenData.get("union_id")))
                .orElse(string(userData.get("union_id"))));
        payload.put("scope", scope);
        payload.put("expiresAt", text(expiresAt));
        payload.put("refreshExpiresAt", text(refreshExpiresAt));
        payload.put("account", account);
        payload.put("rawTokenResponse", tokenResponse);
        payload.put("authorizedAt", authorizedAt.toString());
        payload.put("lastRefreshedAt", null);
        return payload;
    }

    private Map<String, Object> fetchUserInfoWhenAuthorized(
            List<String> scope,
            String accessToken,
            String openId
    ) {
        if (openId == null || openId.isBlank() || !scope.contains("user_info")) {
            return Map.of();
        }
        return client.fetchUserInfo(accessToken, openId);
    }

    private String liveAuthorizationUrl(String state) {
        return AUTHORIZATION_URL
                + "?client_key=" + encodeQueryValue(properties.oauthClientKey())
                + "&response_type=code"
                + "&scope=" + encodeQueryValue(properties.oauthScope())
                + "&redirect_uri=" + encodeQueryValue(properties.oauthRedirectUri())
                + "&state=" + encodeQueryValue(state);
    }

    private String mockAuthorizationUrl(UUID loginId, String state) {
        return UriComponentsBuilder.fromPath("/api/douyin/auth/oauth/mock/authorize")
                .queryParam("loginId", loginId)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    private Map<String, Object> mockTokenResponse(UUID loginId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("access_token", "mock-access-" + loginId);
        data.put("refresh_token", "mock-refresh-" + loginId);
        data.put("open_id", "mock-open-" + loginId);
        data.put("union_id", "mock-union-" + loginId);
        data.put("scope", properties.oauthScope());
        data.put("expires_in", 1296000);
        data.put("refresh_expires_in", 2592000);
        data.put("error_code", 0);
        data.put("provider_extra", "preserved-mock-token-field");
        return Map.of("data", data, "message", "success");
    }

    private Map<String, Object> mockRefreshResponse(DouyinStoredCredential active) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("access_token", "mock-refreshed-access-" + active.credentialId());
        data.put("refresh_token", active.payload().get("refreshToken"));
        data.put("open_id", active.payload().get("openId"));
        data.put("scope", active.payload().get("scope"));
        data.put("expires_in", 1296000);
        data.put("refresh_expires_in", 2592000);
        data.put("error_code", 0);
        return Map.of("data", data, "message", "success");
    }

    private Map<String, Object> mockUserInfoResponse(String openId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("open_id", openId);
        data.put("union_id", openId == null ? null : openId.replace("open", "union"));
        data.put("nickname", "Douyin Mock User");
        data.put("avatar", "https://example.test/mock-avatar.png");
        data.put("provider_extra", "preserved-mock-user-field");
        return Map.of("data", data, "err_no", 0, "err_msg", "");
    }

    private Map<String, Object> successfulData(Map<String, Object> response, String message) {
        Map<String, Object> data = objectMap(response.get("data"));
        long errorCode = number(data.get("error_code"), number(response.get("err_no"), 0));
        if (errorCode != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message + " Raw response: " + response);
        }
        return data.isEmpty() ? response : data;
    }

    private void requireSessionState(DouyinAuthSession session, String callbackState) {
        if (callbackState == null || !callbackState.equals(session.state())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Douyin OAuth state does not match the session.");
        }
    }

    private void requireNotExpired(DouyinAuthSession session) {
        if (!session.expiresAt().isAfter(now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Douyin OAuth session has expired.");
        }
    }

    private void requireLiveConfiguration() {
        if (isBlank(properties.oauthClientKey())
                || isBlank(properties.oauthClientSecret())
                || isBlank(properties.oauthRedirectUri())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Douyin live OAuth requires client key, client secret and redirect URI.");
        }
    }

    private String mode() {
        String value = properties.oauthMode() == null
                ? "disabled"
                : properties.oauthMode().trim().toLowerCase(Locale.ROOT);
        if (!List.of("disabled", "mock", "live").contains(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported Douyin OAuth mode: " + value);
        }
        return value;
    }

    private String newState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Map<String, Object> rawCallback(Map<String, List<String>> callbackParameters) {
        return Map.of("callbackParameters", callbackParameters == null ? Map.of() : callbackParameters);
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<String> scope(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String item : String.valueOf(value).split(",")) {
            if (!item.isBlank()) {
                values.add(item.trim());
            }
        }
        return List.copyOf(values);
    }

    private String first(Map<String, List<String>> values, String key) {
        if (values == null || values.get(key) == null || values.get(key).isEmpty()) {
            return null;
        }
        return values.get(key).get(0);
    }

    private String requiredString(Map<String, Object> values, String key, String message) {
        String value = string(values.get(key));
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
        return value;
    }

    private OffsetDateTime plusSeconds(OffsetDateTime base, Object seconds) {
        long value = number(seconds, 0);
        return value <= 0 ? null : base.plusSeconds(value);
    }

    private long number(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String text(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
