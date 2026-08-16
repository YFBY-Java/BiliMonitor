package com.socialmonitor.bilibili.live.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.socialmonitor.bilibili.live.client.BilibiliLiveApiClient;
import com.socialmonitor.bilibili.live.config.BilibiliLiveMonitorProperties;
import com.socialmonitor.bilibili.live.domain.BilibiliFetchedLiveRoomSnapshot;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.dto.AddBilibiliLiveRoomMonitorRequest;
import com.socialmonitor.bilibili.live.repository.BilibiliLiveMonitorRepository;
import com.socialmonitor.bilibili.live.session.service.BilibiliLiveSessionBoundaryService;
import com.socialmonitor.collector.service.RateLimitService;
import com.socialmonitor.collector.service.RetryPolicy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveMonitorServiceTests {

    private static final OffsetDateTime LIVE_TIME = OffsetDateTime.of(
            2026, 8, 16, 20, 0, 0, 0, ZoneOffset.ofHours(8)
    );
    private static final OffsetDateTime FETCHED_AT = LIVE_TIME.plusMinutes(1);

    @Mock
    private BilibiliLiveMonitorRepository repository;
    @Mock
    private BilibiliLiveApiClient apiClient;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private RetryPolicy retryPolicy;
    @Mock
    private BilibiliLiveSessionBoundaryService boundaryService;
    @Mock
    private BilibiliLiveSnapshotApplicationService snapshotApplicationService;

    private BilibiliLiveMonitorService service;

    @BeforeEach
    void setUp() {
        BilibiliLiveMonitorProperties properties = new BilibiliLiveMonitorProperties();
        properties.setMaxAttempts(1);
        properties.setRequestMinIntervalMs(0);
        service = new BilibiliLiveMonitorService(
                repository, apiClient, properties, rateLimitService, retryPolicy,
                snapshotApplicationService
        );
    }

    @Test
    void addRoomDelegatesAtomicInitializationToSnapshotCoordinator() {
        BilibiliFetchedLiveRoomSnapshot snapshot = snapshot(1, LIVE_TIME);
        BilibiliLiveRoomMonitor persisted = room(1, LIVE_TIME);
        when(apiClient.fetchStatusByUids(List.of(22L))).thenReturn(Map.of(22L, snapshot));
        when(apiClient.fetchRoomInfo(33L)).thenReturn(snapshot);
        when(snapshotApplicationService.initializeMonitor(any(), eq(300))).thenReturn(persisted);
        when(repository.findRecentSnapshots(persisted.id(), 48)).thenReturn(List.of());

        service.addRoom(new AddBilibiliLiveRoomMonitorRequest(22L, null, 300));

        verify(snapshotApplicationService).initializeMonitor(
                any(BilibiliFetchedLiveRoomSnapshot.class), eq(300)
        );
        verify(repository, never()).upsertMonitorFromSnapshot(any(), any(Integer.class), any());
        verify(repository, never()).upsertSnapshot(any(), any());
        verify(boundaryService, never()).reconcileRest(any(), any());
    }

    @Test
    void successfulRefreshReconcilesSessionBoundary() {
        BilibiliLiveRoomMonitor previous = room(0, null);
        BilibiliFetchedLiveRoomSnapshot liveSnapshot = snapshot(1, LIVE_TIME);
        when(repository.findById(previous.id())).thenReturn(java.util.Optional.of(previous));
        when(apiClient.fetchStatusByUids(List.of(previous.uid())))
                .thenReturn(Map.of(previous.uid(), liveSnapshot));
        when(apiClient.fetchRoomInfo(previous.roomId())).thenReturn(liveSnapshot);

        service.refreshNow(previous.id());

        verify(snapshotApplicationService).applySuccessfulSnapshot(
                eq(previous.id()), any(BilibiliFetchedLiveRoomSnapshot.class)
        );
    }

    @Test
    void staleSuccessfulResponseIsReportedAsIgnoredInsteadOfOk() {
        BilibiliLiveRoomMonitor previous = room(1, LIVE_TIME);
        BilibiliFetchedLiveRoomSnapshot staleSnapshot = snapshot(0, null);
        when(repository.findById(previous.id())).thenReturn(java.util.Optional.of(previous));
        when(apiClient.fetchStatusByUids(List.of(previous.uid())))
                .thenReturn(Map.of(previous.uid(), staleSnapshot));
        when(apiClient.fetchRoomInfo(previous.roomId())).thenReturn(staleSnapshot);
        when(snapshotApplicationService.applySuccessfulSnapshot(eq(previous.id()), any()))
                .thenReturn(false);

        var result = service.refreshNow(previous.id());

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("ignored-stale");
    }

    private BilibiliFetchedLiveRoomSnapshot snapshot(int liveStatus, OffsetDateTime liveTime) {
        return new BilibiliFetchedLiveRoomSnapshot(
                22L, 33L, null, "anchor", null, "title", null, null,
                null, null, null, null, liveStatus, liveTime, 100L, 200L,
                FETCHED_AT, BilibiliLiveApiClient.STATUS_BY_UIDS_ENDPOINT, "{}"
        );
    }

    private BilibiliLiveRoomMonitor room(int liveStatus, OffsetDateTime liveTime) {
        return new BilibiliLiveRoomMonitor(
                11L, 22L, 33L, null, "anchor", null, "title", null, null,
                null, null, null, null, liveStatus, liveTime, 100L, 200L,
                "ACTIVE", 300, FETCHED_AT.plusMinutes(5), FETCHED_AT, FETCHED_AT,
                null, null, null, null, BilibiliLiveApiClient.STATUS_BY_UIDS_ENDPOINT,
                FETCHED_AT.minusDays(1), FETCHED_AT
        );
    }
}
