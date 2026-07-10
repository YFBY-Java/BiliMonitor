package com.socialmonitor.bilibili.live.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.live.config.BilibiliLiveMonitorProperties;
import com.socialmonitor.bilibili.live.domain.BilibiliFetchedLiveRoomSnapshot;
import com.socialmonitor.platform.enums.FetchErrorType;
import com.socialmonitor.platform.enums.RiskLevel;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public class BilibiliLiveApiClient {

    public static final String STATUS_BY_UIDS_ENDPOINT = "room/v1/Room/get_status_info_by_uids";
    public static final String ROOM_INIT_ENDPOINT = "room/v1/Room/room_init";
    public static final String ROOM_INFO_ENDPOINT = "room/v1/Room/get_info";
    public static final String ROOM_INFO_OLD_ENDPOINT = "room/v1/Room/getRoomInfoOld";

    private static final String STATUS_BY_UIDS_URL = "https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids";
    private static final String ROOM_INIT_URL = "https://api.live.bilibili.com/room/v1/Room/room_init";
    private static final String ROOM_INFO_URL = "https://api.live.bilibili.com/room/v1/Room/get_info";
    private static final String ROOM_INFO_OLD_URL = "https://api.live.bilibili.com/room/v1/Room/getRoomInfoOld";
    private static final DateTimeFormatter BILI_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneOffset CHINA_OFFSET = ZoneOffset.ofHours(8);

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public BilibiliLiveApiClient(ObjectMapper objectMapper, BilibiliLiveMonitorProperties properties) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(properties))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .defaultHeader(HttpHeaders.REFERER, properties.getReferer())
                .build();
    }

    public Map<Long, BilibiliFetchedLiveRoomSnapshot> fetchStatusByUids(List<Long> uids) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(STATUS_BY_UIDS_URL);
        uids.forEach(uid -> builder.queryParam("uids[]", uid));
        String raw = get(builder.build().encode().toUri(), STATUS_BY_UIDS_ENDPOINT);
        return parseStatusByUids(raw, OffsetDateTime.now());
    }

    public BilibiliFetchedLiveRoomSnapshot fetchRoomInit(Long roomId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(ROOM_INIT_URL)
                .queryParam("id", roomId)
                .build(true)
                .toUri();
        String raw = get(uri, ROOM_INIT_ENDPOINT);
        return parseRoomInit(raw, OffsetDateTime.now());
    }

    public BilibiliFetchedLiveRoomSnapshot fetchRoomInfo(Long roomId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(ROOM_INFO_URL)
                .queryParam("room_id", roomId)
                .build(true)
                .toUri();
        String raw = get(uri, ROOM_INFO_ENDPOINT);
        return parseRoomInfo(raw, OffsetDateTime.now());
    }

    public Optional<BilibiliFetchedLiveRoomSnapshot> fetchRoomInfoOld(Long uid) {
        URI uri = UriComponentsBuilder.fromHttpUrl(ROOM_INFO_OLD_URL)
                .queryParam("mid", uid)
                .build(true)
                .toUri();
        String raw = get(uri, ROOM_INFO_OLD_ENDPOINT);
        return parseRoomInfoOld(raw, OffsetDateTime.now());
    }

    Map<Long, BilibiliFetchedLiveRoomSnapshot> parseStatusByUids(String raw, OffsetDateTime fetchedAt) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            ensureSuccess(root, STATUS_BY_UIDS_ENDPOINT, raw);
            JsonNode data = root.path("data");
            Map<Long, BilibiliFetchedLiveRoomSnapshot> snapshots = new LinkedHashMap<>();
            data.fields().forEachRemaining(entry -> {
                JsonNode item = entry.getValue();
                parseLong(item.path("uid"))
                        .or(() -> parseLongText(entry.getKey()))
                        .ifPresent(uid -> snapshots.put(uid, parseStatusItem(item, uid, fetchedAt, raw)));
            });
            return snapshots;
        } catch (BilibiliFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parseError(STATUS_BY_UIDS_ENDPOINT, "Unable to parse live status response: " + exception.getMessage(), raw);
        }
    }

    private BilibiliFetchedLiveRoomSnapshot parseStatusItem(JsonNode item, Long uid, OffsetDateTime fetchedAt, String raw) {
        Long roomId = parseLong(item.path("room_id"))
                .orElseThrow(() -> parseError(STATUS_BY_UIDS_ENDPOINT, "Missing room_id for uid=" + uid, raw));
        Integer liveStatus = parseInteger(item.path("live_status")).orElse(0);
        return new BilibiliFetchedLiveRoomSnapshot(
                uid,
                roomId,
                parseLong(item.path("short_id")).orElse(null),
                optionalText(item.path("uname")).orElse("UID " + uid),
                optionalText(item.path("face")).orElse(null),
                optionalText(item.path("title")).orElse(null),
                optionalText(item.path("cover_from_user"))
                        .or(() -> optionalText(item.path("cover")))
                        .orElse(null),
                optionalText(item.path("keyframe")).orElse(null),
                parseLong(item.path("area_v2_id")).or(() -> parseLong(item.path("area_id"))).orElse(null),
                optionalText(item.path("area_v2_name")).or(() -> optionalText(item.path("area_name"))).orElse(null),
                parseLong(item.path("area_v2_parent_id")).or(() -> parseLong(item.path("parent_area_id"))).orElse(null),
                optionalText(item.path("area_v2_parent_name")).or(() -> optionalText(item.path("parent_area_name"))).orElse(null),
                liveStatus,
                parseLiveTime(item.path("live_time")).orElse(null),
                parseLong(item.path("online")).or(() -> parseLong(item.path("watched_show").path("num"))).orElse(null),
                parseLong(item.path("attention")).orElse(null),
                fetchedAt,
                STATUS_BY_UIDS_ENDPOINT,
                raw
        );
    }

    private BilibiliFetchedLiveRoomSnapshot parseRoomInit(String raw, OffsetDateTime fetchedAt) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            ensureSuccess(root, ROOM_INIT_ENDPOINT, raw);
            JsonNode data = root.path("data");
            Long uid = parseLong(data.path("uid"))
                    .orElseThrow(() -> parseError(ROOM_INIT_ENDPOINT, "Missing data.uid", raw));
            Long roomId = parseLong(data.path("room_id"))
                    .orElseThrow(() -> parseError(ROOM_INIT_ENDPOINT, "Missing data.room_id", raw));
            return new BilibiliFetchedLiveRoomSnapshot(
                    uid,
                    roomId,
                    parseLong(data.path("short_id")).orElse(null),
                    "UID " + uid,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    parseInteger(data.path("live_status")).orElse(0),
                    parseLiveTime(data.path("live_time")).orElse(null),
                    null,
                    null,
                    fetchedAt,
                    ROOM_INIT_ENDPOINT,
                    raw
            );
        } catch (BilibiliFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parseError(ROOM_INIT_ENDPOINT, "Unable to parse room_init response: " + exception.getMessage(), raw);
        }
    }

    private BilibiliFetchedLiveRoomSnapshot parseRoomInfo(String raw, OffsetDateTime fetchedAt) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            ensureSuccess(root, ROOM_INFO_ENDPOINT, raw);
            JsonNode data = root.path("data");
            Long uid = parseLong(data.path("uid"))
                    .orElseThrow(() -> parseError(ROOM_INFO_ENDPOINT, "Missing data.uid", raw));
            Long roomId = parseLong(data.path("room_id"))
                    .orElseThrow(() -> parseError(ROOM_INFO_ENDPOINT, "Missing data.room_id", raw));
            return new BilibiliFetchedLiveRoomSnapshot(
                    uid,
                    roomId,
                    parseLong(data.path("short_id")).orElse(null),
                    "UID " + uid,
                    null,
                    optionalText(data.path("title")).orElse(null),
                    optionalText(data.path("user_cover"))
                            .or(() -> optionalText(data.path("cover")))
                            .or(() -> optionalText(data.path("background")))
                            .orElse(null),
                    optionalText(data.path("keyframe")).orElse(null),
                    parseLong(data.path("area_id")).orElse(null),
                    optionalText(data.path("area_name")).orElse(null),
                    parseLong(data.path("parent_area_id")).orElse(null),
                    optionalText(data.path("parent_area_name")).orElse(null),
                    parseInteger(data.path("live_status")).orElse(0),
                    parseLiveTime(data.path("live_time")).orElse(null),
                    parseLong(data.path("online")).orElse(null),
                    parseLong(data.path("attention")).orElse(null),
                    fetchedAt,
                    ROOM_INFO_ENDPOINT,
                    raw
            );
        } catch (BilibiliFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parseError(ROOM_INFO_ENDPOINT, "Unable to parse room info response: " + exception.getMessage(), raw);
        }
    }

    private Optional<BilibiliFetchedLiveRoomSnapshot> parseRoomInfoOld(String raw, OffsetDateTime fetchedAt) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            ensureSuccess(root, ROOM_INFO_OLD_ENDPOINT, raw);
            JsonNode data = root.path("data");
            boolean hasRoom = parseInteger(data.path("roomStatus")).orElse(0) == 1
                    || parseLong(data.path("roomid")).isPresent();
            if (!hasRoom) {
                return Optional.empty();
            }
            Long roomId = parseLong(data.path("roomid"))
                    .orElseThrow(() -> parseError(ROOM_INFO_OLD_ENDPOINT, "Missing data.roomid", raw));
            return Optional.of(new BilibiliFetchedLiveRoomSnapshot(
                    null,
                    roomId,
                    null,
                    null,
                    null,
                    optionalText(data.path("title")).orElse(null),
                    optionalText(data.path("cover")).orElse(null),
                    null,
                    null,
                    null,
                    null,
                    null,
                    parseInteger(data.path("liveStatus")).or(() -> parseInteger(data.path("live_status"))).orElse(0),
                    null,
                    parseLong(data.path("online")).orElse(null),
                    null,
                    fetchedAt,
                    ROOM_INFO_OLD_ENDPOINT,
                    raw
            ));
        } catch (BilibiliFetchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw parseError(ROOM_INFO_OLD_ENDPOINT, "Unable to parse old room info response: " + exception.getMessage(), raw);
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
                    "Bilibili live HTTP " + status + " from " + endpointKey,
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
                    "Network error calling Bilibili live API: " + exception.getMessage(),
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
        boolean retryable = code >= 500 || code == -500;
        String message = optionalText(root.path("message")).orElse("Bilibili live API error: " + code);
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

    private Optional<Long> parseLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (node.isNumber()) {
            return Optional.of(node.asLong());
        }
        return parseLongText(node.asText(null));
    }

    private Optional<Long> parseLongText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private Optional<Integer> parseInteger(JsonNode node) {
        return parseLong(node).map(Long::intValue);
    }

    private Optional<OffsetDateTime> parseLiveTime(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        if (node.isNumber()) {
            long value = node.asLong();
            return value <= 0 ? Optional.empty() : Optional.of(OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(value), CHINA_OFFSET));
        }
        String value = node.asText(null);
        if (value == null || value.isBlank() || value.startsWith("0000-00-00")) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(value, BILI_TIME_FORMATTER).atOffset(CHINA_OFFSET));
        } catch (Exception ignored) {
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

    private SimpleClientHttpRequestFactory requestFactory(BilibiliLiveMonitorProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return factory;
    }
}
