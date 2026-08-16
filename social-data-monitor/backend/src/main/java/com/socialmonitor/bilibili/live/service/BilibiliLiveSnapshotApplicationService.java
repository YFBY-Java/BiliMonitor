package com.socialmonitor.bilibili.live.service;

import com.socialmonitor.bilibili.live.domain.BilibiliFetchedLiveRoomSnapshot;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.repository.BilibiliLiveMonitorRepository;
import com.socialmonitor.bilibili.live.session.service.BilibiliLiveSessionBoundaryService;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSnapshotApplicationService {

    private final BilibiliLiveMonitorRepository repository;
    private final BilibiliLiveSessionBoundaryService sessionBoundaryService;

    public BilibiliLiveSnapshotApplicationService(
            BilibiliLiveMonitorRepository repository,
            BilibiliLiveSessionBoundaryService sessionBoundaryService
    ) {
        this.repository = repository;
        this.sessionBoundaryService = sessionBoundaryService;
    }

    @Transactional
    public BilibiliLiveRoomMonitor initializeMonitor(
            BilibiliFetchedLiveRoomSnapshot snapshot,
            int intervalSeconds
    ) {
        repository.lockInitializationUid(snapshot.uid());
        var previous = repository.findByUidForUpdate(snapshot.uid());
        if (previous.isPresent() && isStale(previous.orElseThrow(), snapshot)) {
            return reactivateMonitor(previous.orElseThrow(), snapshot, intervalSeconds);
        }
        if (previous.isPresent() && sessionBoundaryService.hasNewerPendingSignal(
                previous.orElseThrow().id(), snapshot.fetchedAt()
        )) {
            return reactivateMonitor(previous.orElseThrow(), snapshot, intervalSeconds);
        }

        var persisted = repository.upsertMonitorFromSnapshot(
                snapshot,
                intervalSeconds,
                snapshot.fetchedAt().plusSeconds(intervalSeconds)
        );
        if (persisted.isEmpty()) {
            BilibiliLiveRoomMonitor winner = repository.findByUidForUpdate(snapshot.uid())
                    .orElseThrow(() -> new IllegalStateException(
                            "Bilibili live monitor disappeared after upsert conflict: " + snapshot.uid()
                    ));
            return reactivateMonitor(winner, snapshot, intervalSeconds);
        }

        BilibiliLiveRoomMonitor initialized = persisted.orElseThrow();
        sessionBoundaryService.reconcileRest(previous.orElse(initialized), snapshot);
        repository.upsertSnapshot(initialized.id(), snapshot);
        return initialized;
    }

    @Transactional
    public boolean applySuccessfulSnapshot(Long monitorId, BilibiliFetchedLiveRoomSnapshot snapshot) {
        BilibiliLiveRoomMonitor current = repository.findByIdForUpdate(monitorId)
                .orElseThrow(() -> new IllegalArgumentException("Bilibili live monitor not found: " + monitorId));
        OffsetDateTime newestPersistedAt = latest(current.lastSnapshotAt(), current.lastSuccessAt());
        if (newestPersistedAt != null && snapshot.fetchedAt().isBefore(newestPersistedAt)) {
            return false;
        }
        if (sessionBoundaryService.hasNewerPendingSignal(monitorId, snapshot.fetchedAt())) {
            return false;
        }

        recordSuccessEvents(current, snapshot);
        sessionBoundaryService.reconcileRest(current, snapshot);
        repository.updateSuccessfulSnapshot(
                current.id(),
                snapshot,
                snapshot.fetchedAt().plusSeconds(current.intervalSeconds())
        );
        repository.upsertSnapshot(current.id(), snapshot);
        return true;
    }

    private void recordSuccessEvents(
            BilibiliLiveRoomMonitor room,
            BilibiliFetchedLiveRoomSnapshot snapshot
    ) {
        if (room.lastErrorType() != null) {
            repository.insertStatusEvent(
                    room.id(), room.uid(), room.roomId(), "ERROR_RECOVERED",
                    room.liveStatus(), snapshot.liveStatus(), room.title(), snapshot.title(), snapshot.onlineCount()
            );
        }

        Integer from = room.liveStatus();
        Integer to = snapshot.liveStatus();
        if (from != null && to != null && !from.equals(to)) {
            String eventType = statusEventType(from, to);
            if (eventType != null) {
                repository.insertStatusEvent(
                        room.id(), snapshot.uid(), snapshot.roomId(), eventType,
                        from, to, room.title(), snapshot.title(), snapshot.onlineCount()
                );
            }
        }

        if (hasText(room.title()) && hasText(snapshot.title()) && !room.title().equals(snapshot.title())) {
            repository.insertStatusEvent(
                    room.id(), snapshot.uid(), snapshot.roomId(), "TITLE_CHANGED",
                    from, to, room.title(), snapshot.title(), snapshot.onlineCount()
            );
        }
    }

    private String statusEventType(int from, int to) {
        if (to == 1 && from != 1) return "LIVE_STARTED";
        if (from == 1 && to != 1) return "LIVE_ENDED";
        if (to == 2 && from != 2) return "ROUND_STARTED";
        return null;
    }

    private boolean isStale(
            BilibiliLiveRoomMonitor monitor,
            BilibiliFetchedLiveRoomSnapshot snapshot
    ) {
        OffsetDateTime newestPersistedAt = latest(monitor.lastSnapshotAt(), monitor.lastSuccessAt());
        return newestPersistedAt != null && snapshot.fetchedAt().isBefore(newestPersistedAt);
    }

    private BilibiliLiveRoomMonitor reactivateMonitor(
            BilibiliLiveRoomMonitor current,
            BilibiliFetchedLiveRoomSnapshot snapshot,
            int intervalSeconds
    ) {
        repository.updateMonitor(
                current.id(),
                intervalSeconds,
                true,
                snapshot.fetchedAt().plusSeconds(intervalSeconds)
        );
        return repository.findById(current.id()).orElse(current);
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
