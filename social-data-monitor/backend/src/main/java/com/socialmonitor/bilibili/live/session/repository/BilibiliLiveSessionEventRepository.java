package com.socialmonitor.bilibili.live.session.repository;

import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.EventKind;

public interface BilibiliLiveSessionEventRepository {

    boolean existsByStrongSourceId(Long monitorId, EventKind eventKind, String sourceEventId);

    boolean insertIfAbsent(
            Long liveSessionId,
            Long monitorId,
            Long roomId,
            Long connectionSessionId,
            long receiptOrdinal,
            Integer protocolVersion,
            BilibiliLiveDanmakuEvent event,
            String resolvedDisplayName
    );
}
