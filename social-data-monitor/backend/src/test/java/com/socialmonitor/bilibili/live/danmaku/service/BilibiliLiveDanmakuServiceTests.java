package com.socialmonitor.bilibili.live.danmaku.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.socialmonitor.bilibili.client.BilibiliApiClient;
import com.socialmonitor.bilibili.live.danmaku.client.BilibiliLiveDanmuInfoClient;
import com.socialmonitor.bilibili.live.danmaku.config.BilibiliLiveDanmakuProperties;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.Actor;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.EventKind;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.Metrics;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEventParser;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuPacketCodec;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuPacketCodec.ParsedPacket;
import com.socialmonitor.bilibili.live.danmaku.repository.BilibiliLiveDanmakuRepository;
import com.socialmonitor.bilibili.live.danmaku.domain.BilibiliLiveDanmakuSession;
import com.socialmonitor.bilibili.live.danmaku.domain.BilibiliLiveDanmakuStats;
import com.socialmonitor.bilibili.live.danmaku.dto.BilibiliLiveDanmakuStatusView;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.repository.BilibiliLiveMonitorRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveDanmakuServiceTests {

    private static final OffsetDateTime EVENT_TIME = OffsetDateTime.of(
            2026, 8, 16, 20, 0, 0, 0, ZoneOffset.ofHours(8)
    );

    @Mock
    private BilibiliLiveMonitorRepository liveRepository;
    @Mock
    private BilibiliLiveDanmakuRepository legacyRepository;
    @Mock
    private BilibiliApiClient bilibiliApiClient;
    @Mock
    private BilibiliLiveDanmuInfoClient danmuInfoClient;
    @Mock
    private BilibiliLiveDanmakuPacketCodec packetCodec;
    @Mock
    private BilibiliLiveDanmakuEventParser eventParser;
    @Mock
    private BilibiliLiveEventIngestionService ingestionService;

    private BilibiliLiveDanmakuService service;
    private BilibiliLiveDanmakuProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BilibiliLiveDanmakuProperties();
        service = new BilibiliLiveDanmakuService(
                liveRepository,
                legacyRepository,
                bilibiliApiClient,
                danmuInfoClient,
                properties,
                packetCodec,
                eventParser,
                ingestionService
        );
    }

    @Test
    void parsedWebSocketEventIsDelegatedToTransactionalIngestion() {
        BilibiliLiveRoomMonitor room = room();
        BilibiliLiveDanmakuEvent event = new BilibiliLiveDanmakuEvent(
                "DANMU_MSG", EventKind.DANMAKU, "DANMU_MSG:id-1", "id-1", "{}",
                EVENT_TIME, EVENT_TIME, null, null, new Actor(22L, "Alice", "Fans"),
                null, Metrics.empty(), "hello", null, null, null
        );

        service.ingestParsedEvent(room, 71L, 1L, 3, event);

        verify(ingestionService).ingest(room, 71L, 1L, 3, event, "Alice");
    }

    @Test
    void connectionHandleAssignsMonotonicReceiptOrdinals() throws Exception {
        BilibiliLiveRoomMonitor room = room();
        BilibiliLiveDanmakuEvent event = new BilibiliLiveDanmakuEvent(
                "LIVE", EventKind.LIVE, null, null, "{}", EVENT_TIME, EVENT_TIME,
                null, null, null, null, Metrics.empty(), null, null, null, null
        );
        Object handle = connectionHandle(room);
        Method applyEvent = BilibiliLiveDanmakuService.class.getDeclaredMethod(
                "applyEvent", handle.getClass(), BilibiliLiveDanmakuEvent.class
        );
        applyEvent.setAccessible(true);

        applyEvent.invoke(service, handle, event);
        applyEvent.invoke(service, handle, event);

        verify(ingestionService).ingest(room, 71L, 1L, 3, event, null);
        verify(ingestionService).ingest(room, 71L, 2L, 3, event, null);
    }

    @Test
    void oneEventPersistenceFailureDoesNotStopFollowingReceipts() {
        BilibiliLiveRoomMonitor room = room();
        BilibiliLiveDanmakuEvent event = new BilibiliLiveDanmakuEvent(
                "LIVE", EventKind.LIVE, null, null, "{}", EVENT_TIME, EVENT_TIME,
                null, null, null, null, Metrics.empty(), null, null, null, null
        );
        when(ingestionService.ingest(room, 71L, 1L, 3, event, null))
                .thenThrow(new RuntimeException("constraint violation"));

        assertThatNoException().isThrownBy(() -> {
            service.ingestParsedEvent(room, 71L, 1L, 3, event);
            service.ingestParsedEvent(room, 71L, 2L, 3, event);
        });

        verify(ingestionService).ingest(room, 71L, 2L, 3, event, null);
    }

    @Test
    void successfulAuthenticationReplyRecordsConnectedAt() throws Exception {
        Object handle = connectionHandle(room());

        deliverAuthReply(handle, "{\"code\":0}");

        verify(legacyRepository).markSessionConnected(eq(71L), any(OffsetDateTime.class));
        verify(legacyRepository, never()).markSessionError(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"code\":-101}", "{}", "not-json"})
    void rejectedMissingCodeOrMalformedAuthenticationReplyEntersErrorAndCloses(String body) throws Exception {
        WebSocket webSocket = mock(WebSocket.class);
        Object handle = connectionHandle(room(), webSocket);
        connections().put(11L, handle);

        deliverAuthReply(handle, body);

        verify(legacyRepository, never()).markSessionConnected(any(), any());
        verify(legacyRepository).markSessionError(eq(71L), any(), any());
        verify(webSocket).sendClose(WebSocket.NORMAL_CLOSURE, "authentication failed");
        assertThat(connections()).doesNotContainKey(11L);
    }

    @Test
    void outOfRangeIntegralAuthenticationCodeIsRejected() throws Exception {
        WebSocket webSocket = mock(WebSocket.class);
        Object handle = connectionHandle(room(), webSocket);
        connections().put(11L, handle);

        deliverAuthReply(handle, "{\"code\":4294967296}");

        verify(legacyRepository, never()).markSessionConnected(any(), any());
        verify(legacyRepository).markSessionError(eq(71L), any(), any());
        verify(webSocket).sendClose(WebSocket.NORMAL_CLOSURE, "authentication failed");
    }

    @Test
    void authenticationReplyWithoutBodyEntersErrorAndCloses() throws Exception {
        WebSocket webSocket = mock(WebSocket.class);
        Object handle = connectionHandle(room(), webSocket);
        connections().put(11L, handle);

        deliverAuthReply(handle, null);

        verify(legacyRepository, never()).markSessionConnected(any(), any());
        verify(legacyRepository).markSessionError(eq(71L), any(), any());
        verify(webSocket).sendClose(WebSocket.NORMAL_CLOSURE, "authentication failed");
        assertThat(connections()).doesNotContainKey(11L);
    }

    @Test
    void authenticationFailureCloseCallbackDoesNotOverwriteErrorStatus() throws Exception {
        WebSocket webSocket = mock(WebSocket.class);
        Object handle = connectionHandle(room(), webSocket);
        connections().put(11L, handle);

        deliverAuthReply(handle, "{\"code\":-101}");
        invokeListener(handle, "onClose",
                new Class<?>[]{WebSocket.class, int.class, String.class},
                webSocket, WebSocket.NORMAL_CLOSURE, "authentication failed");

        verify(legacyRepository).markSessionError(eq(71L), any(), any());
        verify(legacyRepository, never()).markSessionStatus(71L, "CLOSED");
        verify(legacyRepository, never()).markSessionStatus(71L, "STOPPED");
    }

    @Test
    void applicationStartupMarksOrphanedTransportSessionsInterrupted() {
        service.recoverOrphanedTransportSessions();

        verify(legacyRepository).markOrphanedSessionsInterrupted();
    }

    @Test
    void autoDesiredRoomIsRetriedAfterConnectionError() {
        when(legacyRepository.findAutoStartRoomMonitorIds()).thenReturn(java.util.List.of(11L));
        when(liveRepository.findById(11L)).thenReturn(Optional.of(room()));
        when(danmuInfoClient.fetchDanmuInfo(33L)).thenThrow(new RuntimeException("connection failed"));

        service.syncAutoConnections();
        service.syncAutoConnections();

        verify(danmuInfoClient, atLeast(2)).fetchDanmuInfo(33L);
    }

    @Test
    void reconnectsAnonymousConnectionsAfterLoginWithoutTouchingAuthenticatedConnections() throws Exception {
        WebSocket anonymousSocket = mock(WebSocket.class);
        Object anonymousHandle = connectionHandle(room(11L, 33L), anonymousSocket, true, "ANONYMOUS", 0L);
        WebSocket authenticatedSocket = mock(WebSocket.class);
        Object authenticatedHandle = connectionHandle(room(12L, 34L), authenticatedSocket, true, "LOGIN", 99L);
        connections().put(11L, anonymousHandle);
        connections().put(12L, authenticatedHandle);
        when(liveRepository.findById(11L)).thenReturn(Optional.of(room(11L, 33L)));
        when(legacyRepository.stats(any(), any())).thenReturn(new BilibiliLiveDanmakuStats(
                0, 0, null, null, null, null, null, null
        ));
        when(danmuInfoClient.fetchDanmuInfo(33L)).thenThrow(new RuntimeException("stop after proving reconnect"));

        service.reconnectAnonymousConnections();

        verify(anonymousSocket).sendClose(WebSocket.NORMAL_CLOSURE, "stopped");
        verify(authenticatedSocket, never()).sendClose(any(Integer.class), any());
        verify(danmuInfoClient).fetchDanmuInfo(33L);
        verify(danmuInfoClient, never()).fetchDanmuInfo(34L);
    }

    @Test
    void establishedAutoHandleIsRebuiltAfterOnError() throws Exception {
        WebSocket webSocket = mock(WebSocket.class);
        Object handle = connectionHandle(room(), webSocket);
        connections().put(11L, handle);
        when(legacyRepository.findAutoStartRoomMonitorIds()).thenReturn(List.of(11L));
        when(liveRepository.findById(11L)).thenReturn(Optional.of(room()));
        when(danmuInfoClient.fetchDanmuInfo(33L)).thenThrow(new RuntimeException("connection failed"));

        invokeListener(handle, "onError", new Class<?>[]{WebSocket.class, Throwable.class},
                webSocket, new RuntimeException("transport failed"));
        service.syncAutoConnections();

        assertThat(connections()).doesNotContainKey(11L);
        verify(danmuInfoClient, atLeast(1)).fetchDanmuInfo(33L);
    }

    @Test
    void establishedAutoHandleIsRebuiltAfterOnClose() throws Exception {
        WebSocket webSocket = mock(WebSocket.class);
        Object handle = connectionHandle(room(), webSocket);
        connections().put(11L, handle);
        when(legacyRepository.findAutoStartRoomMonitorIds()).thenReturn(List.of(11L));
        when(liveRepository.findById(11L)).thenReturn(Optional.of(room()));
        when(danmuInfoClient.fetchDanmuInfo(33L)).thenThrow(new RuntimeException("connection failed"));

        invokeListener(handle, "onClose",
                new Class<?>[]{WebSocket.class, int.class, String.class},
                webSocket, 1006, "transport closed");
        service.syncAutoConnections();

        assertThat(connections()).doesNotContainKey(11L);
        verify(danmuInfoClient, atLeast(1)).fetchDanmuInfo(33L);
    }

    @Test
    void autoConnectionAttemptsPreserveRepositoryPriorityOrder() {
        BilibiliLiveRoomMonitor live = room(12L, 34L);
        BilibiliLiveRoomMonitor binding = room(11L, 33L);
        when(legacyRepository.findAutoStartRoomMonitorIds()).thenReturn(List.of(12L, 11L));
        when(liveRepository.findById(12L)).thenReturn(Optional.of(live));
        when(liveRepository.findById(11L)).thenReturn(Optional.of(binding));
        when(danmuInfoClient.fetchDanmuInfo(any())).thenThrow(new RuntimeException("connection failed"));

        service.syncAutoConnections();

        InOrder order = inOrder(liveRepository);
        order.verify(liveRepository).findById(12L);
        order.verify(liveRepository).findById(11L);
    }

    @Test
    void maxConnectionsStopsStartingLowerPriorityDesiredRooms() throws Exception {
        properties.setMaxConnections(1);
        connections().put(12L, connectionHandle(room(12L, 34L)));
        when(legacyRepository.findAutoStartRoomMonitorIds()).thenReturn(List.of(12L, 11L));

        service.syncAutoConnections();

        verify(liveRepository, never()).findById(11L);
        assertThat(connections()).containsKey(12L);
        assertThat(connections()).hasSize(1);
    }

    @Test
    void higherPriorityLiveRoomDisplacesConnectedLowerPriorityAutoHandleAtCapacity() throws Exception {
        properties.setMaxConnections(1);
        WebSocket lowPrioritySocket = mock(WebSocket.class);
        Object lowPriorityHandle = connectionHandle(room(11L, 33L), lowPrioritySocket, true);
        setHandleStatus(lowPriorityHandle, "CONNECTED");
        connections().put(11L, lowPriorityHandle);
        BilibiliLiveRoomMonitor highPriorityLive = room(12L, 34L);
        when(legacyRepository.findAutoStartRoomMonitorIds()).thenReturn(List.of(12L, 11L));
        when(liveRepository.findById(11L)).thenReturn(Optional.of(room(11L, 33L)));
        when(liveRepository.findById(12L)).thenReturn(Optional.of(highPriorityLive));
        when(legacyRepository.stats(any(), any())).thenReturn(new BilibiliLiveDanmakuStats(
                0, 0, null, null, null, null, null, null
        ));
        when(danmuInfoClient.fetchDanmuInfo(34L)).thenThrow(new RuntimeException("connection failed"));

        service.syncAutoConnections();

        InOrder order = inOrder(legacyRepository, liveRepository, danmuInfoClient);
        order.verify(legacyRepository).markSessionStatus(71L, "STOPPED");
        order.verify(liveRepository).findById(12L);
        order.verify(danmuInfoClient, atLeast(1)).fetchDanmuInfo(34L);
        verify(lowPrioritySocket).sendClose(WebSocket.NORMAL_CLOSURE, "stopped");
        assertThat(connections()).doesNotContainKey(11L);
    }

    @Test
    void higherPriorityAutoRoomNeverDisplacesManualConnectionAtCapacity() throws Exception {
        properties.setMaxConnections(1);
        WebSocket manualSocket = mock(WebSocket.class);
        Object manualHandle = connectionHandle(room(11L, 33L), manualSocket, false);
        setHandleStatus(manualHandle, "CONNECTED");
        connections().put(11L, manualHandle);
        when(legacyRepository.findAutoStartRoomMonitorIds()).thenReturn(List.of(12L, 11L));

        service.syncAutoConnections();

        verify(manualSocket, never()).sendClose(any(Integer.class), any());
        verify(legacyRepository, never()).markSessionStatus(71L, "STOPPED");
        verify(liveRepository, never()).findById(12L);
        assertThat(connections()).containsKey(11L);
    }

    @Test
    void persistedConnectedStatusIsNotReportedAsLiveWithoutInMemoryTransport() {
        BilibiliLiveRoomMonitor room = room();
        BilibiliLiveDanmakuSession stale = new BilibiliLiveDanmakuSession(
                71L, room.id(), room.roomId(), EVENT_TIME.minusMinutes(5), null,
                "CONNECTED", "wss://example.invalid/sub", 0, EVENT_TIME.minusSeconds(30),
                null, null, null, EVENT_TIME.minusMinutes(5)
        );
        when(liveRepository.findById(room.id())).thenReturn(Optional.of(room));
        when(legacyRepository.findLatestSession(room.id())).thenReturn(Optional.of(stale));
        when(legacyRepository.stats(any(), any())).thenReturn(new BilibiliLiveDanmakuStats(
                0, 0, null, null, null, null, null, null
        ));

        BilibiliLiveDanmakuStatusView status = service.status(room.id());

        assertThat(status.running()).isFalse();
        assertThat(status.status()).isEqualTo("ERROR");
        assertThat(status.lastErrorType()).isEqualTo("PROCESS_RESTART");
    }

    private Object connectionHandle(BilibiliLiveRoomMonitor room) throws Exception {
        return connectionHandle(room, null);
    }

    private Object connectionHandle(BilibiliLiveRoomMonitor room, WebSocket webSocket) throws Exception {
        return connectionHandle(room, webSocket, true);
    }

    private Object connectionHandle(
            BilibiliLiveRoomMonitor room,
            WebSocket webSocket,
            boolean autoManaged
    ) throws Exception {
        return connectionHandle(room, webSocket, autoManaged, "ANONYMOUS", 0L);
    }

    private Object connectionHandle(
            BilibiliLiveRoomMonitor room,
            WebSocket webSocket,
            boolean autoManaged,
            String authMode,
            Long authUid
    ) throws Exception {
        Class<?> handleType = java.util.Arrays.stream(BilibiliLiveDanmakuService.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("ConnectionHandle"))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = handleType.getDeclaredConstructor(
                BilibiliLiveRoomMonitor.class, Long.class, String.class, boolean.class,
                int.class, String.class, Long.class
        );
        constructor.setAccessible(true);
        Object handle = constructor.newInstance(
                room, 71L, "wss://example.invalid/sub", autoManaged, 3, authMode, authUid
        );
        if (webSocket != null) {
            Field field = handleType.getDeclaredField("webSocket");
            field.setAccessible(true);
            field.set(handle, webSocket);
        }
        return handle;
    }

    private void setHandleStatus(Object handle, String status) throws Exception {
        Field field = handle.getClass().getDeclaredField("status");
        field.setAccessible(true);
        field.set(handle, status);
    }

    private void deliverAuthReply(Object handle, String body) throws Exception {
        Method handlePacket = BilibiliLiveDanmakuService.class.getDeclaredMethod(
                "handlePacket", handle.getClass(), ParsedPacket.class
        );
        handlePacket.setAccessible(true);
        handlePacket.invoke(service, handle, new ParsedPacket(
                1, BilibiliLiveDanmakuPacketCodec.OP_AUTH_REPLY, 1, body, null
        ));
    }

    private void invokeListener(
            Object handle,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) throws Exception {
        Class<?> listenerType = java.util.Arrays.stream(BilibiliLiveDanmakuService.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("DanmakuWebSocketListener"))
                .findFirst()
                .orElseThrow();
        Constructor<?> constructor = listenerType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object listener = constructor.newInstance(service, handle);
        Method method = listenerType.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        method.invoke(listener, arguments);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<Long, Object> connections() throws Exception {
        Field field = BilibiliLiveDanmakuService.class.getDeclaredField("connections");
        field.setAccessible(true);
        return (ConcurrentMap<Long, Object>) field.get(service);
    }

    private BilibiliLiveRoomMonitor room() {
        return room(11L, 33L);
    }

    private BilibiliLiveRoomMonitor room(Long monitorId, Long roomId) {
        return new BilibiliLiveRoomMonitor(
                monitorId, 22L, roomId, null, "anchor", null, "title", null, null,
                null, null, null, null, 1, EVENT_TIME.minusMinutes(5), 100L, 200L,
                "ACTIVE", 300, EVENT_TIME.plusMinutes(5), EVENT_TIME, EVENT_TIME,
                null, null, null, null, "endpoint", EVENT_TIME.minusDays(1), EVENT_TIME
        );
    }
}
