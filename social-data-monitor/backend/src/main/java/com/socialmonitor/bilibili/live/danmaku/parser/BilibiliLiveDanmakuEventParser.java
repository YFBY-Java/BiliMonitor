package com.socialmonitor.bilibili.live.danmaku.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.Actor;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.EventKind;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.Gift;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.Metrics;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
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
            String normalizedCommand = normalizeCommand(command);
            OffsetDateTime safeReceivedAt = receivedAt == null
                    ? OffsetDateTime.now(DISPLAY_OFFSET)
                    : receivedAt;
            OffsetDateTime occurredAt = eventTime(root, safeReceivedAt);
            return Optional.of(switch (normalizedCommand) {
                case "DANMU_MSG" -> danmuEvent(command, normalizedCommand, root, rawJson, safeReceivedAt, occurredAt);
                case "SEND_GIFT" -> giftEvent(command, normalizedCommand, root, rawJson, safeReceivedAt, occurredAt);
                case "SUPER_CHAT_MESSAGE", "SUPER_CHAT_MESSAGE_JPN" ->
                        superChatEvent(command, normalizedCommand, root, rawJson, safeReceivedAt, occurredAt);
                case "GUARD_BUY" -> guardBuyEvent(command, normalizedCommand, root, rawJson, safeReceivedAt, occurredAt);
                case "LIVE" -> liveControlEvent(command, normalizedCommand, root, rawJson, safeReceivedAt, occurredAt, true);
                case "PREPARING" -> liveControlEvent(command, normalizedCommand, root, rawJson, safeReceivedAt, occurredAt, false);
                case "WATCHED_CHANGE" -> watchedEvent(command, normalizedCommand, root, rawJson, safeReceivedAt, occurredAt);
                case "LIKE_INFO_V3_UPDATE" -> likeEvent(command, normalizedCommand, root, rawJson, safeReceivedAt, occurredAt);
                case "ROOM_REAL_TIME_MESSAGE_UPDATE" ->
                        roomRealtimeEvent(command, normalizedCommand, root, rawJson, safeReceivedAt, occurredAt);
                case "COMBO_SEND", "USER_TOAST_MSG" ->
                        notificationEvent(command, normalizedCommand, root, rawJson, safeReceivedAt, occurredAt);
                default -> unknownEvent(command, normalizedCommand, root, rawJson, safeReceivedAt, occurredAt);
            });
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private BilibiliLiveDanmakuEvent danmuEvent(
            String command,
            String normalizedCommand,
            JsonNode root,
            String rawJson,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt
    ) {
        JsonNode info = root.path("info");
        JsonNode userInfo = info.path(2);
        JsonNode medalInfo = info.path(3);
        JsonNode detail = info.path(0).path(15);
        JsonNode detailUser = detail.path("user");

        Long senderUid = userInfo.isArray() && userInfo.size() > 0 ? parseLong(userInfo.path(0)) : null;
        String arrayDisplayName = userInfo.isArray() && userInfo.size() > 1 ? text(userInfo.path(1)) : null;
        String displayName = firstPlainText(
                text(detailUser.path("base").path("name")),
                text(detailUser.path("name")),
                text(detailUser.path("uname"))
        );
        displayName = chooseBetterName(displayName, arrayDisplayName);
        String medalName = firstText(
                detailUser.path("medal").path("name"),
                detailUser.path("medal").path("medal_name"),
                medalInfo.isArray() && medalInfo.size() > 1 ? medalInfo.path(1) : null
        );
        String sourceEventId = firstNonBlank(
                extraId(root.path("extra")),
                extraId(root.path("data").path("extra")),
                extraId(detail.path("extra")),
                text(detail.path("id_str"))
        );
        OffsetDateTime sentAt = danmuTime(info, root, occurredAt);
        return event(
                command, normalizedCommand, EventKind.DANMAKU, sourceEventId, root, rawJson,
                sentAt, receivedAt, null, null,
                actor(senderUid, displayName, medalName), null, Metrics.empty(),
                info.path(1).asText(""), null, null, null
        );
    }

    private BilibiliLiveDanmakuEvent giftEvent(
            String command,
            String normalizedCommand,
            JsonNode root,
            String rawJson,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt
    ) {
        JsonNode data = root.path("data");
        Long uid = firstLong(data, "uid", "sender_uid");
        String displayName = firstText(
                data.path("uname"),
                data.path("username"),
                data.path("name"),
                data.path("uinfo").path("base").path("name")
        );
        String medalName = firstText(
                data.path("medal_info").path("medal_name"),
                data.path("medal_info").path("name"),
                data.path("uinfo").path("medal").path("name")
        );
        Long giftId = nonNegative(firstLong(data, "giftId", "gift_id"));
        String giftName = firstText(data.path("giftName"), data.path("gift_name"));
        int quantity = positiveInt(firstLong(data, "num", "gift_num"), 1);
        String coinType = normalizeCoinType(firstText(data.path("coin_type"), data.path("coinType")));
        Long price = nonNegative(firstLong(data, "price", "discount_price"));
        Long totalCoin = nonNegative(firstLong(data, "total_coin", "totalCoin"));
        boolean paid = "gold".equals(coinType);
        long amountMilliYuan = paid ? valueOrZero(totalCoin != null ? totalCoin : multiply(price, quantity)) : 0L;
        String sourceEventId = firstText(data.path("tid"), data.path("rnd"), data.path("batch_combo_id"));
        Gift gift = new Gift(giftId, giftName, quantity, coinType, price, totalCoin, paid, amountMilliYuan);
        return event(
                command, normalizedCommand, EventKind.GIFT, sourceEventId, root, rawJson,
                occurredAt, receivedAt, null, null,
                actor(uid, displayName, medalName), gift, Metrics.empty(),
                null, amountMilliYuan, null, quantity
        );
    }

    private BilibiliLiveDanmakuEvent superChatEvent(
            String command,
            String normalizedCommand,
            JsonNode root,
            String rawJson,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt
    ) {
        JsonNode data = root.path("data");
        JsonNode userInfo = data.path("user_info");
        Long price = nonNegative(firstLong(data, "price"));
        Long amountMilliYuan = multiply(price, 1000);
        String sourceEventId = firstText(data.path("id_str"), data.path("id"));
        return event(
                command, normalizedCommand, EventKind.SUPER_CHAT, sourceEventId, root, rawJson,
                occurredAt, receivedAt, null, null,
                actor(
                        firstLong(data, "uid"),
                        firstText(userInfo.path("uname"), data.path("uname")),
                        firstText(
                                data.path("medal_info").path("medal_name"),
                                data.path("medal_info").path("name"),
                                userInfo.path("medal_info").path("medal_name"),
                                userInfo.path("medal_info").path("name")
                        )
                ),
                null,
                Metrics.empty(),
                text(data.path("message")),
                amountMilliYuan,
                null,
                1
        );
    }

    private BilibiliLiveDanmakuEvent guardBuyEvent(
            String command,
            String normalizedCommand,
            JsonNode root,
            String rawJson,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt
    ) {
        JsonNode data = root.path("data");
        Long uid = nonNegative(firstLong(data, "uid"));
        Integer guardLevel = nonNegativeInteger(firstLong(data, "guard_level", "guardLevel"));
        int quantity = positiveInt(firstLong(data, "num"), 1);
        Long price = nonNegative(firstLong(data, "price"));
        Long amountMilliYuan = multiply(price, quantity);
        String sourceEventId = firstText(
                data.path("payflow_id"),
                data.path("payflowId"),
                data.path("order_id"),
                data.path("orderId")
        );
        if (sourceEventId == null) {
            String semanticInput = String.join("|",
                    value(uid),
                    value(guardLevel),
                    String.valueOf(quantity),
                    value(price),
                    firstNonBlank(
                            firstText(data.path("start_time"), data.path("timestamp"), data.path("ts")),
                            ""
                    )
            );
            sourceEventId = "semantic:" + sha256(semanticInput);
        }
        return event(
                command, normalizedCommand, EventKind.GUARD_BUY, sourceEventId, root, rawJson,
                occurredAt, receivedAt, null, null,
                actor(uid, firstText(data.path("username"), data.path("uname")), null),
                null,
                Metrics.empty(),
                null,
                amountMilliYuan,
                guardLevel,
                quantity
        );
    }

    private BilibiliLiveDanmakuEvent liveControlEvent(
            String command,
            String normalizedCommand,
            JsonNode root,
            String rawJson,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt,
            boolean live
    ) {
        JsonNode data = root.path("data");
        String liveKey = firstText(
                root.path("live_key"), data.path("live_key"),
                root.path("sub_session_key"), data.path("sub_session_key")
        );
        String sourceEventId = firstNonBlank(
                firstText(root.path("msg_id"), data.path("msg_id")),
                liveKey
        );
        OffsetDateTime liveStartedAt = live
                ? optionalEpoch(firstLong(root, "live_time"), firstLong(data, "live_time"))
                : null;
        return event(
                command, normalizedCommand, live ? EventKind.LIVE : EventKind.PREPARING,
                sourceEventId, root, rawJson,
                occurredAt,
                receivedAt,
                liveStartedAt,
                liveKey,
                null, null, Metrics.empty(), null, null, null, null
        );
    }

    private BilibiliLiveDanmakuEvent watchedEvent(
            String command,
            String normalizedCommand,
            JsonNode root,
            String rawJson,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt
    ) {
        Long watched = firstLong(root.path("data"), "num", "text_large", "text_small", "count", "watched_count");
        return metricEvent(command, normalizedCommand, root, rawJson, receivedAt, occurredAt,
                new Metrics(null, null, watched));
    }

    private BilibiliLiveDanmakuEvent likeEvent(
            String command,
            String normalizedCommand,
            JsonNode root,
            String rawJson,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt
    ) {
        JsonNode data = root.path("data");
        Long likeCount = firstLong(data, "click_count", "count", "like_count", "total");
        Long increment = firstLong(data, "increment", "like_increment", "num", "count_update");
        return metricEvent(command, normalizedCommand, root, rawJson, receivedAt, occurredAt,
                new Metrics(likeCount, increment, null));
    }

    private BilibiliLiveDanmakuEvent roomRealtimeEvent(
            String command,
            String normalizedCommand,
            JsonNode root,
            String rawJson,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt
    ) {
        JsonNode data = root.path("data");
        Long watched = firstLong(data, "watched_count", "watching_count", "online", "fans");
        Long likeCount = firstLong(data, "like_count", "likes");
        return metricEvent(command, normalizedCommand, root, rawJson, receivedAt, occurredAt,
                new Metrics(likeCount, null, watched));
    }

    private BilibiliLiveDanmakuEvent metricEvent(
            String command,
            String normalizedCommand,
            JsonNode root,
            String rawJson,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt,
            Metrics metrics
    ) {
        String sourceEventId = firstText(root.path("msg_id"), root.path("data").path("id"));
        return event(
                command, normalizedCommand, EventKind.METRICS, sourceEventId, root, rawJson,
                occurredAt, receivedAt, null, null,
                null, null, metrics, null, null, null, null
        );
    }

    private BilibiliLiveDanmakuEvent notificationEvent(
            String command,
            String normalizedCommand,
            JsonNode root,
            String rawJson,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt
    ) {
        JsonNode data = root.path("data");
        String sourceEventId = firstText(
                data.path("id"), data.path("batch_combo_id"), data.path("combo_id"), data.path("rnd")
        );
        return event(
                command, normalizedCommand, EventKind.NOTIFICATION, sourceEventId, root, rawJson,
                occurredAt, receivedAt, null, null,
                actor(
                        firstLong(data, "uid"),
                        firstText(data.path("username"), data.path("uname")),
                        null
                ),
                null, Metrics.empty(), null, null, null, null
        );
    }

    private BilibiliLiveDanmakuEvent unknownEvent(
            String command,
            String normalizedCommand,
            JsonNode root,
            String rawJson,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt
    ) {
        return event(
                command, normalizedCommand, EventKind.UNKNOWN, null, root, rawJson,
                occurredAt, receivedAt, null, null,
                null, null, Metrics.empty(), null, null, null, null
        );
    }

    private BilibiliLiveDanmakuEvent event(
            String command,
            String normalizedCommand,
            EventKind kind,
            String sourceEventId,
            JsonNode root,
            String rawJson,
            OffsetDateTime occurredAt,
            OffsetDateTime receivedAt,
            OffsetDateTime liveStartedAt,
            String liveKey,
            Actor actor,
            Gift gift,
            Metrics metrics,
            String messageText,
            Long amountMilliYuan,
            Integer guardLevel,
            Integer quantity
    ) {
        return new BilibiliLiveDanmakuEvent(
                command,
                kind,
                eventKey(normalizedCommand, sourceEventId),
                sourceEventId,
                rawJson,
                occurredAt,
                receivedAt,
                liveStartedAt,
                liveKey,
                actor,
                gift,
                metrics,
                messageText,
                amountMilliYuan,
                guardLevel,
                quantity
        );
    }

    private String eventKey(String normalizedCommand, String sourceEventId) {
        if (sourceEventId != null && !sourceEventId.isBlank()) {
            return normalizedCommand + ":" + sourceEventId;
        }
        return null;
    }

    private String extraId(JsonNode extra) {
        if (extra == null || extra.isMissingNode() || extra.isNull()) {
            return null;
        }
        if (extra.isObject()) {
            return firstText(extra.path("id_str"), extra.path("id"));
        }
        if (!extra.isTextual()) {
            return null;
        }
        String value = extra.asText("").trim();
        if (!value.startsWith("{")) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(value);
            return firstText(parsed.path("id_str"), parsed.path("id"));
        } catch (Exception ignored) {
            return null;
        }
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

    private String firstText(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            String value = text(node);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.isContainerNode()) {
            return null;
        }
        return blankToNull(node.asText(null));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
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

    private Actor actor(Long uid, String displayName, String medalName) {
        uid = nonNegative(uid);
        if (uid == null && displayName == null && medalName == null) {
            return null;
        }
        return new Actor(uid, displayName, medalName);
    }

    private OffsetDateTime eventTime(JsonNode root, OffsetDateTime fallback) {
        Long sendTime = firstLong(root, "send_time", "timestamp", "ts");
        if (sendTime == null) {
            sendTime = firstLong(root.path("data"), "send_time", "timestamp", "ts", "start_time");
        }
        return fromEpoch(sendTime, fallback);
    }

    private OffsetDateTime danmuTime(JsonNode info, JsonNode root, OffsetDateTime fallback) {
        Long fromInfo = null;
        JsonNode ext = info.path(0);
        if (ext.isArray() && ext.size() > 4) {
            fromInfo = parseLong(ext.path(4));
        }
        Long fromRoot = firstLong(root, "send_time", "timestamp", "ts");
        return fromEpoch(fromInfo == null ? fromRoot : fromInfo, fallback);
    }

    private OffsetDateTime optionalEpoch(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null && value > 0) {
                return fromEpoch(value, null);
            }
        }
        return null;
    }

    private Long firstLong(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
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
        String value = text(node);
        if (value == null) {
            return null;
        }
        String normalized = value.replace(",", "");
        double multiplier = 1D;
        if (normalized.endsWith("\u4ebf")) {
            multiplier = 100_000_000D;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("\u4e07")) {
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
        try {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), DISPLAY_OFFSET);
        } catch (DateTimeException exception) {
            return fallback;
        }
    }

    private Integer toInteger(Long value) {
        if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            return null;
        }
        return value.intValue();
    }

    private Integer nonNegativeInteger(Long value) {
        Integer converted = toInteger(value);
        return converted == null || converted < 0 ? null : converted;
    }

    private Long nonNegative(Long value) {
        return value == null || value < 0 ? null : value;
    }

    private int positiveInt(Long value, int fallback) {
        Integer converted = toInteger(value);
        return converted == null || converted <= 0 ? fallback : converted;
    }

    private Long multiply(Long value, int multiplier) {
        if (value == null) {
            return null;
        }
        try {
            return Math.multiplyExact(value, multiplier);
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private String normalizeCoinType(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
