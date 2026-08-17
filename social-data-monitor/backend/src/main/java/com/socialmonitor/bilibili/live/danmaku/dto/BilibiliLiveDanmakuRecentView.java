package com.socialmonitor.bilibili.live.danmaku.dto;

import java.time.OffsetDateTime;

public record BilibiliLiveDanmakuRecentView(
        String messageText,
        Long senderUid,
        String displayName,
        String medalName,
        OffsetDateTime sentAt
) {
}
