package com.socialmonitor.bilibili.live.rank.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.live.config.BilibiliLiveMonitorProperties;
import com.socialmonitor.bilibili.live.danmaku.client.BilibiliWbiSigner;
import com.socialmonitor.bilibili.live.rank.config.BilibiliLiveRankProperties;
import com.socialmonitor.bilibili.live.rank.domain.BilibiliLiveRankFetchedEntry;
import com.socialmonitor.bilibili.live.rank.domain.BilibiliLiveRankFetchedSnapshot;
import com.socialmonitor.platform.enums.FetchErrorType;
import com.socialmonitor.platform.enums.RiskLevel;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = {"storage-enabled", "rank.enabled"}, matchIfMissing = true)
public class BilibiliLiveRankApiClient {

    public static final String AUDIENCE_RANK_ENDPOINT = "xlive/general-interface/v1/rank/queryContributionRank";
    public static final String ONLINE_GOLD_RANK_ENDPOINT = "xlive/general-interface/v1/rank/getOnlineGoldRank";
    public static final String GUARD_RANK_ENDPOINT = "xlive/app-room/v2/guardTab/topListNew";

    private static final String AUDIENCE_RANK_URL = "https://api.live.bilibili.com/xlive/general-interface/v1/rank/queryContributionRank";
    private static final String ONLINE_GOLD_RANK_URL = "https://api.live.bilibili.com/xlive/general-interface/v1/rank/getOnlineGoldRank";
    private static final String GUARD_RANK_URL = "https://api.live.bilibili.com/xlive/app-room/v2/guardTab/topListNew";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final BilibiliWbiSigner wbiSigner;
    private final BilibiliLiveRankProperties rankProperties;

    public BilibiliLiveRankApiClient(
            ObjectMapper objectMapper,
            BilibiliLiveMonitorProperties liveProperties,
            BilibiliLiveRankProperties rankProperties,
            BilibiliWbiSigner wbiSigner
    ) {
        this.objectMapper = objectMapper;
        this.rankProperties = rankProperties;
        this.wbiSigner = wbiSigner;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(liveProperties))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, liveProperties.getUserAgent())
                .defaultHeader(HttpHeaders.REFERER, liveProperties.getReferer())
                .build();
    }

    public BilibiliLiveRankFetchedSnapshot fetchAudienceRank(
            long roomId,
            long ruid,
            String rankType,
            String rankSwitch,
            String periodScope,
            int page,
            int pageSize,
            boolean forceRefreshWbi
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("ruid", String.valueOf(ruid));
        params.put("room_id", String.valueOf(roomId));
        params.put("page", String.valueOf(page));
        params.put("page_size", String.valueOf(pageSize));
        params.put("type", rankType);
        params.put("switch", rankSwitch);
        params.put("platform", "web");
        params.put("web_location", rankProperties.getWebLocation());
        Map<String, String> signedParams = wbiSigner.sign(params, forceRefreshWbi);
        String raw = get(uri(AUDIENCE_RANK_URL, signedParams), AUDIENCE_RANK_ENDPOINT);
        return parseAudienceRank(raw, rankType, rankSwitch, periodScope, page, pageSize, AUDIENCE_RANK_ENDPOINT, true);
    }

    public BilibiliLiveRankFetchedSnapshot fetchOnlineGoldRankFallback(
            long roomId,
            long ruid,
            int page,
            int pageSize
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("roomId", String.valueOf(roomId));
        params.put("ruid", String.valueOf(ruid));
        params.put("page", String.valueOf(page));
        params.put("pageSize", String.valueOf(pageSize));
        String raw = get(uri(ONLINE_GOLD_RANK_URL, params), ONLINE_GOLD_RANK_ENDPOINT);
        return parseOnlineGoldRank(raw, page, pageSize);
    }

    public BilibiliLiveRankFetchedSnapshot fetchGuardRank(
            long roomId,
            long ruid,
            String rankType,
            String periodScope,
            int typ,
            int page,
            int pageSize
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("roomid", String.valueOf(roomId));
        params.put("ruid", String.valueOf(ruid));
        params.put("page", String.valueOf(page));
        params.put("page_size", String.valueOf(pageSize));
        params.put("typ", String.valueOf(typ));
        String raw = get(uri(GUARD_RANK_URL, params), GUARD_RANK_ENDPOINT);
        return parseGuardRank(raw, rankType, periodScope, page, pageSize);
    }

    private BilibiliLiveRankFetchedSnapshot parseAudienceRank(
            String raw,
            String rankType,
            String rankSwitch,
            String periodScope,
            int page,
            int pageSize,
            String endpoint,
            boolean signedRequired
    ) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            ensureSuccess(root, endpoint, raw);
            JsonNode data = root.path("data");
            JsonNode config = data.path("config");
            List<BilibiliLiveRankFetchedEntry> entries = new ArrayList<>();
            for (JsonNode item : arrayItems(data.path("item"))) {
                entries.add(parseAudienceEntry(item, "LIST"));
            }
            return new BilibiliLiveRankFetchedSnapshot(
                    "AUDIENCE",
                    rankType,
                    rankSwitch,
                    periodScope,
                    page,
                    pageSize,
                    parseLong(data.path("count")).orElse(null),
                    optionalText(data.path("count_text")).orElse(null),
                    optionalText(config.path("value_text")).orElse(null),
                    optionalText(config.path("title")).orElse(null),
                    endpoint,
                    signedRequired,
                    OffsetDateTime.now(),
                    raw,
                    entries
            );
        } catch (BilibiliFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parseError(endpoint, "Unable to parse Bilibili audience rank response: " + exception.getMessage(), raw);
        }
    }

    private BilibiliLiveRankFetchedSnapshot parseOnlineGoldRank(String raw, int page, int pageSize) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            ensureSuccess(root, ONLINE_GOLD_RANK_ENDPOINT, raw);
            JsonNode data = root.path("data");
            List<BilibiliLiveRankFetchedEntry> entries = new ArrayList<>();
            for (JsonNode item : arrayItems(data.path("OnlineRankItem"))) {
                entries.add(parseAudienceEntry(item, "LIST"));
            }
            return new BilibiliLiveRankFetchedSnapshot(
                    "AUDIENCE",
                    "online_rank",
                    "contribution_rank",
                    "REALTIME",
                    page,
                    pageSize,
                    parseLong(data.path("onlineNum")).orElse(null),
                    optionalText(data.path("onlineNumText")).orElse(null),
                    "贡献值",
                    "在线贡献榜备用接口",
                    ONLINE_GOLD_RANK_ENDPOINT,
                    false,
                    OffsetDateTime.now(),
                    raw,
                    entries
            );
        } catch (BilibiliFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parseError(ONLINE_GOLD_RANK_ENDPOINT, "Unable to parse Bilibili online gold rank response: " + exception.getMessage(), raw);
        }
    }

    private BilibiliLiveRankFetchedSnapshot parseGuardRank(
            String raw,
            String rankType,
            String periodScope,
            int page,
            int pageSize
    ) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            ensureSuccess(root, GUARD_RANK_ENDPOINT, raw);
            JsonNode data = root.path("data");
            List<BilibiliLiveRankFetchedEntry> entries = new ArrayList<>();
            for (JsonNode item : arrayItems(data.path("top3"))) {
                entries.add(parseGuardEntry(item, "TOP3"));
            }
            for (JsonNode item : arrayItems(data.path("list"))) {
                entries.add(parseGuardEntry(item, "LIST"));
            }
            for (JsonNode item : arrayItems(data.path("extop"))) {
                entries.add(parseGuardEntry(item, "EXTOP"));
            }
            JsonNode info = data.path("info");
            return new BilibiliLiveRankFetchedSnapshot(
                    "GUARD",
                    rankType,
                    null,
                    periodScope,
                    page,
                    pageSize,
                    parseLong(info.path("num")).orElse(null),
                    optionalText(info.path("num_text")).or(() -> parseLong(info.path("num")).map(String::valueOf)).orElse(null),
                    null,
                    optionalText(data.path("remind_msg")).orElse(null),
                    GUARD_RANK_ENDPOINT,
                    false,
                    OffsetDateTime.now(),
                    raw,
                    entries
            );
        } catch (BilibiliFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parseError(GUARD_RANK_ENDPOINT, "Unable to parse Bilibili guard rank response: " + exception.getMessage(), raw);
        }
    }

    private BilibiliLiveRankFetchedEntry parseAudienceEntry(JsonNode item, String entryKind) {
        JsonNode medal = firstPresent(item.path("medal_info"), item.path("medalInfo"), item.path("uinfo").path("medal"));
        JsonNode guard = firstPresent(item.path("uinfo").path("guard"), item.path("guard"));
        return new BilibiliLiveRankFetchedEntry(
                parseLong(item.path("uid")).orElse(null),
                parseInteger(item.path("rank")).orElse(null),
                entryKind,
                optionalText(item.path("name")).or(() -> optionalText(item.path("uname"))).orElse(null),
                optionalText(item.path("face")).orElse(null),
                parseLong(item.path("score")).orElse(null),
                parseInteger(item.path("guard_level")).or(() -> parseInteger(guard.path("level"))).orElse(null),
                parseInteger(item.path("wealth_level")).orElse(null),
                optionalText(medal.path("medal_name")).or(() -> optionalText(medal.path("name"))).orElse(null),
                parseInteger(medal.path("medal_level")).or(() -> parseInteger(medal.path("level"))).orElse(null),
                parseLong(medal.path("ruid")).orElse(null),
                parseInteger(medal.path("is_light")).orElse(null),
                optionalText(guard.path("expired_str")).orElse(null),
                null,
                item.toString()
        );
    }

    private BilibiliLiveRankFetchedEntry parseGuardEntry(JsonNode item, String entryKind) {
        JsonNode uinfo = item.path("uinfo");
        JsonNode base = uinfo.path("base");
        JsonNode medal = uinfo.path("medal");
        JsonNode guard = uinfo.path("guard");
        return new BilibiliLiveRankFetchedEntry(
                parseLong(uinfo.path("uid")).or(() -> parseLong(item.path("uid"))).orElse(null),
                parseInteger(item.path("rank")).orElse(null),
                entryKind,
                optionalText(base.path("name")).or(() -> optionalText(item.path("username"))).orElse(null),
                optionalText(base.path("face")).or(() -> optionalText(item.path("face"))).orElse(null),
                parseLong(item.path("score")).orElse(null),
                parseInteger(guard.path("level")).or(() -> parseInteger(item.path("guard_level"))).orElse(null),
                parseInteger(uinfo.path("wealth").path("level")).orElse(null),
                optionalText(medal.path("name")).or(() -> optionalText(medal.path("medal_name"))).orElse(null),
                parseInteger(medal.path("level")).or(() -> parseInteger(medal.path("medal_level"))).orElse(null),
                parseLong(medal.path("ruid")).orElse(null),
                parseInteger(medal.path("is_light")).orElse(null),
                optionalText(guard.path("expired_str")).orElse(null),
                parseInteger(item.path("accompany")).orElse(null),
                item.toString()
        );
    }

    private String get(URI uri, String endpointKey) {
        try {
            return restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            FetchErrorType errorType = status == 429
                    ? FetchErrorType.RATE_LIMITED
                    : status >= 500 ? FetchErrorType.SERVER_ERROR : FetchErrorType.UNKNOWN;
            boolean retryable = errorType == FetchErrorType.RATE_LIMITED || errorType == FetchErrorType.SERVER_ERROR;
            throw new BilibiliFetchException(
                    errorType,
                    retryable,
                    retryable ? RiskLevel.MEDIUM : RiskLevel.LOW,
                    endpointKey,
                    status,
                    null,
                    "Bilibili live rank HTTP " + status + " from " + endpointKey,
                    exception.getResponseBodyAsString()
            );
        } catch (ResourceAccessException exception) {
            throw new BilibiliFetchException(
                    FetchErrorType.NETWORK_ERROR,
                    true,
                    RiskLevel.LOW,
                    endpointKey,
                    null,
                    null,
                    "Network error calling Bilibili live rank API: " + exception.getMessage(),
                    null
            );
        }
    }

    private void ensureSuccess(JsonNode root, String endpointKey, String raw) {
        int code = root.path("code").asInt(Integer.MIN_VALUE);
        if (code == 0) {
            return;
        }
        FetchErrorType errorType = switch (code) {
            case -101 -> FetchErrorType.AUTH_EXPIRED;
            case -352, -412 -> FetchErrorType.RISK_CONTROL;
            default -> FetchErrorType.UNKNOWN;
        };
        String message = optionalText(root.path("message")).orElse("Bilibili live rank API error: " + code);
        throw new BilibiliFetchException(
                errorType,
                code == -352 || code == -412 || code >= 500 || code == -500,
                errorType == FetchErrorType.RISK_CONTROL ? RiskLevel.HIGH : RiskLevel.LOW,
                endpointKey,
                200,
                code,
                message,
                raw
        );
    }

    private URI uri(String url, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        params.forEach(builder::queryParam);
        return builder.build().encode().toUri();
    }

    private List<JsonNode> arrayItems(JsonNode node) {
        List<JsonNode> items = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(items::add);
        }
        return items;
    }

    private JsonNode firstPresent(JsonNode first, JsonNode second) {
        return isPresent(first) ? first : second;
    }

    private JsonNode firstPresent(JsonNode first, JsonNode second, JsonNode third) {
        if (isPresent(first)) return first;
        if (isPresent(second)) return second;
        return third;
    }

    private boolean isPresent(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull();
    }

    private Optional<String> optionalText(JsonNode node) {
        if (!isPresent(node)) {
            return Optional.empty();
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private Optional<Long> parseLong(JsonNode node) {
        if (!isPresent(node)) {
            return Optional.empty();
        }
        if (node.isNumber()) {
            return Optional.of(node.asLong());
        }
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value.replace(",", "")));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private Optional<Integer> parseInteger(JsonNode node) {
        return parseLong(node).map(Long::intValue);
    }

    private BilibiliFetchException parseError(String endpointKey, String message, String raw) {
        return new BilibiliFetchException(
                FetchErrorType.PARSE_ERROR,
                false,
                RiskLevel.MEDIUM,
                endpointKey,
                200,
                null,
                message,
                raw
        );
    }

    private SimpleClientHttpRequestFactory requestFactory(BilibiliLiveMonitorProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return factory;
    }
}
