package com.socialmonitor.bilibili.live.danmaku.dto;

import java.time.OffsetDateTime;

public record BilibiliLiveDanmakuRecentView(
        String messageText,
        String displayName,
        String medalName,
        OffsetDateTime sentAt
) {
}
