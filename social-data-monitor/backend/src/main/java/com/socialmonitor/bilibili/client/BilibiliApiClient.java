package com.socialmonitor.bilibili.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.config.BilibiliFollowerMonitorProperties;
import com.socialmonitor.bilibili.domain.BilibiliFetchedUserSnapshot;
import com.socialmonitor.bilibili.domain.BilibiliMonitoredUser;
import com.socialmonitor.platform.enums.FetchErrorType;
import com.socialmonitor.platform.enums.RiskLevel;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BilibiliApiClient {

    public static final String CARD_ENDPOINT = "x/web-interface/card";
    public static final String RELATION_STAT_ENDPOINT = "x/relation/stat";

    private static final String CARD_URL = "https://api.bilibili.com/x/web-interface/card";
    private static final String RELATION_STAT_URL = "https://api.bilibili.com/x/relation/stat";

    private final ObjectMapper objectMapper;
    private final BilibiliFollowerMonitorProperties properties;
    private final RestClient restClient;

    public BilibiliApiClient(ObjectMapper objectMapper, BilibiliFollowerMonitorProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(properties))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .defaultHeader(HttpHeaders.REFERER, properties.getReferer())
                .build();
    }

    public BilibiliFetchedUserSnapshot fetchUserCard(Long mid) {
        URI uri = UriComponentsBuilder.fromHttpUrl(CARD_URL)
                .queryParam("mid", mid)
                .queryParam("photo", "true")
                .build(true)
                .toUri();
        String raw = get(uri, CARD_ENDPOINT);
        return parseCardResponse(raw, OffsetDateTime.now());
    }

    public BilibiliFetchedUserSnapshot fetchRelationStat(BilibiliMonitoredUser existingUser) {
        URI uri = UriComponentsBuilder.fromHttpUrl(RELATION_STAT_URL)
                .queryParam("vmid", existingUser.mid())
                .build(true)
                .toUri();
        String raw = get(uri, RELATION_STAT_ENDPOINT);
        BilibiliFetchedUserSnapshot relationSnapshot = parseRelationStatResponse(raw, OffsetDateTime.now());
        return relationSnapshot.withExistingProfile(existingUser);
    }

    BilibiliFetchedUserSnapshot parseCardResponse(String raw, OffsetDateTime fetchedAt) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            ensureSuccess(root, CARD_ENDPOINT, raw);
            JsonNode data = root.path("data");
            JsonNode card = data.path("card");
            Long mid = parseLong(card.path("mid")).orElseThrow(() -> parseError(CARD_ENDPOINT, "Missing card.mid", raw));
            String nickname = requiredText(CARD_ENDPOINT, card.path("name"), "card.name", raw);
            String avatarUrl = optionalText(card.path("face")).orElse(null);
            Long followerCount = parseLong(data.path("follower"))
                    .or(() -> parseLong(card.path("fans")))
                    .orElseThrow(() -> parseError(CARD_ENDPOINT, "Missing follower count in data.follower/card.fans", raw));
            Long followingCount = parseLong(card.path("attention"))
                    .or(() -> parseLong(card.path("friend")))
                    .orElse(null);

            return new BilibiliFetchedUserSnapshot(
                    mid,
                    nickname,
                    avatarUrl,
                    "https://space.bilibili.com/" + mid,
                    followerCount,
                    followingCount,
                    fetchedAt,
                    CARD_ENDPOINT,
                    raw
            );
        } catch (BilibiliFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parseError(CARD_ENDPOINT, "Unable to parse Bilibili card response: " + exception.getMessage(), raw);
        }
    }

    private BilibiliFetchedUserSnapshot parseRelationStatResponse(String raw, OffsetDateTime fetchedAt) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            ensureSuccess(root, RELATION_STAT_ENDPOINT, raw);
            JsonNode data = root.path("data");
            Long mid = parseLong(data.path("mid")).orElseThrow(() -> parseError(RELATION_STAT_ENDPOINT, "Missing data.mid", raw));
            Long followerCount = parseLong(data.path("follower"))
                    .orElseThrow(() -> parseError(RELATION_STAT_ENDPOINT, "Missing data.follower", raw));
            Long followingCount = parseLong(data.path("following")).orElse(null);

            return new BilibiliFetchedUserSnapshot(
                    mid,
                    null,
                    null,
                    "https://space.bilibili.com/" + mid,
                    followerCount,
                    followingCount,
                    fetchedAt,
                    RELATION_STAT_ENDPOINT,
                    raw
            );
        } catch (BilibiliFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parseError(RELATION_STAT_ENDPOINT, "Unable to parse Bilibili relation stat response: " + exception.getMessage(), raw);
        }
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
                    "Bilibili HTTP " + status + " from " + endpointKey,
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
                    "Network error calling Bilibili: " + exception.getMessage(),
                    null
            );
        }
    }

    private void ensureSuccess(JsonNode root, String endpointKey, String raw) {
        JsonNode data = root.path("data");
        if (data.hasNonNull("v_voucher")) {
            throw new BilibiliFetchException(
                    FetchErrorType.RISK_CONTROL,
                    false,
                    RiskLevel.HIGH,
                    endpointKey,
                    200,
                    0,
                    "Bilibili returned v_voucher risk-control response.",
                    raw
            );
        }

        int code = root.path("code").asInt(Integer.MIN_VALUE);
        if (code == 0) {
            return;
        }

        FetchErrorType errorType = switch (code) {
            case -101 -> FetchErrorType.AUTH_EXPIRED;
            case -352, -412 -> FetchErrorType.RISK_CONTROL;
            case -404, 40061 -> FetchErrorType.UNKNOWN;
            default -> FetchErrorType.UNKNOWN;
        };
        boolean retryable = code >= 500;
        String message = optionalText(root.path("message")).orElse("Bilibili API error: " + code);
        throw new BilibiliFetchException(
                errorType,
                retryable,
                errorType == FetchErrorType.RISK_CONTROL ? RiskLevel.HIGH : RiskLevel.LOW,
                endpointKey,
                200,
                code,
                message,
                raw
        );
    }

    private Optional<String> optionalText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String requiredText(String endpointKey, JsonNode node, String field, String raw) {
        return optionalText(node).orElseThrow(() -> parseError(endpointKey, "Missing " + field, raw));
    }

    private Optional<Long> parseLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (node.isNumber()) {
            return Optional.of(node.asLong());
        }
        String text = node.asText(null);
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
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

    private SimpleClientHttpRequestFactory requestFactory(BilibiliFollowerMonitorProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return factory;
    }
}
