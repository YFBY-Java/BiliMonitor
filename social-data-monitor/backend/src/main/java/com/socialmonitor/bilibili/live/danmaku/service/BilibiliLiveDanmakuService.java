package com.socialmonitor.bilibili.live.danmaku.service;

import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.client.BilibiliApiClient;
import com.socialmonitor.bilibili.live.danmaku.client.BilibiliLiveDanmuInfoClient;
import com.socialmonitor.bilibili.live.danmaku.config.BilibiliLiveDanmakuProperties;
import com.socialmonitor.bilibili.live.danmaku.domain.BilibiliLiveDanmakuMetricBucket;
import com.socialmonitor.bilibili.live.danmaku.domain.BilibiliLiveDanmakuRecent;
import com.socialmonitor.bilibili.live.danmaku.domain.BilibiliLiveDanmakuSession;
import com.socialmonitor.bilibili.live.danmaku.domain.BilibiliLiveDanmakuStats;
import com.socialmonitor.bilibili.live.danmaku.dto.BilibiliLiveDanmakuMetricBucketView;
import com.socialmonitor.bilibili.live.danmaku.dto.BilibiliLiveDanmakuRecentView;
import com.socialmonitor.bilibili.live.danmaku.dto.BilibiliLiveDanmakuStatusView;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEventParser;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuPacketCodec;
import com.socialmonitor.bilibili.live.danmaku.repository.BilibiliLiveDanmakuRepository;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.repository.BilibiliLiveMonitorRepository;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveDanmakuService {

    private static final Logger log = LoggerFactory.getLogger(BilibiliLiveDanmakuService.class);
    private static final ZoneOffset DISPLAY_OFFSET = ZoneOffset.ofHours(8);
    private static final Duration DANMU_NAME_CACHE_TTL = Duration.ofHours(12);
    private static final Duration DANMU_NAME_FAILURE_CACHE_TTL = Duration.ofMinutes(10);

    private final BilibiliLiveMonitorRepository liveRepository;
    private final BilibiliLiveDanmakuRepository danmakuRepository;
    private final BilibiliApiClient bilibiliApiClient;
    private final BilibiliLiveDanmuInfoClient danmuInfoClient;
    private final BilibiliLiveDanmakuProperties properties;
    private final BilibiliLiveDanmakuPacketCodec packetCodec;
    private final BilibiliLiveDanmakuEventParser eventParser;
    private final HttpClient httpClient;
    private final ScheduledExecutorService heartbeatExecutor;
    private final ConcurrentMap<Long, ConnectionHandle> connections = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, CachedDanmuName> danmuNameCache = new ConcurrentHashMap<>();

    public BilibiliLiveDanmakuService(
            BilibiliLiveMonitorRepository liveRepository,
            BilibiliLiveDanmakuRepository danmakuRepository,
            BilibiliApiClient bilibiliApiClient,
            BilibiliLiveDanmuInfoClient danmuInfoClient,
            BilibiliLiveDanmakuProperties properties,
            BilibiliLiveDanmakuPacketCodec packetCodec,
            BilibiliLiveDanmakuEventParser eventParser
    ) {
        this.liveRepository = liveRepository;
        this.danmakuRepository = danmakuRepository;
        this.bilibiliApiClient = bilibiliApiClient;
        this.danmuInfoClient = danmuInfoClient;
        this.properties = properties;
        this.packetCodec = packetCodec;
        this.eventParser = eventParser;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getConnectTimeoutMs())))
                .build();
        this.heartbeatExecutor = Executors.newScheduledThreadPool(2, namedThreadFactory());
    }

    public synchronized BilibiliLiveDanmakuStatusView start(Long roomMonitorId) {
        return start(roomMonitorId, null);
    }

    public synchronized BilibiliLiveDanmakuStatusView start(Long roomMonitorId, Integer protocolVersion) {
        return startInternal(roomMonitorId, false, protocolVersion);
    }

    private BilibiliLiveDanmakuStatusView startInternal(Long roomMonitorId, boolean autoManaged, Integer requestedProtocolVersion) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Bilibili live danmaku monitor is disabled.");
        }
        List<Integer> protocolCandidates = resolveProtocolCandidates(requestedProtocolVersion);
        ConnectionHandle existing = connections.get(roomMonitorId);
        if (existing != null && existing.isActive()) {
            if (isAutoProtocol(requestedProtocolVersion) || protocolCandidates.contains(existing.protocolVersion)) {
                return status(roomMonitorId);
            }
            stop(roomMonitorId);
        }
        if (connections.size() >= Math.max(1, properties.getMaxConnections())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Danmaku connection limit reached.");
        }

        BilibiliLiveRoomMonitor room = requireRoom(roomMonitorId);
        BusinessException lastException = null;
        for (Integer protocolVersion : protocolCandidates) {
            try {
                return startWithProtocol(room, autoManaged, protocolVersion);
            } catch (BusinessException exception) {
                lastException = exception;
                log.warn("Failed to start Bilibili live danmaku websocket with protover={}. monitorId={}, roomId={}, message={}",
                        protocolVersion, room.id(), room.roomId(), exception.getMessage());
            }
        }
        throw lastException == null
                ? new BusinessException(ErrorCode.BUSINESS_ERROR, "Failed to start Bilibili danmaku websocket.")
                : lastException;
    }

    private BilibiliLiveDanmakuStatusView startWithProtocol(
            BilibiliLiveRoomMonitor room,
            boolean autoManaged,
            int protocolVersion
    ) {
        BilibiliLiveDanmakuSession session = null;
        try {
            BilibiliLiveDanmuInfoClient.DanmuInfo danmuInfo = danmuInfoClient.fetchDanmuInfo(room.roomId());
            BilibiliLiveDanmuInfoClient.DanmuHost host = danmuInfo.hosts().isEmpty()
                    ? new BilibiliLiveDanmuInfoClient.DanmuHost("broadcastlv.chat.bilibili.com", 2243, 2245, 2244)
                    : danmuInfo.hosts().get(0);
            URI uri = URI.create(host.defaultWssUri());
            session = danmakuRepository.createSession(room.id(), room.roomId(), uri.toString());
            ConnectionHandle handle = new ConnectionHandle(
                    room.id(),
                    room.roomId(),
                    session.id(),
                    uri.toString(),
                    autoManaged,
                    protocolVersion,
                    danmuInfo.authMode(),
                    danmuInfo.authUid()
            );
            DanmakuWebSocketListener listener = new DanmakuWebSocketListener(handle);
            WebSocket webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getConnectTimeoutMs())))
                    .buildAsync(uri, listener)
                    .get(Math.max(1000, properties.getConnectTimeoutMs()), TimeUnit.MILLISECONDS);
            handle.webSocket = webSocket;
            handle.status = "AUTHENTICATING";
            connections.put(room.id(), handle);
            danmakuRepository.markSessionStatus(session.id(), "AUTHENTICATING");
            sendAuth(handle, danmuInfo);
            handle.heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(
                    () -> sendHeartbeat(handle),
                    Math.max(5, properties.getHeartbeatSeconds()),
                    Math.max(5, properties.getHeartbeatSeconds()),
                    TimeUnit.SECONDS
            );
            log.info("Started Bilibili live danmaku websocket. monitorId={}, roomId={}, host={}, protover={}, authMode={}, authUid={}",
                    room.id(), room.roomId(), uri, protocolVersion, danmuInfo.authMode(), danmuInfo.authUid());
            return status(room.id());
        } catch (BilibiliFetchException exception) {
            if (session != null) {
                danmakuRepository.markSessionError(session.id(), exception.errorType().name(), exception.getMessage());
            }
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, exception.getMessage());
        } catch (Exception exception) {
            if (session != null) {
                danmakuRepository.markSessionError(session.id(), exception.getClass().getSimpleName(), exception.getMessage());
            }
            connections.remove(room.id());
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Failed to start Bilibili danmaku websocket: " + exception.getMessage());
        }
    }

    public synchronized BilibiliLiveDanmakuStatusView stop(Long roomMonitorId) {
        ConnectionHandle handle = connections.remove(roomMonitorId);
        if (handle != null) {
            handle.closeRequested = true;
            handle.status = "STOPPED";
            if (handle.heartbeatFuture != null) {
                handle.heartbeatFuture.cancel(true);
            }
            if (handle.webSocket != null) {
                handle.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stopped");
            }
            danmakuRepository.markSessionStatus(handle.sessionId, "STOPPED");
        }
        return status(roomMonitorId);
    }

    public BilibiliLiveDanmakuStatusView status(Long roomMonitorId) {
        BilibiliLiveRoomMonitor room = liveRepository.findById(roomMonitorId).orElse(null);
        ConnectionHandle handle = connections.get(roomMonitorId);
        BilibiliLiveDanmakuSession latest = handle == null
                ? danmakuRepository.findLatestSession(roomMonitorId).orElse(null)
                : danmakuRepository.findLatestSession(roomMonitorId).orElse(null);
        BilibiliLiveDanmakuStats stats = danmakuRepository.stats(roomMonitorId, OffsetDateTime.now(DISPLAY_OFFSET));
        return toStatusView(roomMonitorId, room == null ? null : room.roomId(), handle, latest, stats);
    }

    public List<BilibiliLiveDanmakuRecentView> recent(Long roomMonitorId, int limit) {
        return danmakuRepository.findRecent(roomMonitorId, Math.min(Math.max(limit, 1), 200)).stream()
                .map(this::toRecentView)
                .toList();
    }

    public List<BilibiliLiveDanmakuMetricBucketView> metrics(Long roomMonitorId, String range) {
        OffsetDateTime to = OffsetDateTime.now(DISPLAY_OFFSET);
        OffsetDateTime from = to.minus(parseRange(range));
        return danmakuRepository.findBuckets(roomMonitorId, from, to, 2000).stream()
                .map(this::toBucketView)
                .toList();
    }

    public BilibiliLiveDanmakuStats stats(Long roomMonitorId) {
        return danmakuRepository.stats(roomMonitorId, OffsetDateTime.now(DISPLAY_OFFSET));
    }

    public synchronized void syncAutoConnections() {
        if (!properties.isEnabled() || !properties.isAutoStartEnabled()) {
            return;
        }
        Set<Long> desired = new HashSet<>(danmakuRepository.findAutoStartRoomMonitorIds());
        desired.forEach(roomMonitorId -> {
            if (!connections.containsKey(roomMonitorId)) {
                try {
                    startInternal(roomMonitorId, true, null);
                } catch (BusinessException exception) {
                    log.warn("Auto start Bilibili danmaku failed. monitorId={}, message={}",
                            roomMonitorId, exception.getMessage());
                }
            }
        });
        connections.entrySet().stream()
                .filter(entry -> entry.getValue().autoManaged && !desired.contains(entry.getKey()))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::stop);
    }

    @PreDestroy
    public void shutdown() {
        connections.keySet().stream().toList().forEach(this::stop);
        heartbeatExecutor.shutdownNow();
    }

    private void sendAuth(ConnectionHandle handle, BilibiliLiveDanmuInfoClient.DanmuInfo danmuInfo) {
        if (handle.webSocket == null) {
            return;
        }
        byte[] packet = packetCodec.authPacket(
                handle.roomId,
                danmuInfo.authUid(),
                danmuInfo.token(),
                danmuInfo.buvid(),
                properties.getClientVersion(),
                handle.protocolVersion
        );
        handle.webSocket.sendBinary(ByteBuffer.wrap(packet), true);
    }

    private void sendHeartbeat(ConnectionHandle handle) {
        if (!handle.isActive() || handle.webSocket == null) {
            return;
        }
        try {
            handle.webSocket.sendBinary(ByteBuffer.wrap(packetCodec.heartbeatPacket()), true);
            danmakuRepository.markSessionHeartbeat(handle.sessionId);
        } catch (Exception exception) {
            markError(handle, exception);
        }
    }

    private void handlePacket(ConnectionHandle handle, BilibiliLiveDanmakuPacketCodec.ParsedPacket packet) {
        if (packet.operation() == BilibiliLiveDanmakuPacketCodec.OP_AUTH_REPLY) {
            handle.status = "CONNECTED";
            danmakuRepository.markSessionStatus(handle.sessionId, "CONNECTED");
            return;
        }
        if (packet.operation() == BilibiliLiveDanmakuPacketCodec.OP_HEARTBEAT_REPLY) {
            Long popularity = packet.popularity();
            if (popularity != null) {
                danmakuRepository.recordMetricEvent(
                        handle.monitorId,
                        handle.sessionId,
                        handle.roomId,
                        OffsetDateTime.now(DISPLAY_OFFSET),
                        properties.getBucketSeconds(),
                        0,
                        null,
                        null,
                        null,
                        popularity,
                        0,
                        0,
                        0
                );
            }
            danmakuRepository.markSessionHeartbeat(handle.sessionId);
            return;
        }
        if (packet.operation() != BilibiliLiveDanmakuPacketCodec.OP_COMMAND || packet.bodyText() == null) {
            return;
        }
        OffsetDateTime receivedAt = OffsetDateTime.now(DISPLAY_OFFSET);
        eventParser.parse(packet.bodyText(), receivedAt).ifPresent(event -> applyEvent(handle, event));
    }

    private void applyEvent(ConnectionHandle handle, BilibiliLiveDanmakuEvent event) {
        danmakuRepository.recordMetricEvent(
                handle.monitorId,
                handle.sessionId,
                handle.roomId,
                event.occurredAt(),
                properties.getBucketSeconds(),
                event.danmu() ? 1 : 0,
                event.likeCount(),
                event.likeIncrement(),
                event.watchedCount(),
                null,
                valueOrZero(event.giftCount()),
                valueOrZero(event.superChatCount()),
                1
        );
        if (event.danmu() && event.messageText() != null && !event.messageText().isBlank()) {
            String displayName = resolveDanmuDisplayName(event);
            danmakuRepository.insertRecent(
                    handle.monitorId,
                    handle.roomId,
                    event.messageText(),
                    displayName,
                    event.medalName(),
                    event.occurredAt()
            );
            danmakuRepository.trimRecent(handle.monitorId, Math.max(20, properties.getRecentMessageLimitPerRoom()));
        }
    }

    private void markError(ConnectionHandle handle, Throwable throwable) {
        handle.status = "ERROR";
        handle.lastErrorType = throwable.getClass().getSimpleName();
        handle.lastErrorMessage = throwable.getMessage();
        if (!handle.closeRequested) {
            connections.remove(handle.monitorId, handle);
        }
        if (handle.heartbeatFuture != null) {
            handle.heartbeatFuture.cancel(true);
        }
        danmakuRepository.markSessionError(handle.sessionId, handle.lastErrorType, handle.lastErrorMessage);
        log.warn("Bilibili danmaku websocket failed. monitorId={}, roomId={}, error={}",
                handle.monitorId, handle.roomId, throwable.getMessage());
    }

    private BilibiliLiveDanmakuStatusView toStatusView(
            Long roomMonitorId,
            Long roomId,
            ConnectionHandle handle,
            BilibiliLiveDanmakuSession latest,
            BilibiliLiveDanmakuStats stats
    ) {
        boolean running = handle != null && handle.isActive();
        String status = running
                ? handle.status
                : latest == null ? "NOT_STARTED" : latest.status();
        Long sessionId = running ? handle.sessionId : latest == null ? null : latest.id();
        String host = running ? handle.connectHost : latest == null ? null : latest.connectHost();
        OffsetDateTime startedAt = running
                ? handle.startedAt
                : latest == null ? null : latest.startedAt();
        OffsetDateTime heartbeatAt = running
                ? latest == null ? null : latest.lastHeartbeatAt()
                : latest == null ? null : latest.lastHeartbeatAt();
        OffsetDateTime errorAt = running
                ? null
                : latest == null ? null : latest.lastErrorAt();
        String errorType = running ? handle.lastErrorType : latest == null ? null : latest.lastErrorType();
        String errorMessage = running ? handle.lastErrorMessage : latest == null ? null : latest.lastErrorMessage();
        return new BilibiliLiveDanmakuStatusView(
                roomMonitorId,
                roomId == null && latest != null ? latest.roomId() : roomId,
                running,
                status,
                host,
                sessionId,
                stats.ratePerMinute(),
                stats.last5MinutesCount(),
                stats.likeCount(),
                stats.likeIncrement(),
                stats.watchedCount(),
                stats.heartbeatPopularity(),
                running ? handle.protocolVersion : null,
                running ? handle.authMode : null,
                running ? handle.authUid : null,
                startedAt,
                heartbeatAt,
                errorAt,
                errorType,
                errorMessage
        );
    }

    private BilibiliLiveDanmakuRecentView toRecentView(BilibiliLiveDanmakuRecent recent) {
        return new BilibiliLiveDanmakuRecentView(
                recent.messageText(),
                recent.displayName(),
                recent.medalName(),
                recent.sentAt()
        );
    }

    private BilibiliLiveDanmakuMetricBucketView toBucketView(BilibiliLiveDanmakuMetricBucket bucket) {
        return new BilibiliLiveDanmakuMetricBucketView(
                bucket.bucketStart(),
                bucket.bucketSeconds(),
                bucket.danmuCount(),
                bucket.likeCount(),
                bucket.likeIncrement(),
                bucket.watchedCount(),
                bucket.heartbeatPopularity(),
                bucket.rawEventCount()
        );
    }

    private Duration parseRange(String range) {
        if (range == null || range.isBlank()) {
            return Duration.ofHours(1);
        }
        String value = range.trim().toLowerCase();
        try {
            long amount = Long.parseLong(value.substring(0, value.length() - 1));
            if (value.endsWith("m")) {
                return Duration.ofMinutes(Math.max(1, amount));
            }
            if (value.endsWith("h")) {
                return Duration.ofHours(Math.max(1, amount));
            }
            if (value.endsWith("d")) {
                return Duration.ofDays(Math.max(1, amount));
            }
        } catch (RuntimeException ignored) {
        }
        return Duration.ofHours(1);
    }

    private BilibiliLiveRoomMonitor requireRoom(Long roomMonitorId) {
        return liveRepository.findById(roomMonitorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Bilibili live room monitor not found: " + roomMonitorId));
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String resolveDanmuDisplayName(BilibiliLiveDanmakuEvent event) {
        String currentName = blankToNull(event.displayName());
        Long senderUid = event.senderUid();
        if (!isMaskedName(currentName) || senderUid == null || senderUid <= 0) {
            return currentName;
        }

        OffsetDateTime now = OffsetDateTime.now(DISPLAY_OFFSET);
        CachedDanmuName cached = danmuNameCache.get(senderUid);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.name();
        }

        try {
            String fetchedName = blankToNull(bilibiliApiClient.fetchUserCard(senderUid).nickname());
            if (fetchedName != null) {
                danmuNameCache.put(senderUid, new CachedDanmuName(fetchedName, now.plus(DANMU_NAME_CACHE_TTL)));
                return fetchedName;
            }
        } catch (Exception exception) {
            log.debug("Failed to resolve full danmaku sender name. uid={}, message={}", senderUid, exception.getMessage());
        }

        if (currentName != null) {
            danmuNameCache.put(senderUid, new CachedDanmuName(currentName, now.plus(DANMU_NAME_FAILURE_CACHE_TTL)));
        }
        return currentName;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isMaskedName(String value) {
        return value != null && value.indexOf('*') >= 0;
    }

    private List<Integer> resolveProtocolCandidates(Integer requestedProtocolVersion) {
        if (!isAutoProtocol(requestedProtocolVersion)) {
            return List.of(requestedProtocolVersion);
        }
        List<Integer> candidates = new ArrayList<>();
        addProtocolCandidate(candidates, properties.getProtocolVersion());
        addProtocolCandidate(candidates, 3);
        addProtocolCandidate(candidates, 2);
        addProtocolCandidate(candidates, 1);
        addProtocolCandidate(candidates, 0);
        return candidates;
    }

    private void addProtocolCandidate(List<Integer> candidates, int protocolVersion) {
        if (protocolVersion >= 0 && protocolVersion <= 3 && !candidates.contains(protocolVersion)) {
            candidates.add(protocolVersion);
        }
    }

    private boolean isAutoProtocol(Integer requestedProtocolVersion) {
        return requestedProtocolVersion == null || requestedProtocolVersion < 0 || requestedProtocolVersion > 3;
    }

    private ThreadFactory namedThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("bilibili-danmaku-heartbeat-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        };
    }

    private final class DanmakuWebSocketListener implements WebSocket.Listener {

        private final ConnectionHandle handle;
        private final ByteArrayOutputStream pendingBinary = new ByteArrayOutputStream();

        private DanmakuWebSocketListener(ConnectionHandle handle) {
            this.handle = handle;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            synchronized (pendingBinary) {
                pendingBinary.writeBytes(chunk);
                if (last) {
                    byte[] message = pendingBinary.toByteArray();
                    pendingBinary.reset();
                    packetCodec.parse(message).forEach(packet -> handlePacket(handle, packet));
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (last) {
                eventParser.parse(data.toString(), OffsetDateTime.now(DISPLAY_OFFSET))
                        .ifPresent(event -> applyEvent(handle, event));
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connections.remove(handle.monitorId, handle);
            if (handle.heartbeatFuture != null) {
                handle.heartbeatFuture.cancel(true);
            }
            if (handle.closeRequested) {
                danmakuRepository.markSessionStatus(handle.sessionId, "STOPPED");
            } else {
                handle.status = "CLOSED";
                danmakuRepository.markSessionStatus(handle.sessionId, "CLOSED");
            }
            log.info("Bilibili danmaku websocket closed. monitorId={}, roomId={}, code={}, reason={}",
                    handle.monitorId, handle.roomId, statusCode, reason);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            markError(handle, error);
        }
    }

    private static final class ConnectionHandle {

        private final Long monitorId;
        private final Long roomId;
        private final Long sessionId;
        private final String connectHost;
        private final boolean autoManaged;
        private final int protocolVersion;
        private final String authMode;
        private final Long authUid;
        private final OffsetDateTime startedAt = OffsetDateTime.now(DISPLAY_OFFSET);
        private volatile WebSocket webSocket;
        private volatile ScheduledFuture<?> heartbeatFuture;
        private volatile boolean closeRequested;
        private volatile String status = "CONNECTING";
        private volatile String lastErrorType;
        private volatile String lastErrorMessage;

        private ConnectionHandle(
                Long monitorId,
                Long roomId,
                Long sessionId,
                String connectHost,
                boolean autoManaged,
                int protocolVersion,
                String authMode,
                Long authUid
        ) {
            this.monitorId = monitorId;
            this.roomId = roomId;
            this.sessionId = sessionId;
            this.connectHost = connectHost;
            this.autoManaged = autoManaged;
            this.protocolVersion = protocolVersion;
            this.authMode = authMode;
            this.authUid = authUid;
        }

        private boolean isActive() {
            return !closeRequested && webSocket != null && !webSocket.isOutputClosed() && !webSocket.isInputClosed();
        }
    }

    private record CachedDanmuName(String name, OffsetDateTime expiresAt) {
    }
}
