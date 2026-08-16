package com.socialmonitor.bilibili.live.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialmonitor.bilibili.live.domain.BilibiliFetchedLiveRoomSnapshot;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.session.domain.BilibiliLiveSession;
import com.socialmonitor.bilibili.live.session.repository.BilibiliLiveSessionRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveSessionBoundaryServiceTests {

    private static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);
    private static final OffsetDateTime PLATFORM_LIVE_TIME = OffsetDateTime.of(2026, 8, 16, 19, 30, 0, 0, OFFSET);
    private static final OffsetDateTime OBSERVED_AT = PLATFORM_LIVE_TIME.plusMinutes(2);

    @Mock
    private BilibiliLiveSessionRepository repository;

    private BilibiliLiveSessionBoundaryService service;

    @BeforeEach
    void setUp() {
        service = new BilibiliLiveSessionBoundaryService(repository);
    }

    @Test
    void restLiveWithoutActiveSessionOpensAtPlatformLiveTime() {
        BilibiliLiveRoomMonitor room = room(0, null);
        BilibiliFetchedLiveRoomSnapshot snapshot = snapshot(1, PLATFORM_LIVE_TIME, OBSERVED_AT);
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.empty());
        when(repository.insertOpen(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 101L));

        Optional<BilibiliLiveSession> result = service.reconcileRest(room, snapshot);

        assertThat(result).isPresent();
        BilibiliLiveSession opened = result.orElseThrow();
        assertThat(opened.state()).isEqualTo("OPEN");
        assertThat(opened.startedAt()).isEqualTo(PLATFORM_LIVE_TIME);
        assertThat(opened.startDetectedAt()).isEqualTo(OBSERVED_AT);
        assertThat(opened.startSource()).isEqualTo("REST_STATUS");
        assertThat(opened.platformLiveTime()).isEqualTo(PLATFORM_LIVE_TIME);
        verify(repository).lockMonitor(room.id());

        ArgumentCaptor<BilibiliLiveSession> candidate = ArgumentCaptor.forClass(BilibiliLiveSession.class);
        verify(repository).insertOpen(candidate.capture());
        assertThat(candidate.getValue().liveKey()).startsWith("platform:");
        assertThat(candidate.getValue().titleAtStart()).isEqualTo(snapshot.title());
    }

    @Test
    void ingestionLockDelegatesToMonitorRowLock() {
        service.lockForIngestion(11L);

        verify(repository).lockMonitor(11L);
    }

    @Test
    void detectsPendingSignalNewerThanRestSnapshot() {
        BilibiliLiveSession pending = activeSession("END_PENDING", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME);
        OffsetDateTime staleFetchedAt = pending.endSignalAt().minusSeconds(1);
        when(repository.findActiveForUpdate(pending.monitorId())).thenReturn(Optional.of(pending));

        boolean newerPending = service.hasNewerPendingSignal(pending.monitorId(), staleFetchedAt);

        assertThat(newerPending).isTrue();
    }

    @Test
    void restOfflineClosesActiveSession() {
        BilibiliLiveRoomMonitor room = room(1, PLATFORM_LIVE_TIME);
        BilibiliFetchedLiveRoomSnapshot snapshot = snapshot(0, null, OBSERVED_AT);
        BilibiliLiveSession active = activeSession("OPEN", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME);
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(active));

        Optional<BilibiliLiveSession> result = service.reconcileRest(room, snapshot);

        assertThat(result).isEmpty();
        verify(repository, never()).insertOpen(any());
    }

    @Test
    void restLiveWithSamePlatformTimeCancelsEndPending() {
        BilibiliLiveRoomMonitor room = room(1, PLATFORM_LIVE_TIME);
        BilibiliFetchedLiveRoomSnapshot snapshot = snapshot(1, PLATFORM_LIVE_TIME, OBSERVED_AT);
        BilibiliLiveSession pending = activeSession("END_PENDING", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME);
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(pending));

        BilibiliLiveSession result = service.reconcileRest(room, snapshot).orElseThrow();

        assertThat(result.state()).isEqualTo("OPEN");
        assertThat(result.endSignalAt()).isNull();
        assertThat(result.endSource()).isNull();
        assertThat(result.lastLiveObservedAt()).isEqualTo(OBSERVED_AT);
        assertThat(result.titleAtEnd()).isNull();
        verify(repository).update(result);
        verify(repository, never()).insertOpen(any());
    }

    @Test
    void olderLiveRestSnapshotDoesNotCancelNewerPreparingSignal() {
        BilibiliLiveRoomMonitor room = room(1, PLATFORM_LIVE_TIME);
        BilibiliLiveSession pending = activeSession("END_PENDING", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME);
        OffsetDateTime staleFetchedAt = pending.endSignalAt().minusSeconds(1);
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(pending));

        Optional<BilibiliLiveSession> result = service.reconcileRest(
                room,
                snapshot(1, PLATFORM_LIVE_TIME, staleFetchedAt)
        );

        assertThat(result).contains(pending);
        assertThat(result.orElseThrow().endSignalAt()).isEqualTo(pending.endSignalAt());
        verify(repository, never()).update(any());
        verify(repository, never()).insertOpen(any());
    }

    @Test
    void olderOfflineRestSnapshotDoesNotConfirmNewerPreparingSignal() {
        BilibiliLiveRoomMonitor room = room(1, PLATFORM_LIVE_TIME);
        BilibiliLiveSession pending = activeSession("END_PENDING", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME);
        OffsetDateTime staleFetchedAt = pending.endSignalAt().minusSeconds(1);
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(pending));

        Optional<BilibiliLiveSession> result = service.reconcileRest(
                room,
                snapshot(0, null, staleFetchedAt)
        );

        assertThat(result).contains(pending);
        assertThat(result.orElseThrow().endedAt()).isNull();
        assertThat(result.orElseThrow().endDetectedAt()).isNull();
        verify(repository, never()).update(any());
        verify(repository, never()).insertOpen(any());
    }

    @Test
    void changedPlatformLiveTimeClosesOldSessionAndOpensRestart() {
        OffsetDateTime restartedAt = PLATFORM_LIVE_TIME.plusHours(3);
        OffsetDateTime detectedAt = restartedAt.plusSeconds(8);
        BilibiliLiveRoomMonitor room = room(1, PLATFORM_LIVE_TIME);
        BilibiliFetchedLiveRoomSnapshot snapshot = snapshot(1, restartedAt, detectedAt);
        BilibiliLiveSession active = activeSession("OPEN", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME);
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(active));
        when(repository.insertOpen(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 102L));

        BilibiliLiveSession result = service.reconcileRest(room, snapshot).orElseThrow();

        assertThat(result.id()).isEqualTo(102L);
        assertThat(result.platformLiveTime()).isEqualTo(restartedAt);
        assertThat(result.startedAt()).isEqualTo(restartedAt);
        ArgumentCaptor<BilibiliLiveSession> closed = ArgumentCaptor.forClass(BilibiliLiveSession.class);
        verify(repository).update(closed.capture());
        assertThat(closed.getValue().state()).isEqualTo("CLOSED");
        assertThat(closed.getValue().endSource()).isEqualTo("RESTART_DETECTED");
    }

    @Test
    void preparingSignalOnlyMarksEndPendingAndSchedulesImmediateRestCheck() {
        BilibiliLiveRoomMonitor room = room(1, PLATFORM_LIVE_TIME);
        BilibiliLiveSession active = activeSession("OPEN", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME);
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(active));

        BilibiliLiveSession result = service.observePreparingSignal(room, OBSERVED_AT).orElseThrow();

        assertThat(result.state()).isEqualTo("END_PENDING");
        assertThat(result.endSignalAt()).isEqualTo(OBSERVED_AT);
        assertThat(result.endedAt()).isNull();
        verify(repository).update(result);
        verify(repository).scheduleImmediateCollection(room.id());
    }

    @Test
    void eventActivityCancelsEndPending() {
        BilibiliLiveRoomMonitor room = room(1, PLATFORM_LIVE_TIME);
        BilibiliLiveSession pending = activeSession("END_PENDING", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME);
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(pending));

        BilibiliLiveSession result = service.ensureActiveForEvent(room, OBSERVED_AT);

        assertThat(result.state()).isEqualTo("OPEN");
        assertThat(result.endSignalAt()).isNull();
        assertThat(result.endSource()).isNull();
        assertThat(result.lastObservedAt()).isEqualTo(OBSERVED_AT);
        verify(repository).update(result);
    }

    @Test
    void liveSignalWithNoSessionOpensUsingProvidedIdentity() {
        BilibiliLiveRoomMonitor room = room(0, null);
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.empty());
        when(repository.insertOpen(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 103L));

        BilibiliLiveSession result = service.observeLiveSignal(
                room,
                OBSERVED_AT,
                PLATFORM_LIVE_TIME,
                "ws-live-key"
        );

        assertThat(result.state()).isEqualTo("OPEN");
        assertThat(result.startSource()).isEqualTo("WS_LIVE");
        assertThat(result.platformLiveTime()).isEqualTo(PLATFORM_LIVE_TIME);
        assertThat(result.liveKey()).isEqualTo("ws-live-key");
        verify(repository, times(1)).insertOpen(any());
    }

    @Test
    void changedRealWebSocketLiveKeyClosesOldSessionAndOpensRestart() {
        BilibiliLiveRoomMonitor room = room(1, null);
        BilibiliLiveSession active = withLiveKey(activeSession("OPEN", null, PLATFORM_LIVE_TIME), "ws-key-1");
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(active));
        when(repository.insertOpen(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 104L));

        BilibiliLiveSession result = service.observeLiveSignal(
                room, OBSERVED_AT, null, "ws-key-2"
        );

        assertThat(result.id()).isEqualTo(104L);
        assertThat(result.liveKey()).isEqualTo("ws-key-2");
        ArgumentCaptor<BilibiliLiveSession> closed = ArgumentCaptor.forClass(BilibiliLiveSession.class);
        verify(repository).update(closed.capture());
        assertThat(closed.getValue().state()).isEqualTo("CLOSED");
        assertThat(closed.getValue().endSource()).isEqualTo("RESTART_DETECTED");
    }

    @Test
    void firstRealWebSocketKeyUpgradesSyntheticIdentityWithoutRestart() {
        BilibiliLiveRoomMonitor room = room(1, null);
        BilibiliLiveSession synthetic = withLiveKey(
                activeSession("OPEN", null, PLATFORM_LIVE_TIME),
                "activity:" + PLATFORM_LIVE_TIME.toInstant()
        );
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(synthetic));

        BilibiliLiveSession result = service.observeLiveSignal(
                room, OBSERVED_AT, null, "ws-key-1"
        );

        assertThat(result.id()).isEqualTo(synthetic.id());
        assertThat(result.liveKey()).isEqualTo("ws-key-1");
        verify(repository).update(result);
        verify(repository, never()).insertOpen(any());
    }

    @Test
    void authoritativeRestTimeRefinesActivityOpenedStartWithoutChangingDetectionTime() {
        OffsetDateTime activityAt = PLATFORM_LIVE_TIME.plusMinutes(1);
        BilibiliLiveRoomMonitor room = room(1, null);
        BilibiliLiveSession activitySession = withLiveKey(
                activeSession("OPEN", null, activityAt),
                "activity:" + activityAt.toInstant()
        );
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(activitySession));

        BilibiliLiveSession result = service.reconcileRest(
                room,
                snapshot(1, PLATFORM_LIVE_TIME, OBSERVED_AT)
        ).orElseThrow();

        assertThat(result.startedAt()).isEqualTo(PLATFORM_LIVE_TIME);
        assertThat(result.startDetectedAt()).isEqualTo(activitySession.startDetectedAt());
        assertThat(result.platformLiveTime()).isEqualTo(PLATFORM_LIVE_TIME);
    }

    @Test
    void authoritativeWebSocketTimeRefinesActivityOpenedStartWithoutChangingDetectionTime() {
        OffsetDateTime activityAt = PLATFORM_LIVE_TIME.plusMinutes(1);
        BilibiliLiveRoomMonitor room = room(1, null);
        BilibiliLiveSession activitySession = withLiveKey(
                activeSession("OPEN", null, activityAt),
                "activity:" + activityAt.toInstant()
        );
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(activitySession));

        BilibiliLiveSession result = service.observeLiveSignal(
                room, OBSERVED_AT, activityAt, PLATFORM_LIVE_TIME, "live-key-1"
        );

        assertThat(result.startedAt()).isEqualTo(PLATFORM_LIVE_TIME);
        assertThat(result.startDetectedAt()).isEqualTo(activitySession.startDetectedAt());
        assertThat(result.platformLiveTime()).isEqualTo(PLATFORM_LIVE_TIME);
        assertThat(result.liveKey()).isEqualTo("live-key-1");
        verify(repository).update(result);
    }

    @Test
    void restConfirmationAfterPreparingUsesSignalTimeAndRestDetectionTime() {
        OffsetDateTime preparingAt = OBSERVED_AT.minusSeconds(10);
        BilibiliLiveRoomMonitor room = room(1, PLATFORM_LIVE_TIME);
        BilibiliLiveSession pending = activeSession("END_PENDING", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME);
        assertThat(pending.endSignalAt()).isEqualTo(preparingAt);
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(pending));

        service.reconcileRest(room, snapshot(0, null, OBSERVED_AT));

        ArgumentCaptor<BilibiliLiveSession> closed = ArgumentCaptor.forClass(BilibiliLiveSession.class);
        verify(repository).update(closed.capture());
        assertThat(closed.getValue().endedAt()).isEqualTo(preparingAt);
        assertThat(closed.getValue().endDetectedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void delayedPreparingWithDifferentRealKeyDoesNotEndCurrentSession() {
        BilibiliLiveRoomMonitor room = room(1, null);
        BilibiliLiveSession active = withLiveKey(activeSession("OPEN", null, PLATFORM_LIVE_TIME), "live-key-2");
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(active));

        Optional<BilibiliLiveSession> result = service.observePreparingSignal(
                room, OBSERVED_AT, OBSERVED_AT, "live-key-1"
        );

        assertThat(result).contains(active);
        verify(repository, never()).update(any());
        verify(repository).scheduleImmediateCollection(room.id());
    }

    @Test
    void preparingBeforeCurrentStartDoesNotEndCurrentSession() {
        BilibiliLiveRoomMonitor room = room(1, null);
        BilibiliLiveSession active = withLiveKey(activeSession("OPEN", null, PLATFORM_LIVE_TIME), "live-key-2");
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(active));

        Optional<BilibiliLiveSession> result = service.observePreparingSignal(
                room, OBSERVED_AT, PLATFORM_LIVE_TIME.minusSeconds(1), "live-key-2"
        );

        assertThat(result).contains(active);
        verify(repository, never()).update(any());
    }

    @Test
    void restRecoveryReopensClosedSessionWithSamePlatformTime() {
        BilibiliLiveRoomMonitor room = room(0, null);
        BilibiliLiveSession closed = closedSession(PLATFORM_LIVE_TIME, "platform:" + PLATFORM_LIVE_TIME.toInstant());
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.empty());
        when(repository.findByPlatformLiveTimeForUpdate(room.id(), PLATFORM_LIVE_TIME))
                .thenReturn(Optional.of(closed));

        BilibiliLiveSession result = service.reconcileRest(
                room,
                snapshot(1, PLATFORM_LIVE_TIME, OBSERVED_AT)
        ).orElseThrow();

        assertThat(result.id()).isEqualTo(closed.id());
        assertThat(result.state()).isEqualTo("OPEN");
        assertThat(result.endedAt()).isNull();
        assertThat(result.endDetectedAt()).isNull();
        verify(repository).update(result);
        verify(repository, never()).insertOpen(any());
    }

    @Test
    void lateLiveSignalIsAttributedToClosedSessionWithoutRestartingNewerActiveSession() {
        OffsetDateTime oldLiveTime = PLATFORM_LIVE_TIME.minusHours(2);
        BilibiliLiveRoomMonitor room = room(1, PLATFORM_LIVE_TIME);
        BilibiliLiveSession current = withLiveKey(
                activeSession("OPEN", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME), "live-key-2"
        );
        BilibiliLiveSession oldClosed = closedSession(oldLiveTime, "live-key-1");
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(current));
        when(repository.findByPlatformLiveTimeForUpdate(room.id(), oldLiveTime))
                .thenReturn(Optional.of(oldClosed));

        BilibiliLiveSession result = service.observeLiveSignal(
                room, OBSERVED_AT, oldLiveTime.plusMinutes(1), oldLiveTime, "live-key-1"
        );

        assertThat(result).isEqualTo(oldClosed);
        verify(repository, never()).update(any());
        verify(repository, never()).insertOpen(any());
    }

    @Test
    void lateActivityDoesNotCancelNewerEndPendingSignal() {
        BilibiliLiveRoomMonitor room = room(1, PLATFORM_LIVE_TIME);
        BilibiliLiveSession pending = activeSession("END_PENDING", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME);
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(pending));

        BilibiliLiveSession result = service.ensureActiveForEvent(
                room, OBSERVED_AT.plusMinutes(1), pending.endSignalAt().minusSeconds(1)
        );

        assertThat(result).isEqualTo(pending);
        verify(repository, never()).update(any());
        verify(repository, never()).insertOpen(any());
    }

    @Test
    void eventBeforeCurrentStartIsAttributedToHistoricalSession() {
        OffsetDateTime occurredAt = PLATFORM_LIVE_TIME.minusHours(1);
        BilibiliLiveRoomMonitor room = room(1, PLATFORM_LIVE_TIME);
        BilibiliLiveSession current = activeSession("OPEN", PLATFORM_LIVE_TIME, PLATFORM_LIVE_TIME);
        BilibiliLiveSession historical = closedSession(occurredAt.minusMinutes(5), "old-live-key");
        when(repository.findActiveForUpdate(room.id())).thenReturn(Optional.of(current));
        when(repository.findByEventTimeForUpdate(room.id(), occurredAt)).thenReturn(Optional.of(historical));

        BilibiliLiveSession result = service.ensureActiveForEvent(room, OBSERVED_AT, occurredAt);

        assertThat(result).isEqualTo(historical);
        verify(repository, never()).update(any());
    }

    private BilibiliLiveRoomMonitor room(int liveStatus, OffsetDateTime liveTime) {
        return new BilibiliLiveRoomMonitor(
                11L, 22L, 33L, null, "anchor", null, "old title", null, null,
                null, null, null, null, liveStatus, liveTime, 100L, 200L,
                "ACTIVE", 300, OBSERVED_AT, OBSERVED_AT.minusMinutes(5), OBSERVED_AT.minusMinutes(5),
                null, null, null, null, "status", OBSERVED_AT.minusDays(1), OBSERVED_AT.minusMinutes(5)
        );
    }

    private BilibiliFetchedLiveRoomSnapshot snapshot(
            int liveStatus,
            OffsetDateTime liveTime,
            OffsetDateTime fetchedAt
    ) {
        return new BilibiliFetchedLiveRoomSnapshot(
                22L, 33L, null, "anchor", null, "new title", null, null,
                null, null, null, null, liveStatus, liveTime, 120L, 200L,
                fetchedAt, "status", "{}"
        );
    }

    private BilibiliLiveSession withId(BilibiliLiveSession session, Long id) {
        return new BilibiliLiveSession(
                id,
                session.monitorId(),
                session.uid(),
                session.roomId(),
                session.state(),
                session.platformLiveTime(),
                session.liveKey(),
                session.startedAt(),
                session.startDetectedAt(),
                session.startSource(),
                session.endSignalAt(),
                session.endedAt(),
                session.endDetectedAt(),
                session.endSource(),
                session.lastLiveObservedAt(),
                session.lastObservedAt(),
                session.titleAtStart(),
                session.titleAtEnd(),
                session.createdAt(),
                session.updatedAt()
        );
    }

    private BilibiliLiveSession withLiveKey(BilibiliLiveSession session, String liveKey) {
        return new BilibiliLiveSession(
                session.id(), session.monitorId(), session.uid(), session.roomId(), session.state(),
                session.platformLiveTime(), liveKey, session.startedAt(), session.startDetectedAt(),
                session.startSource(), session.endSignalAt(), session.endedAt(), session.endDetectedAt(),
                session.endSource(), session.lastLiveObservedAt(), session.lastObservedAt(),
                session.titleAtStart(), session.titleAtEnd(), session.createdAt(), session.updatedAt()
        );
    }

    private BilibiliLiveSession activeSession(
            String state,
            OffsetDateTime platformLiveTime,
            OffsetDateTime startedAt
    ) {
        return new BilibiliLiveSession(
                90L, 11L, 22L, 33L, state, platformLiveTime,
                platformLiveTime == null ? "activity:1" : "platform:" + platformLiveTime.toInstant(),
                startedAt, startedAt.plusSeconds(5), "REST_STATUS",
                "END_PENDING".equals(state) ? OBSERVED_AT.minusSeconds(10) : null,
                null, null, "END_PENDING".equals(state) ? "WS_PREPARING" : null,
                OBSERVED_AT.minusSeconds(20), OBSERVED_AT.minusSeconds(10),
                "old title", null, startedAt, OBSERVED_AT.minusSeconds(10)
        );
    }

    private BilibiliLiveSession closedSession(OffsetDateTime platformLiveTime, String liveKey) {
        OffsetDateTime endedAt = OBSERVED_AT.minusSeconds(30);
        return new BilibiliLiveSession(
                90L, 11L, 22L, 33L, "CLOSED", platformLiveTime, liveKey,
                platformLiveTime, platformLiveTime.plusSeconds(5), "WS_EVENT_ACTIVITY",
                endedAt, endedAt, endedAt.plusSeconds(5), "REST_STATUS",
                endedAt.minusSeconds(10), endedAt.plusSeconds(5),
                "old title", "old title", platformLiveTime, endedAt.plusSeconds(5)
        );
    }
}
