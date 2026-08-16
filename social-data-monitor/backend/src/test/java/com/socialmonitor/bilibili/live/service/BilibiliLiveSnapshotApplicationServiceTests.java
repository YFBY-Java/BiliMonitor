package com.socialmonitor.bilibili.live.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialmonitor.bilibili.live.client.BilibiliLiveApiClient;
import com.socialmonitor.bilibili.live.domain.BilibiliFetchedLiveRoomSnapshot;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.repository.BilibiliLiveMonitorRepository;
import com.socialmonitor.bilibili.live.session.service.BilibiliLiveSessionBoundaryService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveSnapshotApplicationServiceTests {

    private static final OffsetDateTime T2 = OffsetDateTime.of(
            2026, 8, 16, 20, 2, 0, 0, ZoneOffset.ofHours(8)
    );

    @Mock
    private BilibiliLiveMonitorRepository repository;
    @Mock
    private BilibiliLiveSessionBoundaryService boundaryService;

    private BilibiliLiveSnapshotApplicationService service;

    @BeforeEach
    void setUp() {
        service = new BilibiliLiveSnapshotApplicationService(repository, boundaryService);
    }

    @Test
    void staleT1ResponseAfterPersistedT2IsIgnored() {
        BilibiliLiveRoomMonitor currentAtT2 = roomAt(T2);
        BilibiliFetchedLiveRoomSnapshot staleT1 = snapshotAt(T2.minusMinutes(1));
        when(repository.findByIdForUpdate(currentAtT2.id())).thenReturn(Optional.of(currentAtT2));

        boolean applied = service.applySuccessfulSnapshot(currentAtT2.id(), staleT1);

        assertThat(applied).isFalse();
        verify(boundaryService, never()).reconcileRest(any(), any());
        verify(repository, never()).updateSuccessfulSnapshot(any(), any(), any());
        verify(repository, never()).upsertSnapshot(any(), any());
        verify(repository, never()).insertStatusEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void monitorWithoutPersistedSnapshotTimeAcceptsFirstSuccessfulSnapshot() {
        BilibiliLiveRoomMonitor current = roomWithoutPersistedTime();
        BilibiliFetchedLiveRoomSnapshot snapshot = snapshotAt(T2);
        when(repository.findByIdForUpdate(current.id())).thenReturn(Optional.of(current));

        boolean applied = service.applySuccessfulSnapshot(current.id(), snapshot);

        assertThat(applied).isTrue();
        verify(boundaryService).reconcileRest(current, snapshot);
        verify(repository).updateSuccessfulSnapshot(current.id(), snapshot, T2.plusSeconds(300));
        verify(repository).upsertSnapshot(current.id(), snapshot);
    }

    @Test
    void initializesNewUidWithinCoordinatorInPersistenceOrder() {
        BilibiliFetchedLiveRoomSnapshot snapshot = snapshotAt(T2);
        BilibiliLiveRoomMonitor created = roomWithoutPersistedTime();
        when(repository.findByUidForUpdate(snapshot.uid())).thenReturn(Optional.empty());
        when(repository.upsertMonitorFromSnapshot(snapshot, 300, T2.plusSeconds(300)))
                .thenReturn(Optional.of(created));

        BilibiliLiveRoomMonitor result = service.initializeMonitor(snapshot, 300);

        assertThat(result).isEqualTo(created);
        InOrder order = org.mockito.Mockito.inOrder(repository, boundaryService);
        order.verify(repository).lockInitializationUid(snapshot.uid());
        order.verify(repository).findByUidForUpdate(snapshot.uid());
        order.verify(repository).upsertMonitorFromSnapshot(snapshot, 300, T2.plusSeconds(300));
        order.verify(boundaryService).reconcileRest(created, snapshot);
        order.verify(repository).upsertSnapshot(created.id(), snapshot);
    }

    @Test
    void initializesExistingUidUsingLockedPreviousStateForBoundary() {
        BilibiliLiveRoomMonitor previous = roomAt(T2.minusMinutes(1));
        BilibiliLiveRoomMonitor updated = roomAt(T2);
        BilibiliFetchedLiveRoomSnapshot snapshot = snapshotAt(T2);
        when(repository.findByUidForUpdate(snapshot.uid())).thenReturn(Optional.of(previous));
        when(repository.upsertMonitorFromSnapshot(snapshot, 300, T2.plusSeconds(300)))
                .thenReturn(Optional.of(updated));

        BilibiliLiveRoomMonitor result = service.initializeMonitor(snapshot, 300);

        assertThat(result).isEqualTo(updated);
        verify(boundaryService).reconcileRest(previous, snapshot);
        verify(repository).upsertSnapshot(updated.id(), snapshot);
    }

    @Test
    void staleExistingUidSnapshotOnlyReactivatesMonitorSettings() {
        BilibiliLiveRoomMonitor currentAtT2 = roomAt(T2);
        BilibiliFetchedLiveRoomSnapshot staleT1 = snapshotAt(T2.minusMinutes(1));
        when(repository.findByUidForUpdate(staleT1.uid())).thenReturn(Optional.of(currentAtT2));
        when(repository.findById(currentAtT2.id())).thenReturn(Optional.of(currentAtT2));

        BilibiliLiveRoomMonitor result = service.initializeMonitor(staleT1, 300);

        assertThat(result).isEqualTo(currentAtT2);
        verify(repository).updateMonitor(
                currentAtT2.id(), 300, true, staleT1.fetchedAt().plusSeconds(300)
        );
        verify(repository, never()).upsertMonitorFromSnapshot(any(), any(Integer.class), any());
        verify(boundaryService, never()).reconcileRest(any(), any());
        verify(repository, never()).upsertSnapshot(any(), any());
    }

    @Test
    void initializationEntryIsTransactional() throws Exception {
        var method = BilibiliLiveSnapshotApplicationService.class.getMethod(
                "initializeMonitor", BilibiliFetchedLiveRoomSnapshot.class, int.class
        );

        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void concurrentFirstAddRechecksWinnerWhenSqlRejectsStaleConflictUpdate() {
        BilibiliLiveRoomMonitor winnerAtT2 = roomAt(T2);
        BilibiliFetchedLiveRoomSnapshot staleT1 = snapshotAt(T2.minusMinutes(1));
        when(repository.findByUidForUpdate(staleT1.uid()))
                .thenReturn(Optional.empty(), Optional.of(winnerAtT2));
        when(repository.upsertMonitorFromSnapshot(
                staleT1, 300, staleT1.fetchedAt().plusSeconds(300)
        )).thenReturn(Optional.empty());
        when(repository.findById(winnerAtT2.id())).thenReturn(Optional.of(winnerAtT2));

        BilibiliLiveRoomMonitor result = service.initializeMonitor(staleT1, 300);

        assertThat(result).isEqualTo(winnerAtT2);
        verify(repository).updateMonitor(
                winnerAtT2.id(), 300, true, staleT1.fetchedAt().plusSeconds(300)
        );
        verify(boundaryService, never()).reconcileRest(any(), any());
        verify(repository, never()).upsertSnapshot(any(), any());
    }

    @Test
    void newerPendingBoundaryRejectsSnapshotBeforeEventsAndMonitorWrites() {
        BilibiliLiveRoomMonitor current = roomAt(T2.minusMinutes(2));
        BilibiliFetchedLiveRoomSnapshot snapshot = snapshotAt(T2.minusMinutes(1));
        when(repository.findByIdForUpdate(current.id())).thenReturn(Optional.of(current));
        when(boundaryService.hasNewerPendingSignal(current.id(), snapshot.fetchedAt()))
                .thenReturn(true);

        boolean applied = service.applySuccessfulSnapshot(current.id(), snapshot);

        assertThat(applied).isFalse();
        verify(boundaryService, never()).reconcileRest(any(), any());
        verify(repository, never()).insertStatusEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(repository, never()).updateSuccessfulSnapshot(any(), any(), any());
        verify(repository, never()).upsertSnapshot(any(), any());
    }

    private BilibiliLiveRoomMonitor roomAt(OffsetDateTime persistedAt) {
        return new BilibiliLiveRoomMonitor(
                11L, 22L, 33L, null, "anchor", null, "new title", null, null,
                null, null, null, null, 1, persistedAt.minusHours(1), 100L, 200L,
                "ACTIVE", 300, persistedAt.plusMinutes(5), persistedAt, persistedAt,
                null, null, null, null, BilibiliLiveApiClient.STATUS_BY_UIDS_ENDPOINT,
                persistedAt.minusDays(1), persistedAt
        );
    }

    private BilibiliFetchedLiveRoomSnapshot snapshotAt(OffsetDateTime fetchedAt) {
        return new BilibiliFetchedLiveRoomSnapshot(
                22L, 33L, null, "anchor", null, "old title", null, null,
                null, null, null, null, 0, null, 90L, 200L,
                fetchedAt, BilibiliLiveApiClient.STATUS_BY_UIDS_ENDPOINT, "{}"
        );
    }

    private BilibiliLiveRoomMonitor roomWithoutPersistedTime() {
        return new BilibiliLiveRoomMonitor(
                11L, 22L, 33L, null, "anchor", null, "old title", null, null,
                null, null, null, null, 0, null, 100L, 200L,
                "ACTIVE", 300, null, null, null,
                null, null, null, null, BilibiliLiveApiClient.STATUS_BY_UIDS_ENDPOINT,
                T2.minusDays(1), T2.minusDays(1)
        );
    }
}
