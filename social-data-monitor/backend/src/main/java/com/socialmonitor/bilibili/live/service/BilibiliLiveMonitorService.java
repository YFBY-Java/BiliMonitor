package com.socialmonitor.bilibili.live.service;

import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.live.client.BilibiliLiveApiClient;
import com.socialmonitor.bilibili.live.config.BilibiliLiveMonitorProperties;
import com.socialmonitor.bilibili.live.domain.BilibiliFetchedLiveRoomSnapshot;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomSnapshot;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveStatusEvent;
import com.socialmonitor.bilibili.live.dto.AddBilibiliLiveRoomMonitorRequest;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveCollectResultView;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveRoomTrendView;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveRoomView;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveStatusEventView;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveSummaryView;
import com.socialmonitor.bilibili.live.dto.BilibiliLiveTrendPointView;
import com.socialmonitor.bilibili.live.dto.UpdateBilibiliLiveRoomMonitorRequest;
import com.socialmonitor.bilibili.live.repository.BilibiliLiveMonitorRepository;
import com.socialmonitor.collector.service.RateLimitService;
import com.socialmonitor.collector.service.RetryPolicy;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.platform.enums.FetchErrorType;
import com.socialmonitor.platform.enums.RiskLevel;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveMonitorService {

    private static final Logger log = LoggerFactory.getLogger(BilibiliLiveMonitorService.class);

    private final BilibiliLiveMonitorRepository repository;
    private final BilibiliLiveApiClient apiClient;
    private final BilibiliLiveMonitorProperties properties;
    private final RateLimitService rateLimitService;
    private final RetryPolicy retryPolicy;

    public BilibiliLiveMonitorService(
            BilibiliLiveMonitorRepository repository,
            BilibiliLiveApiClient apiClient,
            BilibiliLiveMonitorProperties properties,
            RateLimitService rateLimitService,
            RetryPolicy retryPolicy
    ) {
        this.repository = repository;
        this.apiClient = apiClient;
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.retryPolicy = retryPolicy;
    }

    public List<BilibiliLiveRoomView> listRooms() {
        return repository.findAll().stream()
                .map(room -> toRoomView(room, repository.findRecentSnapshots(room.id(), 48)))
                .toList();
    }

    public BilibiliLiveSummaryView summary() {
        List<BilibiliLiveRoomMonitor> rooms = repository.findAll();
        int active = (int) rooms.stream().filter(room -> "ACTIVE".equals(room.monitorStatus())).count();
        int live = (int) rooms.stream().filter(room -> room.liveStatus() != null && room.liveStatus() == 1).count();
        int round = (int) rooms.stream().filter(room -> room.liveStatus() != null && room.liveStatus() == 2).count();
        int error = (int) rooms.stream().filter(room -> room.lastErrorType() != null).count();
        long totalOnline = rooms.stream()
                .filter(room -> room.liveStatus() != null && room.liveStatus() == 1)
                .map(BilibiliLiveRoomMonitor::onlineCount)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        BilibiliLiveStatusEventView latestEvent = repository.findRecentEvents(1).stream()
                .findFirst()
                .map(this::toEventView)
                .orElse(null);
        return new BilibiliLiveSummaryView(
                rooms.size(),
                active,
                live,
                round,
                Math.max(0, rooms.size() - live - round),
                error,
                totalOnline,
                repository.countTodayLiveStarts(),
                latestEvent
        );
    }

    public BilibiliLiveRoomView addRoom(AddBilibiliLiveRoomMonitorRequest request) {
        if ((request.uid() == null && request.roomId() == null) || (request.uid() != null && request.roomId() != null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写 UID 或房间号，二选一。");
        }
        int intervalSeconds = resolveInterval(request.intervalSeconds());
        BilibiliFetchedLiveRoomSnapshot snapshot;
        try {
            snapshot = request.roomId() != null
                    ? fetchByRoomIdWithDetails(request.roomId())
                    : fetchByUidWithDetails(request.uid());
        } catch (BilibiliFetchException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, exception.getMessage());
        }
        if (snapshot.uid() == null || snapshot.roomId() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "直播间接口没有返回完整 UID/房间号。");
        }
        BilibiliLiveRoomMonitor room = repository.upsertMonitorFromSnapshot(
                snapshot,
                intervalSeconds,
                snapshot.fetchedAt().plusSeconds(intervalSeconds)
        );
        repository.upsertSnapshot(room.id(), snapshot);
        return toRoomView(room, repository.findRecentSnapshots(room.id(), 48));
    }

    public BilibiliLiveRoomView updateRoom(Long roomId, UpdateBilibiliLiveRoomMonitorRequest request) {
        BilibiliLiveRoomMonitor room = requireRoom(roomId);
        Integer intervalSeconds = request.intervalSeconds() == null ? null : resolveInterval(request.intervalSeconds());
        OffsetDateTime nextCollectAt = intervalSeconds == null ? null : OffsetDateTime.now().plusSeconds(intervalSeconds);
        repository.updateMonitor(room.id(), intervalSeconds, request.enabled(), nextCollectAt);
        BilibiliLiveRoomMonitor updated = requireRoom(room.id());
        return toRoomView(updated, repository.findRecentSnapshots(updated.id(), 48));
    }

    public void deleteRoom(Long roomId) {
        requireRoom(roomId);
        repository.delete(roomId);
    }

    public BilibiliLiveCollectResultView refreshNow(Long roomId) {
        return collectRoom(requireRoom(roomId), true);
    }

    public List<BilibiliLiveCollectResultView> collectDueRooms() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        List<BilibiliLiveRoomMonitor> dueRooms = repository.findDueRooms(OffsetDateTime.now(), Math.max(1, properties.getDueBatchSize()));
        if (dueRooms.isEmpty()) {
            return List.of();
        }
        List<BilibiliLiveCollectResultView> results = new ArrayList<>();
        int batchSize = Math.max(1, properties.getStatusBatchSize());
        for (int start = 0; start < dueRooms.size(); start += batchSize) {
            List<BilibiliLiveRoomMonitor> batch = dueRooms.subList(start, Math.min(start + batchSize, dueRooms.size()));
            results.addAll(collectRoomBatch(batch));
        }
        return results;
    }

    public BilibiliLiveRoomTrendView trend(Long roomId, OffsetDateTime from, OffsetDateTime to, int limit) {
        BilibiliLiveRoomMonitor room = requireRoom(roomId);
        List<BilibiliLiveRoomSnapshot> snapshots = repository.findSnapshots(room.id(), from, to, normalizeLimit(limit));
        return new BilibiliLiveRoomTrendView(toRoomView(room, snapshots), toPointViews(snapshots));
    }

    public List<BilibiliLiveRoomTrendView> trends(List<Long> roomIds, OffsetDateTime from, OffsetDateTime to, int limitPerRoom) {
        List<BilibiliLiveRoomMonitor> rooms = roomIds == null || roomIds.isEmpty()
                ? repository.findAll()
                : roomIds.stream().map(this::requireRoom).toList();
        int limit = normalizeLimit(limitPerRoom);
        return rooms.stream()
                .map(room -> {
                    List<BilibiliLiveRoomSnapshot> snapshots = repository.findSnapshots(room.id(), from, to, limit);
                    return new BilibiliLiveRoomTrendView(toRoomView(room, snapshots), toPointViews(snapshots));
                })
                .toList();
    }

    public List<BilibiliLiveStatusEventView> events(int limit) {
        return repository.findRecentEvents(Math.min(Math.max(limit, 1), 200)).stream()
                .map(this::toEventView)
                .toList();
    }

    private List<BilibiliLiveCollectResultView> collectRoomBatch(List<BilibiliLiveRoomMonitor> rooms) {
        List<Long> uids = rooms.stream().map(BilibiliLiveRoomMonitor::uid).toList();
        try {
            Map<Long, BilibiliFetchedLiveRoomSnapshot> snapshots = fetchStatusByUidsWithRetry(uids);
            return rooms.stream()
                    .map(room -> collectRoomFromBatchSnapshot(room, snapshots.get(room.uid())))
                    .toList();
        } catch (BilibiliFetchException exception) {
            return rooms.stream()
                    .map(room -> markFailure(room, exception, false))
                    .toList();
        }
    }

    private BilibiliLiveCollectResultView collectRoom(BilibiliLiveRoomMonitor room, boolean throwOnFailure) {
        try {
            BilibiliFetchedLiveRoomSnapshot snapshot = fetchByUidWithDetails(room.uid()).withExistingProfile(room);
            return applySuccessfulSnapshot(room, snapshot);
        } catch (BilibiliFetchException exception) {
            return markFailure(room, exception, throwOnFailure);
        }
    }

    private BilibiliLiveCollectResultView collectRoomFromBatchSnapshot(
            BilibiliLiveRoomMonitor room,
            BilibiliFetchedLiveRoomSnapshot snapshot
    ) {
        if (snapshot == null) {
            return markFailure(room, new BilibiliFetchException(
                    FetchErrorType.UNKNOWN,
                    true,
                    RiskLevel.LOW,
                    BilibiliLiveApiClient.STATUS_BY_UIDS_ENDPOINT,
                    200,
                    null,
                    "Bilibili live status response missing uid=" + room.uid(),
                    null
            ), false);
        }
        return applySuccessfulSnapshot(room, snapshot.withExistingProfile(room));
    }

    private BilibiliLiveCollectResultView applySuccessfulSnapshot(
            BilibiliLiveRoomMonitor room,
            BilibiliFetchedLiveRoomSnapshot snapshot
    ) {
        OffsetDateTime nextCollectAt = snapshot.fetchedAt().plusSeconds(room.intervalSeconds());
        recordSuccessEvents(room, snapshot);
        repository.updateSuccessfulSnapshot(room.id(), snapshot, nextCollectAt);
        repository.upsertSnapshot(room.id(), snapshot);
        log.info("Collected Bilibili live room snapshot. uid={}, roomId={}, liveStatus={}, online={}",
                snapshot.uid(), snapshot.roomId(), snapshot.liveStatus(), snapshot.onlineCount());
        return new BilibiliLiveCollectResultView(
                room.id(),
                snapshot.uid(),
                snapshot.roomId(),
                true,
                snapshot.liveStatus(),
                snapshot.onlineCount(),
                snapshot.fetchedAt(),
                snapshot.sourceEndpoint(),
                "ok"
        );
    }

    private BilibiliLiveCollectResultView markFailure(
            BilibiliLiveRoomMonitor room,
            BilibiliFetchException exception,
            boolean throwOnFailure
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime nextCollectAt = now.plusSeconds(failureBackoffSeconds(room));
        repository.markFailure(room.id(), exception, nextCollectAt);
        repository.insertStatusEvent(
                room.id(),
                room.uid(),
                room.roomId(),
                "ERROR_OCCURRED",
                room.liveStatus(),
                room.liveStatus(),
                room.title(),
                room.title(),
                room.onlineCount()
        );
        log.warn("Bilibili live collection failed. uid={}, roomId={}, errorType={}, message={}",
                room.uid(), room.roomId(), exception.errorType(), exception.getMessage());
        if (throwOnFailure) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, exception.getMessage());
        }
        return new BilibiliLiveCollectResultView(
                room.id(),
                room.uid(),
                room.roomId(),
                false,
                room.liveStatus(),
                room.onlineCount(),
                now,
                exception.endpointKey(),
                exception.getMessage()
        );
    }

    private void recordSuccessEvents(BilibiliLiveRoomMonitor room, BilibiliFetchedLiveRoomSnapshot snapshot) {
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

    private BilibiliFetchedLiveRoomSnapshot fetchByRoomIdWithDetails(Long roomId) {
        BilibiliFetchedLiveRoomSnapshot init = fetchWithRetry(
                BilibiliLiveApiClient.ROOM_INIT_ENDPOINT,
                () -> apiClient.fetchRoomInit(roomId),
                Map.of("roomId", roomId)
        );
        BilibiliFetchedLiveRoomSnapshot info = fetchWithRetry(
                BilibiliLiveApiClient.ROOM_INFO_ENDPOINT,
                () -> apiClient.fetchRoomInfo(init.roomId()),
                Map.of("roomId", init.roomId())
        );
        BilibiliFetchedLiveRoomSnapshot status = fetchStatusByUidsWithRetry(List.of(init.uid())).get(init.uid());
        return (status == null ? info : status).mergeWith(info).mergeWith(init);
    }

    private BilibiliFetchedLiveRoomSnapshot fetchByUidWithDetails(Long uid) {
        Map<Long, BilibiliFetchedLiveRoomSnapshot> statuses = fetchStatusByUidsWithRetry(List.of(uid));
        BilibiliFetchedLiveRoomSnapshot status = statuses.get(uid);
        if (status != null && status.roomId() != null) {
            BilibiliFetchedLiveRoomSnapshot info = fetchWithRetry(
                    BilibiliLiveApiClient.ROOM_INFO_ENDPOINT,
                    () -> apiClient.fetchRoomInfo(status.roomId()),
                    Map.of("roomId", status.roomId())
            );
            return status.mergeWith(info);
        }

        BilibiliFetchedLiveRoomSnapshot old = fetchWithRetry(
                BilibiliLiveApiClient.ROOM_INFO_OLD_ENDPOINT,
                () -> apiClient.fetchRoomInfoOld(uid).orElse(null),
                Map.of("uid", uid)
        );
        if (old == null || old.roomId() == null) {
            throw new BilibiliFetchException(
                    FetchErrorType.UNKNOWN,
                    false,
                    RiskLevel.LOW,
                    BilibiliLiveApiClient.ROOM_INFO_OLD_ENDPOINT,
                    200,
                    null,
                    "该 UID 暂无公开直播间或接口未返回房间信息：" + uid,
                    null
            );
        }
        BilibiliFetchedLiveRoomSnapshot init = fetchWithRetry(
                BilibiliLiveApiClient.ROOM_INIT_ENDPOINT,
                () -> apiClient.fetchRoomInit(old.roomId()),
                Map.of("roomId", old.roomId())
        );
        BilibiliFetchedLiveRoomSnapshot info = fetchWithRetry(
                BilibiliLiveApiClient.ROOM_INFO_ENDPOINT,
                () -> apiClient.fetchRoomInfo(init.roomId()),
                Map.of("roomId", init.roomId())
        );
        return info.mergeWith(init).mergeWith(old);
    }

    private Map<Long, BilibiliFetchedLiveRoomSnapshot> fetchStatusByUidsWithRetry(List<Long> uids) {
        return fetchWithRetry(
                BilibiliLiveApiClient.STATUS_BY_UIDS_ENDPOINT,
                () -> apiClient.fetchStatusByUids(uids),
                Map.of("uids", uids)
        );
    }

    private <T> T fetchWithRetry(String endpointKey, FetchOperation<T> operation, Map<String, Object> requestMeta) {
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        BilibiliFetchException lastException = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                rateLimitService.acquireMinInterval(
                        "bilibili-live",
                        endpointKey,
                        Duration.ofMillis(Math.max(0, properties.getRequestMinIntervalMs()))
                );
                T result = operation.fetch();
                repository.recordApiCall(endpointKey, 200, elapsedMs(startedAt), null, false, requestMeta,
                        Map.of("success", true));
                return result;
            } catch (BilibiliFetchException exception) {
                lastException = exception;
                repository.recordApiCall(
                        exception.endpointKey() == null ? endpointKey : exception.endpointKey(),
                        exception.statusCode(),
                        elapsedMs(startedAt),
                        exception.errorType().name(),
                        exception.retryable(),
                        requestMeta,
                        failureMeta(exception)
                );
                if (attempt < maxAttempts - 1 && retryPolicy.shouldRetry(exception.errorType(), attempt)) {
                    sleepBeforeRetry(attempt);
                    continue;
                }
                break;
            }
        }
        throw lastException == null
                ? new BilibiliFetchException(FetchErrorType.UNKNOWN, false, RiskLevel.LOW,
                endpointKey, null, null, "Unknown Bilibili live fetch failure.", null)
                : lastException;
    }

    private Map<String, Object> failureMeta(BilibiliFetchException exception) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("success", false);
        meta.put("message", exception.getMessage());
        if (exception.biliCode() != null) {
            meta.put("biliCode", exception.biliCode());
        }
        return meta;
    }

    private void sleepBeforeRetry(int attempt) {
        long backoff = (long) Math.max(100, properties.getRetryBackoffMs()) * (1L << Math.min(attempt, 4));
        long jitter = ThreadLocalRandom.current().nextLong(0, 300);
        try {
            Thread.sleep(backoff + jitter);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private int resolveInterval(Integer requestedInterval) {
        int interval = requestedInterval == null ? properties.getDefaultIntervalSeconds() : requestedInterval;
        int minimum = Math.max(1, properties.getMinIntervalSeconds());
        int maximum = Math.max(minimum, properties.getMaxIntervalSeconds());
        if (interval < minimum) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "采集间隔不能低于 " + minimum + " 秒，避免过于频繁请求 B站直播接口。");
        }
        if (interval > maximum) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "采集间隔不能超过 " + maximum + " 秒。");
        }
        if (interval < Math.max(1, properties.getShortIntervalWarningSeconds())) {
            log.warn("Bilibili live monitor short interval configured. intervalSeconds={}, minRequestIntervalMs={}",
                    interval, properties.getRequestMinIntervalMs());
        }
        return interval;
    }

    private int failureBackoffSeconds(BilibiliLiveRoomMonitor room) {
        return Math.max(60, Math.min(room.intervalSeconds(), properties.getFailureBackoffSeconds()));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 500;
        }
        return Math.min(limit, 2000);
    }

    private BilibiliLiveRoomMonitor requireRoom(Long roomId) {
        return repository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "监控直播间不存在：" + roomId));
    }

    private BilibiliLiveRoomView toRoomView(BilibiliLiveRoomMonitor room, List<BilibiliLiveRoomSnapshot> snapshots) {
        List<BilibiliLiveRoomSnapshot> sorted = snapshots.stream()
                .sorted(Comparator.comparing(BilibiliLiveRoomSnapshot::capturedAt))
                .toList();
        Long previous = sorted.size() < 2 ? null : sorted.get(sorted.size() - 2).onlineCount();
        Long current = room.onlineCount();
        Long delta = previous == null || current == null ? null : current - previous;
        return new BilibiliLiveRoomView(
                room.id(),
                room.uid(),
                room.roomId(),
                room.shortId(),
                room.uname(),
                room.faceUrl(),
                room.title(),
                room.coverUrl(),
                room.keyframeUrl(),
                room.areaId(),
                room.areaName(),
                room.parentAreaId(),
                room.parentAreaName(),
                room.liveStatus(),
                room.liveTime(),
                room.onlineCount(),
                room.attentionCount(),
                delta,
                room.monitorStatus(),
                room.intervalSeconds(),
                room.nextCollectAt(),
                room.lastSnapshotAt(),
                room.lastSuccessAt(),
                room.lastErrorAt(),
                room.lastErrorType(),
                room.lastErrorMessage(),
                room.backoffUntil(),
                room.sourceEndpoint(),
                toPointViews(sorted)
        );
    }

    private List<BilibiliLiveTrendPointView> toPointViews(List<BilibiliLiveRoomSnapshot> snapshots) {
        return snapshots.stream()
                .map(snapshot -> new BilibiliLiveTrendPointView(
                        snapshot.roomId(),
                        snapshot.uid(),
                        snapshot.capturedAt(),
                        snapshot.liveStatus(),
                        snapshot.onlineCount(),
                        snapshot.attentionCount(),
                        snapshot.sourceEndpoint()
                ))
                .toList();
    }

    private BilibiliLiveStatusEventView toEventView(BilibiliLiveStatusEvent event) {
        return new BilibiliLiveStatusEventView(
                event.id(),
                event.monitorId(),
                event.uid(),
                event.roomId(),
                event.eventType(),
                event.fromLiveStatus(),
                event.toLiveStatus(),
                event.titleBefore(),
                event.titleAfter(),
                event.onlineCount(),
                event.occurredAt()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface FetchOperation<T> {
        T fetch();
    }
}
