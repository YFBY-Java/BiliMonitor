package com.socialmonitor.bilibili.live.session.service;

import com.socialmonitor.bilibili.live.domain.BilibiliFetchedLiveRoomSnapshot;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.session.domain.BilibiliLiveSession;
import com.socialmonitor.bilibili.live.session.repository.BilibiliLiveSessionRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSessionBoundaryService {

    private static final String OPEN = "OPEN";
    private static final String END_PENDING = "END_PENDING";
    private static final String CLOSED = "CLOSED";
    private static final String REST_STATUS = "REST_STATUS";
    private static final String RESTART_DETECTED = "RESTART_DETECTED";
    private static final String WS_LIVE = "WS_LIVE";
    private static final String WS_PREPARING = "WS_PREPARING";
    private static final String WS_EVENT_ACTIVITY = "WS_EVENT_ACTIVITY";

    private final BilibiliLiveSessionRepository repository;

    public BilibiliLiveSessionBoundaryService(BilibiliLiveSessionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void lockForIngestion(Long monitorId) {
        repository.lockMonitor(monitorId);
    }

    @Transactional
    public boolean hasNewerPendingSignal(Long monitorId, OffsetDateTime fetchedAt) {
        return repository.findActiveForUpdate(monitorId)
                .filter(session -> END_PENDING.equals(session.state()))
                .map(BilibiliLiveSession::endSignalAt)
                .filter(endSignalAt -> fetchedAt.isBefore(endSignalAt))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<BilibiliLiveSession> findActive(Long monitorId) {
        return repository.findActive(monitorId);
    }

    @Transactional
    public Optional<BilibiliLiveSession> reconcileRest(
            BilibiliLiveRoomMonitor previousRoom,
            BilibiliFetchedLiveRoomSnapshot snapshot
    ) {
        repository.lockMonitor(previousRoom.id());
        Optional<BilibiliLiveSession> active = repository.findActiveForUpdate(previousRoom.id());
        if (active.isPresent()
                && END_PENDING.equals(active.orElseThrow().state())
                && active.orElseThrow().endSignalAt() != null
                && snapshot.fetchedAt().isBefore(active.orElseThrow().endSignalAt())) {
            return active;
        }
        if (snapshot.liveStatus() == null || snapshot.liveStatus() != 1) {
            active.ifPresent(session -> repository.update(close(
                    session, snapshot.fetchedAt(), REST_STATUS, snapshot.title()
            )));
            return Optional.empty();
        }
        if (active.isPresent()) {
            BilibiliLiveSession current = active.orElseThrow();
            if (hasChangedPlatformLiveTime(current.platformLiveTime(), snapshot.liveTime())) {
                repository.update(close(current, snapshot.fetchedAt(), RESTART_DETECTED, snapshot.title()));
                return Optional.of(open(
                        previousRoom,
                        snapshot.fetchedAt(),
                        snapshot.fetchedAt(),
                        snapshot.liveTime(),
                        platformKey(snapshot.liveTime(), snapshot.fetchedAt()),
                        REST_STATUS,
                        snapshot.title()
                ));
            }
            BilibiliLiveSession observed = resume(
                    current,
                    snapshot.fetchedAt(),
                    snapshot.liveTime(),
                    platformKey(snapshot.liveTime(), snapshot.fetchedAt()),
                    true
            );
            repository.update(observed);
            return Optional.of(observed);
        }

        Optional<BilibiliLiveSession> existing = findByIdentityForUpdate(
                previousRoom.id(), snapshot.liveTime(), platformKey(snapshot.liveTime(), snapshot.fetchedAt())
        );
        if (existing.isPresent()) {
            BilibiliLiveSession recovered = resume(
                    existing.orElseThrow(),
                    snapshot.fetchedAt(),
                    snapshot.liveTime(),
                    platformKey(snapshot.liveTime(), snapshot.fetchedAt()),
                    true
            );
            repository.update(recovered);
            return Optional.of(recovered);
        }

        return Optional.of(open(
                previousRoom,
                snapshot.fetchedAt(),
                snapshot.fetchedAt(),
                snapshot.liveTime(),
                platformKey(snapshot.liveTime(), snapshot.fetchedAt()),
                REST_STATUS,
                snapshot.title()
        ));
    }

    @Transactional
    public BilibiliLiveSession observeLiveSignal(
            BilibiliLiveRoomMonitor room,
            OffsetDateTime eventTime,
            OffsetDateTime platformLiveTime,
            String liveKey
    ) {
        return observeLiveSignal(room, eventTime, eventTime, platformLiveTime, liveKey);
    }

    @Transactional
    public BilibiliLiveSession observeLiveSignal(
            BilibiliLiveRoomMonitor room,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt,
            OffsetDateTime platformLiveTime,
            String liveKey
    ) {
        OffsetDateTime observationTime = first(receivedAt, occurredAt);
        OffsetDateTime signalTime = first(occurredAt, observationTime);
        repository.lockMonitor(room.id());
        Optional<BilibiliLiveSession> active = repository.findActiveForUpdate(room.id());
        String resolvedKey = hasText(liveKey) ? liveKey : platformKey(platformLiveTime, observationTime);
        if (active.isEmpty()) {
            Optional<BilibiliLiveSession> existing = findByIdentityForUpdate(
                    room.id(), platformLiveTime, resolvedKey
            );
            if (existing.isPresent()) {
                BilibiliLiveSession recovered = resume(
                        existing.orElseThrow(), observationTime, platformLiveTime, resolvedKey, true
                );
                repository.update(recovered);
                return recovered;
            }
            return open(
                    room, observationTime, signalTime, platformLiveTime, resolvedKey, WS_LIVE, room.title()
            );
        }
        BilibiliLiveSession current = active.orElseThrow();
        OffsetDateTime authoritativeStart = first(platformLiveTime, signalTime);
        boolean changedIdentity = hasChangedPlatformLiveTime(current.platformLiveTime(), platformLiveTime)
                || hasChangedRealLiveKey(current.liveKey(), resolvedKey);
        if (changedIdentity
                && authoritativeStart != null
                && authoritativeStart.isBefore(current.startedAt())) {
            return findByIdentityForUpdate(room.id(), platformLiveTime, resolvedKey)
                    .orElse(current);
        }
        if (isOlderThanPendingSignal(current, signalTime)) {
            return current;
        }
        if (changedIdentity) {
            repository.update(close(current, observationTime, RESTART_DETECTED, room.title()));
            Optional<BilibiliLiveSession> existing = findByIdentityForUpdate(
                    room.id(), platformLiveTime, resolvedKey
            );
            if (existing.isPresent()) {
                BilibiliLiveSession recovered = resume(
                        existing.orElseThrow(), observationTime, platformLiveTime, resolvedKey, true
                );
                repository.update(recovered);
                return recovered;
            }
            return open(
                    room, observationTime, signalTime, platformLiveTime, resolvedKey, WS_LIVE, room.title()
            );
        }
        BilibiliLiveSession observed = resume(
                current, observationTime, platformLiveTime, resolvedKey, true
        );
        repository.update(observed);
        return observed;
    }

    @Transactional
    public Optional<BilibiliLiveSession> observePreparingSignal(
            BilibiliLiveRoomMonitor room,
            OffsetDateTime eventTime
    ) {
        return observePreparingSignal(room, eventTime, eventTime, null);
    }

    @Transactional
    public Optional<BilibiliLiveSession> observePreparingSignal(
            BilibiliLiveRoomMonitor room,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt,
            String liveKey
    ) {
        OffsetDateTime observationTime = first(receivedAt, occurredAt);
        OffsetDateTime signalTime = first(occurredAt, observationTime);
        repository.lockMonitor(room.id());
        Optional<BilibiliLiveSession> active = repository.findActiveForUpdate(room.id());
        repository.scheduleImmediateCollection(room.id());
        if (active.isEmpty()) {
            return findHistoricalSignalSession(room.id(), signalTime, liveKey);
        }
        BilibiliLiveSession current = active.orElseThrow();
        if (hasChangedRealLiveKey(current.liveKey(), liveKey)
                || signalTime.isBefore(current.startedAt())) {
            return findHistoricalSignalSession(room.id(), signalTime, liveKey)
                    .or(() -> Optional.of(current));
        }
        if (END_PENDING.equals(current.state())) {
            return Optional.of(current);
        }
        BilibiliLiveSession pending = new BilibiliLiveSession(
                current.id(), current.monitorId(), current.uid(), current.roomId(), END_PENDING,
                current.platformLiveTime(), current.liveKey(), current.startedAt(), current.startDetectedAt(),
                current.startSource(), signalTime, null, null, WS_PREPARING,
                current.lastLiveObservedAt(), latest(current.lastObservedAt(), observationTime),
                current.titleAtStart(), current.titleAtEnd(), current.createdAt(), observationTime
        );
        repository.update(pending);
        return Optional.of(pending);
    }

    @Transactional
    public BilibiliLiveSession ensureActiveForEvent(
            BilibiliLiveRoomMonitor room,
            OffsetDateTime eventTime
    ) {
        return ensureActiveForEvent(room, eventTime, eventTime);
    }

    @Transactional
    public BilibiliLiveSession ensureActiveForEvent(
            BilibiliLiveRoomMonitor room,
            OffsetDateTime receivedAt,
            OffsetDateTime occurredAt
    ) {
        OffsetDateTime observationTime = first(receivedAt, occurredAt);
        OffsetDateTime eventTime = first(occurredAt, observationTime);
        repository.lockMonitor(room.id());
        Optional<BilibiliLiveSession> active = repository.findActiveForUpdate(room.id());
        if (active.isEmpty()) {
            Optional<BilibiliLiveSession> historical = repository.findByEventTimeForUpdate(
                    room.id(), eventTime
            );
            if (historical.isPresent()) {
                return historical.orElseThrow();
            }
            return open(
                    room, observationTime, eventTime, null, "activity:" + eventTime.toInstant(),
                    WS_EVENT_ACTIVITY, room.title()
            );
        }
        BilibiliLiveSession current = active.orElseThrow();
        if (eventTime.isBefore(current.startedAt())) {
            return repository.findByEventTimeForUpdate(room.id(), eventTime).orElse(current);
        }
        if (isOlderThanPendingSignal(current, eventTime)) {
            return current;
        }
        BilibiliLiveSession observed = resume(
                current, observationTime, null, null, false
        );
        repository.update(observed);
        return observed;
    }

    private BilibiliLiveSession open(
            BilibiliLiveRoomMonitor room,
            OffsetDateTime detectedAt,
            OffsetDateTime occurredAt,
            OffsetDateTime platformLiveTime,
            String liveKey,
            String source,
            String title
    ) {
        OffsetDateTime startedAt = platformLiveTime == null ? occurredAt : platformLiveTime;
        BilibiliLiveSession candidate = new BilibiliLiveSession(
                null, room.id(), room.uid(), room.roomId(), OPEN, platformLiveTime, liveKey,
                startedAt, detectedAt, source, null, null, null, null,
                detectedAt, detectedAt, title, null, detectedAt, detectedAt
        );
        return repository.insertOpen(candidate);
    }

    private BilibiliLiveSession resume(
            BilibiliLiveSession session,
            OffsetDateTime observedAt,
            OffsetDateTime platformLiveTime,
            String liveKey,
            boolean liveObservation
    ) {
        boolean enrichPlatformTime = session.platformLiveTime() == null && platformLiveTime != null;
        String resolvedLiveKey = session.liveKey();
        if (isRealLiveKey(liveKey)) {
            resolvedLiveKey = liveKey;
        } else if (enrichPlatformTime && !isRealLiveKey(session.liveKey()) && hasText(liveKey)) {
            resolvedLiveKey = liveKey;
        }
        return new BilibiliLiveSession(
                session.id(), session.monitorId(), session.uid(), session.roomId(), OPEN,
                enrichPlatformTime ? platformLiveTime : session.platformLiveTime(),
                resolvedLiveKey,
                enrichPlatformTime ? platformLiveTime : session.startedAt(),
                session.startDetectedAt(), session.startSource(),
                null, null, null, null,
                liveObservation ? latest(session.lastLiveObservedAt(), observedAt) : session.lastLiveObservedAt(),
                latest(session.lastObservedAt(), observedAt), session.titleAtStart(), session.titleAtEnd(),
                session.createdAt(), observedAt
        );
    }

    private BilibiliLiveSession close(
            BilibiliLiveSession session,
            OffsetDateTime detectedAt,
            String source,
            String titleAtEnd
    ) {
        OffsetDateTime signaledEnd = first(session.endSignalAt(), detectedAt);
        OffsetDateTime endedAt = signaledEnd.isBefore(session.startedAt())
                ? session.startedAt()
                : signaledEnd;
        return new BilibiliLiveSession(
                session.id(),
                session.monitorId(),
                session.uid(),
                session.roomId(),
                CLOSED,
                session.platformLiveTime(),
                session.liveKey(),
                session.startedAt(),
                session.startDetectedAt(),
                session.startSource(),
                session.endSignalAt(),
                endedAt,
                detectedAt,
                source,
                session.lastLiveObservedAt(),
                latest(session.lastObservedAt(), detectedAt),
                session.titleAtStart(),
                titleAtEnd,
                session.createdAt(),
                detectedAt
        );
    }

    private boolean hasChangedPlatformLiveTime(OffsetDateTime existing, OffsetDateTime observed) {
        return existing != null && observed != null && !existing.toInstant().equals(observed.toInstant());
    }

    private boolean hasChangedRealLiveKey(String existing, String observed) {
        return isRealLiveKey(existing) && isRealLiveKey(observed) && !existing.equals(observed);
    }

    private boolean isRealLiveKey(String value) {
        return hasText(value)
                && !value.startsWith("platform:")
                && !value.startsWith("activity:")
                && !value.startsWith("rest-observed:")
                && !value.startsWith("migration-current:");
    }

    private String platformKey(OffsetDateTime platformLiveTime, OffsetDateTime observedAt) {
        return platformLiveTime == null
                ? "rest-observed:" + observedAt.toInstant()
                : "platform:" + platformLiveTime.toInstant();
    }

    private Optional<BilibiliLiveSession> findByIdentityForUpdate(
            Long monitorId,
            OffsetDateTime platformLiveTime,
            String liveKey
    ) {
        if (platformLiveTime != null) {
            Optional<BilibiliLiveSession> byPlatformTime = repository.findByPlatformLiveTimeForUpdate(
                    monitorId, platformLiveTime
            );
            if (byPlatformTime.isPresent()) {
                return byPlatformTime;
            }
        }
        if (isRealLiveKey(liveKey)) {
            return repository.findByLiveKeyForUpdate(monitorId, liveKey);
        }
        return Optional.empty();
    }

    private Optional<BilibiliLiveSession> findHistoricalSignalSession(
            Long monitorId,
            OffsetDateTime occurredAt,
            String liveKey
    ) {
        if (isRealLiveKey(liveKey)) {
            Optional<BilibiliLiveSession> byLiveKey = repository.findByLiveKeyForUpdate(
                    monitorId, liveKey
            );
            if (byLiveKey.isPresent()) {
                return byLiveKey;
            }
        }
        return repository.findByEventTimeForUpdate(monitorId, occurredAt);
    }

    private boolean isOlderThanPendingSignal(BilibiliLiveSession session, OffsetDateTime occurredAt) {
        return END_PENDING.equals(session.state())
                && session.endSignalAt() != null
                && occurredAt != null
                && !occurredAt.isAfter(session.endSignalAt());
    }

    private OffsetDateTime first(OffsetDateTime primary, OffsetDateTime fallback) {
        return primary != null ? primary : fallback;
    }

    private OffsetDateTime latest(OffsetDateTime left, OffsetDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
