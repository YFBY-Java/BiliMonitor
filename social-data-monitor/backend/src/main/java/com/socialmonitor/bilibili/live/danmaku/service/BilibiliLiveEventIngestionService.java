package com.socialmonitor.bilibili.live.danmaku.service;

import com.socialmonitor.bilibili.live.danmaku.config.BilibiliLiveDanmakuProperties;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.EventKind;
import com.socialmonitor.bilibili.live.danmaku.repository.BilibiliLiveDanmakuRepository;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.session.domain.BilibiliLiveSession;
import com.socialmonitor.bilibili.live.session.repository.BilibiliLiveSessionEventRepository;
import com.socialmonitor.bilibili.live.session.service.BilibiliLiveSessionBoundaryService;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveEventIngestionService {

    private final BilibiliLiveSessionEventRepository eventRepository;
    private final BilibiliLiveSessionBoundaryService boundaryService;
    private final BilibiliLiveDanmakuRepository legacyRepository;
    private final BilibiliLiveDanmakuProperties properties;

    public BilibiliLiveEventIngestionService(
            BilibiliLiveSessionEventRepository eventRepository,
            BilibiliLiveSessionBoundaryService boundaryService,
            BilibiliLiveDanmakuRepository legacyRepository,
            BilibiliLiveDanmakuProperties properties
    ) {
        this.eventRepository = eventRepository;
        this.boundaryService = boundaryService;
        this.legacyRepository = legacyRepository;
        this.properties = properties;
    }

    @Transactional
    public boolean ingest(
            BilibiliLiveRoomMonitor room,
            Long connectionSessionId,
            long receiptOrdinal,
            Integer protocolVersion,
            BilibiliLiveDanmakuEvent event,
            String resolvedDisplayName
    ) {
        if (room == null || event == null) {
            return false;
        }
        OffsetDateTime eventTime = event.occurredAt() == null ? event.receivedAt() : event.occurredAt();
        if (!event.isPersistable()) {
            projectLegacy(room, connectionSessionId, event, eventTime, resolvedDisplayName);
            return false;
        }
        boundaryService.lockForIngestion(room.id());
        if (event.hasStrongSourceId() && eventRepository.existsByStrongSourceId(
                room.id(), event.kind(), event.sourceEventId()
        )) {
            projectLegacy(room, connectionSessionId, event, eventTime, resolvedDisplayName);
            return false;
        }
        Optional<BilibiliLiveSession> session = routeSessionBoundary(room, event, eventTime);
        if (session.isEmpty()) {
            projectLegacy(room, connectionSessionId, event, eventTime, resolvedDisplayName);
            return false;
        }
        if (!belongsToSession(session.orElseThrow(), eventTime)) {
            projectLegacy(room, connectionSessionId, event, eventTime, resolvedDisplayName);
            return false;
        }
        boolean inserted = eventRepository.insertIfAbsent(
                session.orElseThrow().id(), room.id(), room.roomId(), connectionSessionId,
                receiptOrdinal, protocolVersion,
                event, resolvedDisplayName
        );
        projectLegacy(room, connectionSessionId, event, eventTime, resolvedDisplayName);
        return inserted;
    }

    private void projectLegacy(
            BilibiliLiveRoomMonitor room,
            Long connectionSessionId,
            BilibiliLiveDanmakuEvent event,
            OffsetDateTime eventTime,
            String resolvedDisplayName
    ) {
        legacyRepository.recordMetricEvent(
                room.id(),
                connectionSessionId,
                room.roomId(),
                eventTime,
                properties.getBucketSeconds(),
                event.isDanmaku() ? 1 : 0,
                event.likeCount(),
                event.likeIncrement(),
                event.watchedCount(),
                null,
                event.giftMetricDelta(),
                event.superChatMetricDelta(),
                1
        );
        if (event.isDanmaku() && hasText(event.messageText())) {
            legacyRepository.insertRecent(
                    room.id(),
                    room.roomId(),
                    event.senderUid(),
                    event.messageText(),
                    hasText(resolvedDisplayName) ? resolvedDisplayName : event.displayName(),
                    event.medalName(),
                    eventTime
            );
            legacyRepository.trimRecent(
                    room.id(), Math.max(20, properties.getRecentMessageLimitPerRoom())
            );
        }
    }

    private Optional<BilibiliLiveSession> routeSessionBoundary(
            BilibiliLiveRoomMonitor room,
            BilibiliLiveDanmakuEvent event,
            OffsetDateTime eventTime
    ) {
        return switch (event.kind()) {
            case LIVE -> Optional.of(boundaryService.observeLiveSignal(
                    room,
                    event.receivedAt() == null ? eventTime : event.receivedAt(),
                    eventTime,
                    event.liveStartedAt(),
                    event.liveKey()
            ));
            case PREPARING -> boundaryService.observePreparingSignal(
                    room,
                    event.receivedAt() == null ? eventTime : event.receivedAt(),
                    eventTime,
                    event.liveKey()
            );
            case DANMAKU, GIFT, SUPER_CHAT, GUARD_BUY -> Optional.of(
                    boundaryService.ensureActiveForEvent(
                            room,
                            event.receivedAt() == null ? eventTime : event.receivedAt(),
                            eventTime
                    )
            );
            case METRICS, NOTIFICATION -> boundaryService.findActive(room.id());
            case UNKNOWN -> Optional.empty();
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean belongsToSession(BilibiliLiveSession session, OffsetDateTime eventTime) {
        if (eventTime == null) {
            return true;
        }
        if (session.startedAt() != null && eventTime.isBefore(session.startedAt())) {
            return false;
        }
        return session.endedAt() == null || !eventTime.isAfter(session.endedAt());
    }
}
