package com.socialmonitor.bilibili.live.danmaku.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.auth.domain.BilibiliCookieState;
import com.socialmonitor.bilibili.auth.domain.PersistedBilibiliCredential;
import com.socialmonitor.bilibili.auth.repository.BilibiliCredentialRepository;
import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.live.config.BilibiliLiveMonitorProperties;
import com.socialmonitor.bilibili.live.danmaku.config.BilibiliLiveDanmakuProperties;
import com.socialmonitor.platform.enums.FetchErrorType;
import com.socialmonitor.platform.enums.RiskLevel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BilibiliLiveDanmuInfoClient {

    public static final String ENDPOINT = "xlive/web-room/v1/index/getDanmuInfo";
    private static final String URL = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo";
    private static final Logger log = LoggerFactory.getLogger(BilibiliLiveDanmuInfoClient.class);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final BilibiliWbiSigner wbiSigner;
    private final BilibiliAnonymousCookieProvider cookieProvider;
    private final BilibiliLiveDanmakuProperties danmakuProperties;
    private final ObjectProvider<BilibiliCredentialRepository> credentialRepositoryProvider;

    public BilibiliLiveDanmuInfoClient(
            ObjectMapper objectMapper,
            BilibiliLiveMonitorProperties liveProperties,
            BilibiliLiveDanmakuProperties danmakuProperties,
            BilibiliWbiSigner wbiSigner,
            BilibiliAnonymousCookieProvider cookieProvider,
            ObjectProvider<BilibiliCredentialRepository> credentialRepositoryProvider
    ) {
        this.objectMapper = objectMapper;
        this.danmakuProperties = danmakuProperties;
        this.wbiSigner = wbiSigner;
        this.cookieProvider = cookieProvider;
        this.credentialRepositoryProvider = credentialRepositoryProvider;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(liveProperties))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, liveProperties.getUserAgent())
                .defaultHeader(HttpHeaders.REFERER, liveProperties.getReferer())
                .build();
    }

    public DanmuInfo fetchDanmuInfo(Long roomId) {
        try {
            return fetchDanmuInfo(roomId, false);
        } catch (BilibiliFetchException exception) {
            if (exception.errorType() == FetchErrorType.RISK_CONTROL || Integer.valueOf(-352).equals(exception.biliCode())) {
                wbiSigner.clear();
                cookieProvider.clear();
                return fetchDanmuInfo(roomId, true);
            }
            throw exception;
        }
    }

    private DanmuInfo fetchDanmuInfo(Long roomId, boolean forceRefresh) {
        Optional<RequestCredential> loginCredential = loginCredential();
        if (loginCredential.isPresent()) {
            try {
                return fetchDanmuInfo(roomId, forceRefresh, loginCredential.get());
            } catch (BilibiliFetchException exception) {
                if (!shouldFallbackToAnonymous(exception)) {
                    throw exception;
                }
                log.warn("Bilibili getDanmuInfo login credential failed, fallback to anonymous. roomId={}, uid={}, errorType={}, biliCode={}",
                        roomId, loginCredential.get().authUid(), exception.errorType(), exception.biliCode());
                if (exception.errorType() == FetchErrorType.RISK_CONTROL || Integer.valueOf(-352).equals(exception.biliCode())) {
                    wbiSigner.clear();
                    cookieProvider.clear();
                }
                return fetchDanmuInfo(roomId, true, anonymousCredential());
            }
        }
        return fetchDanmuInfo(roomId, forceRefresh, anonymousCredential());
    }

    private DanmuInfo fetchDanmuInfo(Long roomId, boolean forceRefresh, RequestCredential credential) {
        BilibiliAnonymousCookieProvider.BuvidCookie cookie = cookieProvider.getCookie();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("id", String.valueOf(roomId));
        params.put("type", "0");
        params.put("web_location", danmakuProperties.getWebLocation());
        Map<String, String> signed = wbiSigner.sign(params, forceRefresh);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(URL);
        signed.forEach(builder::queryParam);
        String raw;
        try {
            raw = restClient.get()
                    .uri(builder.build(true).toUri())
                    .header(HttpHeaders.COOKIE, mergeCookieHeaders(credential.cookieHeader(), cookie.toCookieHeader()))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw new BilibiliFetchException(
                    exception.getStatusCode().value() == 429 ? FetchErrorType.RATE_LIMITED : FetchErrorType.UNKNOWN,
                    true,
                    RiskLevel.MEDIUM,
                    ENDPOINT,
                    exception.getStatusCode().value(),
                    null,
                    "Bilibili getDanmuInfo failed with HTTP " + exception.getStatusCode().value(),
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
                    "Network error requesting Bilibili getDanmuInfo: " + exception.getMessage(),
                    null
            );
        }
        return parse(raw, roomId, credential.withBuvid(cookie.buvid3()));
    }

    private DanmuInfo parse(String raw, Long roomId, RequestCredential credential) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            int code = root.path("code").asInt(Integer.MIN_VALUE);
            if (code != 0) {
                FetchErrorType errorType = switch (code) {
                    case -101, 65530 -> FetchErrorType.AUTH_EXPIRED;
                    case -352, -403, -412 -> FetchErrorType.RISK_CONTROL;
                    default -> FetchErrorType.UNKNOWN;
                };
                throw new BilibiliFetchException(
                        errorType,
                        errorType != FetchErrorType.AUTH_EXPIRED,
                        errorType == FetchErrorType.RISK_CONTROL ? RiskLevel.HIGH : RiskLevel.MEDIUM,
                        ENDPOINT,
                        200,
                        code,
                        root.path("message").asText("Bilibili getDanmuInfo returned code " + code),
                        raw
                );
            }
            JsonNode data = root.path("data");
            String token = data.path("token").asText(null);
            if (token == null || token.isBlank()) {
                throw new BilibiliFetchException(
                        FetchErrorType.PARSE_ERROR,
                        true,
                        RiskLevel.MEDIUM,
                        ENDPOINT,
                        200,
                        null,
                        "Bilibili getDanmuInfo response missing token",
                        raw
                );
            }
            List<DanmuHost> hosts = new ArrayList<>();
            for (JsonNode hostNode : data.path("host_list")) {
                String host = hostNode.path("host").asText(null);
                if (host == null || host.isBlank()) {
                    continue;
                }
                hosts.add(new DanmuHost(
                        host,
                        hostNode.path("port").asInt(2243),
                        hostNode.path("wss_port").asInt(2245),
                        hostNode.path("ws_port").asInt(2244)
                ));
            }
            if (hosts.isEmpty()) {
                hosts.add(new DanmuHost("broadcastlv.chat.bilibili.com", 2243, 2245, 2244));
            }
            return new DanmuInfo(
                    roomId,
                    token,
                    credential.buvid3(),
                    credential.authUid(),
                    credential.authenticated(),
                    credential.authMode(),
                    hosts
            );
        } catch (BilibiliFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BilibiliFetchException(
                    FetchErrorType.PARSE_ERROR,
                    true,
                    RiskLevel.MEDIUM,
                    ENDPOINT,
                    200,
                    null,
                    "Unable to parse Bilibili getDanmuInfo response: " + exception.getMessage(),
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

    private Optional<RequestCredential> loginCredential() {
        if (!danmakuProperties.isUseLoginCredential()) {
            return Optional.empty();
        }
        BilibiliCredentialRepository repository = credentialRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return Optional.empty();
        }
        return repository.findActive()
                .filter(credential -> "ACTIVE".equalsIgnoreCase(credential.status()))
                .map(PersistedBilibiliCredential::state)
                .flatMap(this::toLoginCredential);
    }

    private Optional<RequestCredential> toLoginCredential(BilibiliCookieState state) {
        String cookieHeader = state.cookieHeader();
        Long authUid = credentialUid(state);
        if (cookieHeader == null || cookieHeader.isBlank() || authUid == null || authUid <= 0) {
            return Optional.empty();
        }
        return Optional.of(new RequestCredential(authUid, cookieHeader, null, "LOGIN"));
    }

    private RequestCredential anonymousCredential() {
        return new RequestCredential(0L, "", null, "ANONYMOUS");
    }

    private Long credentialUid(BilibiliCookieState state) {
        if (state.account() != null && state.account().mid() != null) {
            return state.account().mid();
        }
        String dedeUserId = state.cookieValue("DedeUserID");
        if (dedeUserId == null || dedeUserId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(dedeUserId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean shouldFallbackToAnonymous(BilibiliFetchException exception) {
        return exception.errorType() == FetchErrorType.AUTH_EXPIRED
                || exception.errorType() == FetchErrorType.RISK_CONTROL
                || Integer.valueOf(65530).equals(exception.biliCode())
                || Integer.valueOf(-352).equals(exception.biliCode())
                || Integer.valueOf(-403).equals(exception.biliCode())
                || Integer.valueOf(-412).equals(exception.biliCode());
    }

    private String mergeCookieHeaders(String primary, String secondary) {
        if (primary == null || primary.isBlank()) {
            return secondary == null ? "" : secondary;
        }
        if (secondary == null || secondary.isBlank()) {
            return primary;
        }
        return primary + "; " + secondary;
    }

    public record DanmuInfo(
            Long roomId,
            String token,
            String buvid,
            Long authUid,
            boolean authenticated,
            String authMode,
            List<DanmuHost> hosts
    ) {
    }

    public record DanmuHost(String host, int port, int wssPort, int wsPort) {
        public String defaultWssUri() {
            return "wss://" + host + "/sub";
        }

        public String portWssUri() {
            return "wss://" + host + ":" + wssPort + "/sub";
        }
    }

    private record RequestCredential(Long authUid, String cookieHeader, String buvid3, String authMode) {

        private boolean authenticated() {
            return authUid != null && authUid > 0;
        }

        private RequestCredential withBuvid(String buvid3) {
            return new RequestCredential(authUid, cookieHeader, buvid3, authMode);
        }
    }
}
