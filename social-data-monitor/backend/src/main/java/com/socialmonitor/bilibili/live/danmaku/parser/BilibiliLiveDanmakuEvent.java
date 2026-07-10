package com.socialmonitor.bilibili.live.danmaku.parser;

import java.time.OffsetDateTime;

public record BilibiliLiveDanmakuEvent(
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
}
