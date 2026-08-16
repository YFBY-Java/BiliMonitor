package com.socialmonitor.bilibili.live.danmaku.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.socialmonitor.bilibili.live.danmaku.config.BilibiliLiveDanmakuProperties;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.Actor;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.EventKind;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.Metrics;
import com.socialmonitor.bilibili.live.danmaku.repository.BilibiliLiveDanmakuRepository;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.session.domain.BilibiliLiveSession;
import com.socialmonitor.bilibili.live.session.repository.BilibiliLiveSessionEventRepository;
import com.socialmonitor.bilibili.live.session.service.BilibiliLiveSessionBoundaryService;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveEventIngestionServiceTests {

    private static final OffsetDateTime EVENT_TIME = OffsetDateTime.of(
            2026, 8, 16, 20, 0, 0, 0, ZoneOffset.ofHours(8)
    );

    @Mock
    private BilibiliLiveSessionEventRepository eventRepository;
    @Mock
    private BilibiliLiveSessionBoundaryService boundaryService;
    @Mock
    private BilibiliLiveDanmakuRepository legacyRepository;

    private BilibiliLiveEventIngestionService service;
    private BilibiliLiveDanmakuProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BilibiliLiveDanmakuProperties();
        properties.setBucketSeconds(60);
        properties.setRecentMessageLimitPerRoom(30);
        service = new BilibiliLiveEventIngestionService(
                eventRepository, boundaryService, legacyRepository, properties
        );
    }

    @Test
    void insertionAndLegacyProjectionShareOneTransaction() throws Exception {
        Method ingest = BilibiliLiveEventIngestionService.class.getMethod(
                "ingest",
                BilibiliLiveRoomMonitor.class,
                Long.class,
                long.class,
                Integer.class,
                BilibiliLiveDanmakuEvent.class,
                String.class
        );

        assertThat(ingest.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void duplicateStrongIdStillProjectsLegacyButDoesNotRerouteBoundary() {
        BilibiliLiveDanmakuEvent event = danmaku();
        when(eventRepository.existsByStrongSourceId(11L, EventKind.DANMAKU, "id-1")).thenReturn(true);

        boolean inserted = service.ingest(room(), 71L, 1L, 3, event, "Resolved Alice");

        assertThat(inserted).isFalse();
        InOrder order = inOrder(boundaryService, eventRepository, legacyRepository);
        order.verify(boundaryService).lockForIngestion(11L);
        order.verify(eventRepository).existsByStrongSourceId(11L, EventKind.DANMAKU, "id-1");
        order.verify(legacyRepository).recordMetricEvent(
                11L, 71L, 33L, EVENT_TIME, 60,
                1, null, null, null, null, 0, 0, 1
        );
        verify(legacyRepository).insertRecent(11L, 33L, "hello", "Resolved Alice", "Fans", EVENT_TIME);
        verify(legacyRepository).trimRecent(11L, 30);
        verify(boundaryService, never()).ensureActiveForEvent(any(), any(), any());
        verify(eventRepository, never()).insertIfAbsent(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), any(), any()
        );
    }

    @Test
    void newlyInsertedDanmakuUpdatesLegacyProjectionAfterDeduplicatedInsert() {
        BilibiliLiveDanmakuEvent event = danmaku();
        BilibiliLiveSession liveSession = liveSession();
        when(boundaryService.ensureActiveForEvent(room(), event.receivedAt(), EVENT_TIME)).thenReturn(liveSession);
        when(eventRepository.insertIfAbsent(
                liveSession.id(), 11L, 33L, 71L, 1L, 3, event, "Resolved Alice"
        )).thenReturn(true);

        assertThat(service.ingest(room(), 71L, 1L, 3, event, "Resolved Alice")).isTrue();

        InOrder order = inOrder(eventRepository, legacyRepository);
        order.verify(eventRepository).insertIfAbsent(
                liveSession.id(), 11L, 33L, 71L, 1L, 3, event, "Resolved Alice"
        );
        order.verify(legacyRepository).recordMetricEvent(
                11L, 71L, 33L, EVENT_TIME, 60,
                1, null, null, null, null, 0, 0, 1
        );
        order.verify(legacyRepository).insertRecent(
                11L, 33L, "hello", "Resolved Alice", "Fans", EVENT_TIME
        );
        order.verify(legacyRepository).trimRecent(11L, 30);
    }

    @Test
    void liveSignalUsesLiveBoundaryAndDoesNotUseGenericActivityBoundary() {
        OffsetDateTime liveStartedAt = EVENT_TIME.minusMinutes(5);
        BilibiliLiveDanmakuEvent live = new BilibiliLiveDanmakuEvent(
                "LIVE", EventKind.LIVE, "LIVE:msg-1", "msg-1", "{\"cmd\":\"LIVE\"}",
                liveStartedAt, EVENT_TIME, liveStartedAt, "live-key",
                null, null, Metrics.empty(), null, null, null, null
        );
        when(boundaryService.observeLiveSignal(
                room(), live.receivedAt(), live.occurredAt(), live.liveStartedAt(), "live-key"
        ))
                .thenReturn(liveSession());
        when(eventRepository.insertIfAbsent(90L, 11L, 33L, 71L, 1L, 3, live, null)).thenReturn(true);

        assertThat(service.ingest(room(), 71L, 1L, 3, live, null)).isTrue();

        verify(boundaryService).observeLiveSignal(
                room(), live.receivedAt(), live.occurredAt(), live.liveStartedAt(), "live-key"
        );
        verify(boundaryService, never()).ensureActiveForEvent(any(), any(), any());
        verify(legacyRepository).recordMetricEvent(
                11L, 71L, 33L, liveStartedAt, 60,
                0, null, null, null, null, 0, 0, 1
        );
    }

    @Test
    void preparingSignalUsesPreparingBoundaryAndPersistsAgainstActiveSession() {
        BilibiliLiveDanmakuEvent preparing = control(
                EventKind.PREPARING, "PREPARING:msg-2", null, "preparing-live-key"
        );
        when(boundaryService.observePreparingSignal(
                room(), preparing.receivedAt(), preparing.occurredAt(), preparing.liveKey()
        )).thenReturn(Optional.of(liveSession()));
        when(eventRepository.insertIfAbsent(90L, 11L, 33L, 71L, 1L, 3, preparing, null)).thenReturn(true);

        assertThat(service.ingest(room(), 71L, 1L, 3, preparing, null)).isTrue();

        verify(boundaryService).observePreparingSignal(
                room(), preparing.receivedAt(), preparing.occurredAt(), preparing.liveKey()
        );
        verify(boundaryService, never()).ensureActiveForEvent(any(), any(), any());
    }

    @Test
    void preparingWithoutActiveSessionIsHandledWithoutInventingOne() {
        BilibiliLiveDanmakuEvent preparing = control(EventKind.PREPARING, "PREPARING:msg-3", null, null);
        when(boundaryService.observePreparingSignal(
                room(), preparing.receivedAt(), preparing.occurredAt(), preparing.liveKey()
        )).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() ->
                assertThat(service.ingest(room(), 71L, 1L, 3, preparing, null)).isFalse()
        );

        verify(eventRepository).existsByStrongSourceId(11L, EventKind.PREPARING, "msg-3");
        verify(eventRepository, never()).insertIfAbsent(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), any(), any()
        );
        verify(boundaryService, never()).ensureActiveForEvent(any(), any(), any());
        verifyLegacyRawProjection(preparing);
    }

    @Test
    void metricsUseExistingSessionWithoutCreatingOne() {
        BilibiliLiveDanmakuEvent metrics = new BilibiliLiveDanmakuEvent(
                "WATCHED_CHANGE", EventKind.METRICS, "WATCHED_CHANGE:hash", null,
                "{\"cmd\":\"WATCHED_CHANGE\"}", EVENT_TIME, EVENT_TIME,
                null, null, null, null, new Metrics(null, null, 123L),
                null, null, null, null
        );
        when(boundaryService.findActive(11L)).thenReturn(Optional.of(liveSession()));
        when(eventRepository.insertIfAbsent(90L, 11L, 33L, 71L, 1L, 3, metrics, null)).thenReturn(true);

        assertThat(service.ingest(room(), 71L, 1L, 3, metrics, null)).isTrue();

        verify(boundaryService).findActive(11L);
        verify(boundaryService, never()).ensureActiveForEvent(any(), any(), any());
        verify(legacyRepository).recordMetricEvent(
                11L, 71L, 33L, EVENT_TIME, 60,
                0, null, null, 123L, null, 0, 0, 1
        );
    }

    @Test
    void notificationWithoutActiveSessionDoesNotCreateOrPersistOne() {
        BilibiliLiveDanmakuEvent notification = control(
                EventKind.NOTIFICATION, "COMBO_SEND:hash", null, null
        );
        when(boundaryService.findActive(11L)).thenReturn(Optional.empty());

        assertThat(service.ingest(room(), 71L, 1L, 3, notification, null)).isFalse();

        verify(boundaryService).findActive(11L);
        verify(boundaryService, never()).ensureActiveForEvent(any(), any(), any());
        verify(eventRepository).existsByStrongSourceId(11L, EventKind.NOTIFICATION, "hash");
        verify(eventRepository, never()).insertIfAbsent(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), any(), any()
        );
        verifyLegacyRawProjection(notification);
    }

    @Test
    void unknownEventIsIgnoredBeforeCreatingSession() {
        BilibiliLiveDanmakuEvent unknown = control(EventKind.UNKNOWN, "UNKNOWN:hash", null, null);

        assertThat(service.ingest(room(), 71L, 1L, 3, unknown, null)).isFalse();

        verifyNoInteractions(eventRepository, boundaryService);
        verifyLegacyRawProjection(unknown);
    }

    @Test
    void identicalNoIdReceiptsReachPersistenceWithDistinctOrdinals() {
        BilibiliLiveDanmakuEvent noId = new BilibiliLiveDanmakuEvent(
                "DANMU_MSG", EventKind.DANMAKU, null, null, "{}", EVENT_TIME,
                EVENT_TIME.plusSeconds(1), null, null, null, null, Metrics.empty(),
                "same", null, null, null
        );
        when(boundaryService.ensureActiveForEvent(room(), noId.receivedAt(), noId.occurredAt()))
                .thenReturn(liveSession());
        when(eventRepository.insertIfAbsent(90L, 11L, 33L, 71L, 1L, 3, noId, null)).thenReturn(true);
        when(eventRepository.insertIfAbsent(90L, 11L, 33L, 71L, 2L, 3, noId, null)).thenReturn(true);

        assertThat(service.ingest(room(), 71L, 1L, 3, noId, null)).isTrue();
        assertThat(service.ingest(room(), 71L, 2L, 3, noId, null)).isTrue();

        verify(eventRepository).insertIfAbsent(90L, 11L, 33L, 71L, 1L, 3, noId, null);
        verify(eventRepository).insertIfAbsent(90L, 11L, 33L, 71L, 2L, 3, noId, null);
        verify(eventRepository, never()).existsByStrongSourceId(anyLong(), any(), any());
    }

    @Test
    void lateReceiptWithoutHistoricalMatchDoesNotAttachToNewerSession() {
        BilibiliLiveDanmakuEvent late = new BilibiliLiveDanmakuEvent(
                "DANMU_MSG", EventKind.DANMAKU, null, null, "{}", EVENT_TIME,
                EVENT_TIME.plusMinutes(10), null, null, null, null, Metrics.empty(),
                "late", null, null, null
        );
        BilibiliLiveSession newer = new BilibiliLiveSession(
                91L, 11L, 22L, 33L, "OPEN", null, "new-live-key",
                EVENT_TIME.plusMinutes(1), EVENT_TIME.plusMinutes(1), "WS_LIVE",
                null, null, null, null, EVENT_TIME.plusMinutes(1), EVENT_TIME.plusMinutes(1),
                "new title", null, EVENT_TIME.plusMinutes(1), EVENT_TIME.plusMinutes(1)
        );
        when(boundaryService.ensureActiveForEvent(room(), late.receivedAt(), late.occurredAt()))
                .thenReturn(newer);

        assertThat(service.ingest(room(), 71L, 7L, 3, late, null)).isFalse();

        verify(eventRepository, never()).insertIfAbsent(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), any(), any()
        );
        verifyLegacyRawProjection(late);
    }

    @Test
    void eventAfterClosedSessionEndIsNotCanonicallyAttached() {
        BilibiliLiveDanmakuEvent late = new BilibiliLiveDanmakuEvent(
                "DANMU_MSG", EventKind.DANMAKU, null, null, "{}", EVENT_TIME.plusMinutes(2),
                EVENT_TIME.plusMinutes(3), null, null, null, null, Metrics.empty(),
                "late", null, null, null
        );
        BilibiliLiveSession closed = new BilibiliLiveSession(
                89L, 11L, 22L, 33L, "CLOSED", null, "old-live-key",
                EVENT_TIME.minusMinutes(5), EVENT_TIME.minusMinutes(5), "WS_LIVE",
                EVENT_TIME, EVENT_TIME, EVENT_TIME, "REST_STATUS", null, EVENT_TIME,
                "old title", "old title", EVENT_TIME.minusMinutes(5), EVENT_TIME
        );
        when(boundaryService.ensureActiveForEvent(room(), late.receivedAt(), late.occurredAt()))
                .thenReturn(closed);

        assertThat(service.ingest(room(), 71L, 8L, 3, late, null)).isFalse();

        verify(eventRepository, never()).insertIfAbsent(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), any(), any()
        );
        verifyLegacyRawProjection(late);
    }

    @Test
    void insertConflictAfterRoutingStillProjectsEveryLegacyReceipt() {
        BilibiliLiveDanmakuEvent event = danmaku();
        when(boundaryService.ensureActiveForEvent(room(), event.receivedAt(), event.occurredAt()))
                .thenReturn(liveSession());
        when(eventRepository.insertIfAbsent(
                90L, 11L, 33L, 71L, 6L, 3, event, "Resolved Alice"
        )).thenReturn(false);

        assertThat(service.ingest(room(), 71L, 6L, 3, event, "Resolved Alice")).isFalse();

        verify(legacyRepository).recordMetricEvent(
                11L, 71L, 33L, EVENT_TIME, 60,
                1, null, null, null, null, 0, 0, 1
        );
        verify(legacyRepository).insertRecent(
                11L, 33L, "hello", "Resolved Alice", "Fans", EVENT_TIME
        );
        verify(legacyRepository).trimRecent(11L, 30);
    }

    @Test
    void comboAndSendGiftKeepBasePacketBasedLegacyDeltas() {
        BilibiliLiveDanmakuEvent sendGift = new BilibiliLiveDanmakuEvent(
                "SEND_GIFT", EventKind.GIFT, "SEND_GIFT:gift-1", "gift-1", "{}",
                EVENT_TIME, EVENT_TIME, null, null, null,
                new BilibiliLiveDanmakuEvent.Gift(7L, "gift", 8, "gold", 1L, 8L, true, 8L),
                Metrics.empty(), null, 8L, null, 8
        );
        BilibiliLiveDanmakuEvent combo = new BilibiliLiveDanmakuEvent(
                "COMBO_SEND", EventKind.NOTIFICATION, "COMBO_SEND:combo-1", "combo-1", "{}",
                EVENT_TIME, EVENT_TIME, null, null, null, null, Metrics.empty(),
                null, null, null, null
        );
        when(boundaryService.ensureActiveForEvent(room(), EVENT_TIME, EVENT_TIME)).thenReturn(liveSession());
        when(boundaryService.findActive(11L)).thenReturn(Optional.of(liveSession()));
        when(eventRepository.insertIfAbsent(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyInt(), any(), any()))
                .thenReturn(true);

        service.ingest(room(), 71L, 1L, 3, sendGift, null);
        service.ingest(room(), 71L, 2L, 3, combo, null);

        verify(legacyRepository, org.mockito.Mockito.times(2)).recordMetricEvent(
                eq(11L), eq(71L), eq(33L), eq(EVENT_TIME), eq(60),
                eq(0), any(), any(), any(), any(), eq(1), eq(0), eq(1)
        );
    }

    private void verifyLegacyRawProjection(BilibiliLiveDanmakuEvent event) {
        verify(legacyRepository).recordMetricEvent(
                11L, 71L, 33L, event.occurredAt(), 60,
                event.isDanmaku() ? 1 : 0,
                event.likeCount(), event.likeIncrement(), event.watchedCount(), null,
                event.giftMetricDelta(), event.superChatMetricDelta(), 1
        );
    }

    private BilibiliLiveDanmakuEvent danmaku() {
        return new BilibiliLiveDanmakuEvent(
                "DANMU_MSG", EventKind.DANMAKU, "DANMU_MSG:id-1", "id-1", "{\"cmd\":\"DANMU_MSG\"}",
                EVENT_TIME, EVENT_TIME.plusSeconds(1), null, null,
                new Actor(22L, "A***e", "Fans"), null, Metrics.empty(), "hello", null, null, null
        );
    }

    private BilibiliLiveDanmakuEvent control(
            EventKind kind,
            String eventKey,
            OffsetDateTime liveStartedAt,
            String liveKey
    ) {
        return new BilibiliLiveDanmakuEvent(
                kind.name(), kind, eventKey, eventKey.substring(eventKey.indexOf(':') + 1),
                "{\"cmd\":\"" + kind.name() + "\"}", EVENT_TIME, EVENT_TIME,
                liveStartedAt, liveKey, null, null, Metrics.empty(), null, null, null, null
        );
    }

    private BilibiliLiveSession liveSession() {
        return new BilibiliLiveSession(
                90L, 11L, 22L, 33L, "OPEN", EVENT_TIME.minusMinutes(5), "live-key",
                EVENT_TIME.minusMinutes(5), EVENT_TIME.minusMinutes(4), "WS_LIVE",
                null, null, null, null, EVENT_TIME, EVENT_TIME,
                "title", null, EVENT_TIME.minusMinutes(4), EVENT_TIME
        );
    }

    private BilibiliLiveRoomMonitor room() {
        return new BilibiliLiveRoomMonitor(
                11L, 22L, 33L, null, "anchor", null, "title", null, null,
                null, null, null, null, 1, EVENT_TIME.minusMinutes(5), 100L, 200L,
                "ACTIVE", 300, EVENT_TIME.plusMinutes(5), EVENT_TIME, EVENT_TIME,
                null, null, null, null, "endpoint", EVENT_TIME.minusDays(1), EVENT_TIME
        );
    }
}
