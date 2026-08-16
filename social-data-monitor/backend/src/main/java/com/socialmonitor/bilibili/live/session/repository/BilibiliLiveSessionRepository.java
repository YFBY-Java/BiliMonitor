package com.socialmonitor.bilibili.live.session.repository;

import com.socialmonitor.bilibili.live.session.domain.BilibiliLiveSession;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface BilibiliLiveSessionRepository {

    void lockMonitor(Long monitorId);

    Optional<BilibiliLiveSession> findActive(Long monitorId);

    Optional<BilibiliLiveSession> findActiveForUpdate(Long monitorId);

    Optional<BilibiliLiveSession> findByPlatformLiveTimeForUpdate(
            Long monitorId,
            OffsetDateTime platformLiveTime
    );

    Optional<BilibiliLiveSession> findByLiveKeyForUpdate(Long monitorId, String liveKey);

    Optional<BilibiliLiveSession> findByEventTimeForUpdate(Long monitorId, OffsetDateTime occurredAt);

    BilibiliLiveSession insertOpen(BilibiliLiveSession session);

    void update(BilibiliLiveSession session);

    void scheduleImmediateCollection(Long monitorId);
}
