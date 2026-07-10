package com.socialmonitor.bilibili.live.danmaku.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BilibiliLiveDanmakuEventParser {

    private static final ZoneOffset DISPLAY_OFFSET = ZoneOffset.ofHours(8);

    private final ObjectMapper objectMapper;

    public BilibiliLiveDanmakuEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<BilibiliLiveDanmakuEvent> parse(String rawJson, OffsetDateTime receivedAt) {
        if (rawJson == null || rawJson.isBlank() || !rawJson.trim().startsWith("{")) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            String command = root.path("cmd").asText("");
            if (command.isBlank()) {
                return Optional.empty();
            }
            String normalized = normalizeCommand(command);
            OffsetDateTime occurredAt = eventTime(root, receivedAt);
            return Optional.of(switch (normalized) {
                case "DANMU_MSG" -> danmuEvent(command, root, occurredAt);
                case "WATCHED_CHANGE" -> watchedEvent(command, root, occurredAt);
                case "LIKE_INFO_V3_UPDATE" -> likeEvent(command, root, occurredAt);
                case "ROOM_REAL_TIME_MESSAGE_UPDATE" -> roomRealtimeEvent(command, root, occurredAt);
                case "SEND_GIFT", "COMBO_SEND" -> simpleCounterEvent(command, 1, 0, occurredAt);
                case "SUPER_CHAT_MESSAGE", "SUPER_CHAT_MESSAGE_JPN" -> simpleCounterEvent(command, 0, 1, occurredAt);
                default -> rawEvent(command, occurredAt);
            });
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private BilibiliLiveDanmakuEvent danmuEvent(String command, JsonNode root, OffsetDateTime occurredAt) {
        JsonNode info = root.path("info");
        String message = info.path(1).asText("");
        JsonNode userInfo = info.path(2);
        String displayName = null;
        String arrayDisplayName = userInfo.isArray() && userInfo.size() > 1 ? userInfo.path(1).asText(null) : null;
        Long senderUid = userInfo.isArray() && userInfo.size() > 0 ? parseLong(userInfo.path(0)) : null;
        JsonNode medalInfo = info.path(3);
        String medalName = medalInfo.isArray() && medalInfo.size() > 1 ? medalInfo.path(1).asText(null) : null;
        JsonNode detailUser = info.path(0).path(15).path("user");
        if (!detailUser.isMissingNode()) {
            displayName = firstPlainText(
                    detailUser.path("base").path("name").asText(null),
                    detailUser.path("name").asText(null),
                    detailUser.path("uname").asText(null)
            );
        }
        displayName = chooseBetterName(displayName, arrayDisplayName);
        if ((medalName == null || medalName.isBlank()) && !detailUser.isMissingNode()) {
            medalName = detailUser.path("medal").path("name").asText(null);
        }
        return new BilibiliLiveDanmakuEvent(
                command,
                true,
                message,
                displayName,
                medalName,
                senderUid,
                null,
                null,
                null,
                0,
                0,
                danmuTime(info, root, occurredAt)
        );
    }

    private BilibiliLiveDanmakuEvent watchedEvent(String command, JsonNode root, OffsetDateTime occurredAt) {
        Long watched = firstLong(root.path("data"), "num", "text_large", "text_small", "count", "watched_count");
        return new BilibiliLiveDanmakuEvent(command, false, null, null, null,
                null, null, null, watched, 0, 0, occurredAt);
    }

    private BilibiliLiveDanmakuEvent likeEvent(String command, JsonNode root, OffsetDateTime occurredAt) {
        JsonNode data = root.path("data");
        Long likeCount = firstLong(data, "click_count", "count", "like_count", "total");
        Long increment = firstLong(data, "increment", "like_increment", "num", "count_update");
        return new BilibiliLiveDanmakuEvent(command, false, null, null, null,
                null, likeCount, increment, null, 0, 0, occurredAt);
    }

    private BilibiliLiveDanmakuEvent roomRealtimeEvent(String command, JsonNode root, OffsetDateTime occurredAt) {
        JsonNode data = root.path("data");
        Long watched = firstLong(data, "watched_count", "watching_count", "online", "fans");
        Long likeCount = firstLong(data, "like_count", "likes");
        return new BilibiliLiveDanmakuEvent(command, false, null, null, null,
                null, likeCount, null, watched, 0, 0, occurredAt);
    }

    private BilibiliLiveDanmakuEvent simpleCounterEvent(String command, int giftDelta, int superChatDelta, OffsetDateTime occurredAt) {
        return new BilibiliLiveDanmakuEvent(command, false, null, null, null,
                null, null, null, null, giftDelta, superChatDelta, occurredAt);
    }

    private BilibiliLiveDanmakuEvent rawEvent(String command, OffsetDateTime occurredAt) {
        return new BilibiliLiveDanmakuEvent(command, false, null, null, null,
                null, null, null, null, 0, 0, occurredAt);
    }

    private String normalizeCommand(String command) {
        int colonIndex = command.indexOf(':');
        return colonIndex > 0 ? command.substring(0, colonIndex) : command;
    }

    private String chooseBetterName(String preferred, String fallback) {
        String normalizedPreferred = blankToNull(preferred);
        String normalizedFallback = blankToNull(fallback);
        if (normalizedPreferred == null) {
            return normalizedFallback;
        }
        if (normalizedFallback == null) {
            return normalizedPreferred;
        }
        if (isMaskedName(normalizedPreferred) && !isMaskedName(normalizedFallback)) {
            return normalizedFallback;
        }
        return normalizedPreferred;
    }

    private String firstPlainText(String... values) {
        String masked = null;
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized == null) {
                continue;
            }
            if (!isMaskedName(normalized)) {
                return normalized;
            }
            if (masked == null) {
                masked = normalized;
            }
        }
        return masked;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isMaskedName(String value) {
        return value != null && value.indexOf('*') >= 0;
    }

    private OffsetDateTime eventTime(JsonNode root, OffsetDateTime fallback) {
        Long sendTime = firstLong(root, "send_time", "timestamp", "ts");
        return fromEpoch(sendTime, fallback == null ? OffsetDateTime.now(DISPLAY_OFFSET) : fallback);
    }

    private OffsetDateTime danmuTime(JsonNode info, JsonNode root, OffsetDateTime fallback) {
        Long fromInfo = null;
        JsonNode ext = info.path(0);
        if (ext.isArray() && ext.size() > 4) {
            fromInfo = parseLong(ext.path(4));
        }
        Long fromRoot = firstLong(root, "send_time", "timestamp", "ts");
        return fromEpoch(fromInfo == null ? fromRoot : fromInfo, fallback == null ? OffsetDateTime.now(DISPLAY_OFFSET) : fallback);
    }

    private Long firstLong(JsonNode node, String... keys) {
        for (String key : keys) {
            Long value = parseLong(node.path(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long parseLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        String text = node.asText(null);
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().replace(",", "");
        double multiplier = 1D;
        if (normalized.endsWith("亿")) {
            multiplier = 100_000_000D;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("万")) {
            multiplier = 10_000D;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return Math.round(Double.parseDouble(normalized) * multiplier);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private OffsetDateTime fromEpoch(Long value, OffsetDateTime fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        long millis = value < 10_000_000_000L ? value * 1000L : value;
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), DISPLAY_OFFSET);
    }
}
