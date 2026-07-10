package com.socialmonitor.bilibili.live.danmaku.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.live.config.BilibiliLiveMonitorProperties;
import com.socialmonitor.bilibili.live.danmaku.config.BilibiliLiveDanmakuProperties;
import com.socialmonitor.platform.enums.FetchErrorType;
import com.socialmonitor.platform.enums.RiskLevel;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class BilibiliAnonymousCookieProvider {

    private static final String ENDPOINT = "x/frontend/finger/spi";
    private static final String URL = "https://api.bilibili.com/x/frontend/finger/spi";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final BilibiliLiveDanmakuProperties danmakuProperties;

    private volatile BuvidCookie cachedCookie;

    public BilibiliAnonymousCookieProvider(
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

    public BuvidCookie getCookie() {
        BuvidCookie current = cachedCookie;
        if (current != null && current.expiresAt().isAfter(Instant.now())) {
            return current;
        }
        synchronized (this) {
            current = cachedCookie;
            if (current != null && current.expiresAt().isAfter(Instant.now())) {
                return current;
            }
            cachedCookie = fetchCookie();
            return cachedCookie;
        }
    }

    public void clear() {
        cachedCookie = null;
    }

    private BuvidCookie fetchCookie() {
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
                    "Bilibili buvid request failed with HTTP " + exception.getStatusCode().value(),
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
                    "Network error requesting Bilibili buvid: " + exception.getMessage(),
                    null
            );
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            int code = root.path("code").asInt(Integer.MIN_VALUE);
            if (code != 0) {
                throw new BilibiliFetchException(
                        FetchErrorType.UNKNOWN,
                        true,
                        RiskLevel.LOW,
                        ENDPOINT,
                        200,
                        code,
                        "Bilibili buvid API returned code " + code,
                        raw
                );
            }
            String buvid3 = root.path("data").path("b_3").asText(null);
            String buvid4 = root.path("data").path("b_4").asText(null);
            if (buvid3 == null || buvid3.isBlank()) {
                throw new BilibiliFetchException(
                        FetchErrorType.PARSE_ERROR,
                        true,
                        RiskLevel.LOW,
                        ENDPOINT,
                        200,
                        null,
                        "Bilibili buvid response missing data.b_3",
                        raw
                );
            }
            return new BuvidCookie(
                    buvid3,
                    buvid4,
                    Instant.now().plusSeconds(Math.max(300, danmakuProperties.getBuvidCacheSeconds()))
            );
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
                    "Unable to parse Bilibili buvid response: " + exception.getMessage(),
                    raw
            );
        }
    }

    private SimpleClientHttpRequestFactory requestFactory(BilibiliLiveMonitorProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return factory;
    }

    public record BuvidCookie(String buvid3, String buvid4, Instant expiresAt) {

        public String toCookieHeader() {
            return "buvid3=" + buvid3 + (buvid4 == null || buvid4.isBlank() ? "" : "; buvid4=" + buvid4);
        }
    }
}
