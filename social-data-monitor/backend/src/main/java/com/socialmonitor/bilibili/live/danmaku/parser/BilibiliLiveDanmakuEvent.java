package com.socialmonitor.bilibili.live.danmaku.parser;

import java.time.OffsetDateTime;

public record BilibiliLiveDanmakuEvent(
        String command,
        EventKind kind,
        String eventKey,
        String sourceEventId,
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

    public BilibiliLiveDanmakuEvent {
        kind = kind == null ? EventKind.UNKNOWN : kind;
        metrics = metrics == null ? Metrics.empty() : metrics;
    }

    public BilibiliLiveDanmakuEvent(
            String command,
            boolean danmu,
            String messageText,
            String displayName,
            String medalName,
            Long senderUid,
            Long likeCount,
            Long likeIncrement,
            Long watchedCount,
            Integer giftCount,
            Integer superChatCount,
            OffsetDateTime occurredAt
    ) {
        this(
                command,
                legacyKind(danmu, likeCount, likeIncrement, watchedCount, giftCount, superChatCount),
                null,
                null,
                null,
                occurredAt,
                occurredAt,
                null,
                null,
                actor(senderUid, displayName, medalName),
                legacyGift(giftCount),
                new Metrics(likeCount, likeIncrement, watchedCount),
                messageText,
                null,
                null,
                positiveOrNull(superChatCount)
        );
    }

    public boolean isPersistable() {
        return kind != EventKind.UNKNOWN;
    }

    public boolean isDanmaku() {
        return kind == EventKind.DANMAKU;
    }

    public boolean hasStrongSourceId() {
        return sourceEventId != null
                && !sourceEventId.isBlank()
                && !sourceEventId.startsWith("semantic:");
    }

    public String persistenceKey(Long connectionSessionId, long receiptOrdinal) {
        if (hasStrongSourceId()) {
            if (eventKey != null && !eventKey.isBlank()) {
                return eventKey;
            }
            String normalizedCommand = command == null ? "UNKNOWN" : command.split(":", 2)[0];
            return normalizedCommand + ":" + sourceEventId;
        }
        return "receipt:" + connectionSessionId + ":" + receiptOrdinal;
    }

    public int giftMetricDelta() {
        String normalizedCommand = command == null ? "" : command.split(":", 2)[0];
        return "SEND_GIFT".equals(normalizedCommand) || "COMBO_SEND".equals(normalizedCommand) ? 1 : 0;
    }

    public int superChatMetricDelta() {
        if (kind != EventKind.SUPER_CHAT || quantity == null) {
            return 0;
        }
        return Math.max(0, quantity);
    }

    public boolean danmu() {
        return isDanmaku();
    }

    public String displayName() {
        return actor == null ? null : actor.displayName();
    }

    public String medalName() {
        return actor == null ? null : actor.medalName();
    }

    public Long senderUid() {
        return actor == null ? null : actor.uid();
    }

    public Long likeCount() {
        return metrics.likeCount();
    }

    public Long likeIncrement() {
        return metrics.likeIncrement();
    }

    public Long watchedCount() {
        return metrics.watchedCount();
    }

    public Integer giftCount() {
        return giftMetricDelta();
    }

    public Integer superChatCount() {
        return superChatMetricDelta();
    }

    private static EventKind legacyKind(
            boolean danmu,
            Long likeCount,
            Long likeIncrement,
            Long watchedCount,
            Integer giftCount,
            Integer superChatCount
    ) {
        if (danmu) {
            return EventKind.DANMAKU;
        }
        if (giftCount != null && giftCount > 0) {
            return EventKind.GIFT;
        }
        if (superChatCount != null && superChatCount > 0) {
            return EventKind.SUPER_CHAT;
        }
        if (likeCount != null || likeIncrement != null || watchedCount != null) {
            return EventKind.METRICS;
        }
        return EventKind.UNKNOWN;
    }

    private static Actor actor(Long uid, String displayName, String medalName) {
        if (uid == null && displayName == null && medalName == null) {
            return null;
        }
        return new Actor(uid, displayName, medalName);
    }

    private static Gift legacyGift(Integer giftCount) {
        if (giftCount == null || giftCount <= 0) {
            return null;
        }
        return new Gift(null, null, giftCount, null, null, null, false, null);
    }

    private static Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    public enum EventKind {
        DANMAKU,
        GIFT,
        SUPER_CHAT,
        GUARD_BUY,
        LIVE,
        PREPARING,
        METRICS,
        NOTIFICATION,
        UNKNOWN
    }

    public record Actor(Long uid, String displayName, String medalName) {
    }

    public record Gift(
            Long giftId,
            String giftName,
            Integer quantity,
            String coinType,
            Long price,
            Long totalCoin,
            boolean paid,
            Long amountMilliYuan
    ) {
    }

    public record Metrics(Long likeCount, Long likeIncrement, Long watchedCount) {

        public static Metrics empty() {
            return new Metrics(null, null, null);
        }
    }
}
