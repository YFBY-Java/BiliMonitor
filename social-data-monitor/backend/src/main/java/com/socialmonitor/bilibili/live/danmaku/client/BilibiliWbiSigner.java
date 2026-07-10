package com.socialmonitor.bilibili.live.danmaku.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.live.config.BilibiliLiveMonitorProperties;
import com.socialmonitor.bilibili.live.danmaku.config.BilibiliLiveDanmakuProperties;
import com.socialmonitor.platform.enums.FetchErrorType;
import com.socialmonitor.platform.enums.RiskLevel;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class BilibiliWbiSigner {

    private static final String ENDPOINT = "x/web-interface/nav";
    private static final String URL = "https://api.bilibili.com/x/web-interface/nav";
    private static final Pattern WBI_KEY_PATTERN = Pattern.compile("/([^/]+)\\.png(?:\\?.*)?$");
    private static final int[] MIXIN_KEY_ENC_TAB = {
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    };

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final BilibiliLiveDanmakuProperties danmakuProperties;

    private volatile WbiKeys cachedKeys;

    public BilibiliWbiSigner(
            ObjectMapper objectMapper,
            BilibiliLiveMonitorProperties liveProperties,
            BilibiliLiveDanmakuProperties danmakuProperties
    ) {
        this.objectMapper = objectMapper;
        this.danmakuProperties = danmakuProperties;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(liveProperties))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, liveProperties.getUserAgent())
                .defaultHeader(HttpHeaders.REFERER, "https://www.bilibili.com/")
                .build();
    }

    public Map<String, String> sign(Map<String, String> params) {
        return sign(params, false);
    }

    public Map<String, String> sign(Map<String, String> params, boolean forceRefresh) {
        WbiKeys keys = getKeys(forceRefresh);
        Map<String, String> sorted = new TreeMap<>(params);
        sorted.put("wts", String.valueOf(Instant.now().getEpochSecond()));
        String query = sorted.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(filterValue(entry.getValue())))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        String rid = md5Hex(query + keys.mixinKey());
        Map<String, String> signed = new LinkedHashMap<>(params);
        signed.put("wts", sorted.get("wts"));
        signed.put("w_rid", rid);
        return signed;
    }

    public void clear() {
        cachedKeys = null;
    }

    private WbiKeys getKeys(boolean forceRefresh) {
        WbiKeys current = cachedKeys;
        if (!forceRefresh && current != null && current.expiresAt().isAfter(Instant.now())) {
            return current;
        }
        synchronized (this) {
            current = cachedKeys;
            if (!forceRefresh && current != null && current.expiresAt().isAfter(Instant.now())) {
                return current;
            }
            cachedKeys = fetchKeys();
            return cachedKeys;
        }
    }

    private WbiKeys fetchKeys() {
        String raw;
        try {
            raw = restClient.get().uri(URL).retrieve().body(String.class);
        } catch (RestClientResponseException exception) {
            throw new BilibiliFetchException(
                    FetchErrorType.UNKNOWN,
                    true,
                    RiskLevel.LOW,
                    ENDPOINT,
                    exception.getStatusCode().value(),
                    null,
                    "Bilibili WBI key request failed with HTTP " + exception.getStatusCode().value(),
                    exception.getResponseBodyAsString()
            );
        } catch (ResourceAccessException exception) {
            throw new BilibiliFetchException(
                    FetchErrorType.NETWORK_ERROR,
                    true,
                    RiskLevel.LOW,
                    ENDPOINT,
                    null,
                    null,
                    "Network error requesting Bilibili WBI keys: " + exception.getMessage(),
                    null
            );
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            int code = root.path("code").asInt(Integer.MIN_VALUE);
            if (code != 0 && code != -101) {
                throw new BilibiliFetchException(
                        FetchErrorType.UNKNOWN,
                        true,
                        RiskLevel.LOW,
                        ENDPOINT,
                        200,
                        code,
                        "Bilibili nav API returned code " + code,
                        raw
                );
            }
            JsonNode wbiImg = root.path("data").path("wbi_img");
            String imgKey = extractKey(wbiImg.path("img_url").asText(null));
            String subKey = extractKey(wbiImg.path("sub_url").asText(null));
            if (imgKey == null || subKey == null) {
                throw new BilibiliFetchException(
                        FetchErrorType.PARSE_ERROR,
                        true,
                        RiskLevel.LOW,
                        ENDPOINT,
                        200,
                        null,
                        "Bilibili WBI response missing img_key/sub_key",
                        raw
                );
            }
            return new WbiKeys(imgKey, subKey, mixinKey(imgKey + subKey),
                    Instant.now().plusSeconds(Math.max(300, danmakuProperties.getWbiCacheSeconds())));
        } catch (BilibiliFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BilibiliFetchException(
                    FetchErrorType.PARSE_ERROR,
                    true,
                    RiskLevel.LOW,
                    ENDPOINT,
                    200,
                    null,
                    "Unable to parse Bilibili WBI key response: " + exception.getMessage(),
                    raw
            );
        }
    }

    private String mixinKey(String rawKey) {
        StringBuilder builder = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            builder.append(rawKey.charAt(MIXIN_KEY_ENC_TAB[i]));
        }
        return builder.toString();
    }

    private String extractKey(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher matcher = WBI_KEY_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String filterValue(String value) {
        return value == null ? "" : value.replaceAll("[!'()*]", "");
    }

    private String md5Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b & 0xff));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate WBI MD5", exception);
        }
    }

    private SimpleClientHttpRequestFactory requestFactory(BilibiliLiveMonitorProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return factory;
    }

    private record WbiKeys(String imgKey, String subKey, String mixinKey, Instant expiresAt) {
    }
}
