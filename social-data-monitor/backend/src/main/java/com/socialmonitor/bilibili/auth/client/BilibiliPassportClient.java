package com.socialmonitor.bilibili.auth.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.auth.config.BilibiliAuthProperties;
import com.socialmonitor.bilibili.auth.domain.BilibiliAccount;
import com.socialmonitor.bilibili.auth.domain.BilibiliAuthConstants;
import com.socialmonitor.bilibili.auth.domain.BilibiliCookie;
import com.socialmonitor.bilibili.auth.domain.BilibiliCookieState;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BilibiliPassportClient {

    private static final ZoneOffset DISPLAY_OFFSET = ZoneOffset.ofHours(8);
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final BilibiliAuthProperties properties;
    private final ObjectMapper objectMapper;

    public BilibiliPassportClient(BilibiliAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public CookieManager newCookieManager() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return cookieManager;
    }

    public QrGenerateResult generateQrCode(CookieManager cookieManager) {
        JsonNode root = sendJson(
                client(cookieManager),
                request("https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header")
                        .GET()
                        .build()
        );
        ensureSuccess(root, "Bilibili QR generate failed.");
        JsonNode data = root.path("data");
        String url = text(data, "url");
        String qrcodeKey = text(data, "qrcode_key");
        if (url == null || qrcodeKey == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Bilibili QR generate response missing url or qrcode_key.");
        }
        return new QrGenerateResult(url, qrcodeKey);
    }

    public QrPollResult pollQrCode(CookieManager cookieManager, String qrcodeKey) {
        String encodedKey = URLEncoder.encode(qrcodeKey, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(
                client(cookieManager),
                request("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=" + encodedKey + "&source=main-fe-header")
                        .GET()
                        .build()
        );
        JsonNode root = readJson(response.body());
        ensureSuccess(root, "Bilibili QR poll failed.");
        JsonNode data = root.path("data");
        int code = data.path("code").asInt(-1);
        String message = data.path("message").asText("");
        String refreshToken = text(data, "refresh_token");
        Long timestamp = data.path("timestamp").isNumber() ? data.path("timestamp").asLong() : null;
        String crossDomainUrl = text(data, "url");
        List<BilibiliCookie> cookies = extractCookies(cookieManager, response, crossDomainUrl);
        return new QrPollResult(code, message, refreshToken, timestamp, cookies);
    }

    public NavResult fetchNav(BilibiliCookieState state) {
        if (state == null || state.cookieHeader().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Bilibili credential cookie is empty.");
        }
        HttpRequest request = request("https://api.bilibili.com/x/web-interface/nav")
                .header("Cookie", state.cookieHeader())
                .header("Referer", properties.getReferer())
                .GET()
                .build();
        JsonNode root = sendJson(HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getConnectTimeoutMs())))
                .build(), request);
        int code = root.path("code").asInt(-1);
        if (code != 0 || !root.path("data").path("isLogin").asBoolean(false)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Bilibili credential is not logged in.");
        }
        JsonNode data = root.path("data");
        BilibiliAccount account = new BilibiliAccount(
                data.path("mid").isNumber() ? data.path("mid").asLong() : null,
                text(data, "uname"),
                text(data, "face"),
                data.path("level_info").path("current_level").isNumber() ? data.path("level_info").path("current_level").asInt() : null,
                data.path("vipStatus").isNumber() ? data.path("vipStatus").asInt() : null
        );
        return new NavResult(account, objectMapper.convertValue(root, OBJECT_MAP));
    }

    private HttpClient client(CookieManager cookieManager) {
        return HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getConnectTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(1000, properties.getRequestTimeoutMs())))
                .header("User-Agent", properties.getUserAgent())
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", properties.getReferer());
    }

    private JsonNode sendJson(HttpClient client, HttpRequest request) {
        return readJson(send(client, request).body());
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Bilibili request failed with HTTP " + response.statusCode());
            }
            return response;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Bilibili request failed: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Bilibili request interrupted.");
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Failed to parse Bilibili response: " + exception.getMessage());
        }
    }

    private void ensureSuccess(JsonNode root, String message) {
        if (root.path("code").asInt(-1) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message + " code=" + root.path("code").asText() + ", message=" + root.path("message").asText());
        }
    }

    private List<BilibiliCookie> extractCookies(CookieManager cookieManager, HttpResponse<String> response, String crossDomainUrl) {
        Map<String, BilibiliCookie> cookies = new LinkedHashMap<>();
        OffsetDateTime now = OffsetDateTime.now(DISPLAY_OFFSET);
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            addCookie(cookies, fromHttpCookie(cookie, now));
        }
        for (String header : response.headers().allValues("set-cookie")) {
            try {
                for (HttpCookie cookie : HttpCookie.parse(header)) {
                    addCookie(cookies, fromHttpCookie(cookie, now));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        parseCrossDomainCookies(crossDomainUrl, cookies);
        return new ArrayList<>(cookies.values());
    }

    private BilibiliCookie fromHttpCookie(HttpCookie cookie, OffsetDateTime now) {
        OffsetDateTime expiresAt = cookie.getMaxAge() >= 0
                ? now.plusSeconds(cookie.getMaxAge())
                : null;
        return new BilibiliCookie(
                cookie.getName(),
                cookie.getValue(),
                cookie.getDomain(),
                cookie.getPath(),
                expiresAt,
                cookie.isHttpOnly(),
                cookie.getSecure(),
                null
        );
    }

    private void parseCrossDomainCookies(String crossDomainUrl, Map<String, BilibiliCookie> cookies) {
        if (crossDomainUrl == null || crossDomainUrl.isBlank()) {
            return;
        }
        URI uri = URI.create(crossDomainUrl);
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int index = pair.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = java.net.URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8);
            String value = java.net.URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
        OffsetDateTime expiresAt = null;
        if (values.containsKey("Expires")) {
            try {
                expiresAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(values.get("Expires"))), DISPLAY_OFFSET);
            } catch (NumberFormatException ignored) {
            }
        }
        for (String name : BilibiliAuthConstants.COOKIE_ORDER) {
            String value = values.get(name);
            if (value != null && !value.isBlank() && !cookies.containsKey(name)) {
                addCookie(cookies, new BilibiliCookie(name, value, ".bilibili.com", "/", expiresAt, "SESSDATA".equals(name), true, null));
            }
        }
    }

    private void addCookie(Map<String, BilibiliCookie> cookies, BilibiliCookie cookie) {
        if (cookie == null || !BilibiliAuthConstants.COOKIE_ORDER.contains(cookie.name())) {
            return;
        }
        if (cookie.value() == null || cookie.value().isBlank()) {
            return;
        }
        cookies.put(cookie.name(), cookie);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    public record QrGenerateResult(String qrUrl, String qrcodeKey) {
    }

    public record QrPollResult(
            int code,
            String message,
            String refreshToken,
            Long timestamp,
            List<BilibiliCookie> cookies
    ) {
    }

    public record NavResult(BilibiliAccount account, Map<String, Object> rawPayload) {
    }
}
