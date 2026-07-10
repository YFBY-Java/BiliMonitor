package com.socialmonitor.bilibili.service;

import com.socialmonitor.bilibili.client.BilibiliApiClient;
import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.config.BilibiliFollowerMonitorProperties;
import com.socialmonitor.bilibili.domain.BilibiliFetchedUserSnapshot;
import com.socialmonitor.bilibili.domain.BilibiliFollowerSnapshot;
import com.socialmonitor.bilibili.domain.BilibiliMonitoredUser;
import com.socialmonitor.bilibili.dto.AddBilibiliMonitorUserRequest;
import com.socialmonitor.bilibili.dto.BilibiliCollectResultView;
import com.socialmonitor.bilibili.dto.BilibiliFollowerPointView;
import com.socialmonitor.bilibili.dto.BilibiliMonitorUserView;
import com.socialmonitor.bilibili.dto.BilibiliUserTrendView;
import com.socialmonitor.bilibili.repository.BilibiliFollowerMonitorRepository;
import com.socialmonitor.collector.service.RateLimitService;
import com.socialmonitor.collector.service.RetryPolicy;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.platform.enums.FetchErrorType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.follower-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliFollowerMonitorService {

    private static final Logger log = LoggerFactory.getLogger(BilibiliFollowerMonitorService.class);

    private final BilibiliFollowerMonitorRepository repository;
    private final BilibiliApiClient apiClient;
    private final BilibiliFollowerMonitorProperties properties;
    private final RateLimitService rateLimitService;
    private final RetryPolicy retryPolicy;

    public BilibiliFollowerMonitorService(
            BilibiliFollowerMonitorRepository repository,
            BilibiliApiClient apiClient,
            BilibiliFollowerMonitorProperties properties,
            RateLimitService rateLimitService,
            RetryPolicy retryPolicy
    ) {
        this.repository = repository;
        this.apiClient = apiClient;
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.retryPolicy = retryPolicy;
    }

    public List<BilibiliMonitorUserView> listUsers() {
        return repository.findAll().stream()
                .map(user -> toUserView(user, repository.findRecentSnapshots(user.id(), 30)))
                .toList();
    }

    public BilibiliMonitorUserView addUser(AddBilibiliMonitorUserRequest request) {
        int intervalSeconds = resolveInterval(request.intervalSeconds());
        BilibiliFetchedUserSnapshot snapshot;
        try {
            snapshot = fetchCardWithRetry(request.mid());
        } catch (BilibiliFetchException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, exception.getMessage());
        }
        BilibiliMonitoredUser user = repository.upsertUserFromSnapshot(
                snapshot,
                intervalSeconds,
                snapshot.fetchedAt().plusSeconds(intervalSeconds)
        );
        repository.upsertSnapshot(user.id(), snapshot);
        return toUserView(user, repository.findRecentSnapshots(user.id(), 30));
    }

    public BilibiliMonitorUserView updateStatus(Long userId, boolean enabled) {
        requireUser(userId);
        repository.updateStatus(userId, enabled);
        BilibiliMonitoredUser updated = requireUser(userId);
        return toUserView(updated, repository.findRecentSnapshots(updated.id(), 30));
    }

    public BilibiliMonitorUserView updateInterval(Long userId, Integer requestedIntervalSeconds) {
        BilibiliMonitoredUser user = requireUser(userId);
        int intervalSeconds = resolveInterval(requestedIntervalSeconds);
        repository.updateInterval(user.id(), intervalSeconds, OffsetDateTime.now().plusSeconds(intervalSeconds));
        BilibiliMonitoredUser updated = requireUser(user.id());
        return toUserView(updated, repository.findRecentSnapshots(updated.id(), 30));
    }

    public void deleteUser(Long userId) {
        requireUser(userId);
        repository.delete(userId);
    }

    public BilibiliCollectResultView refreshNow(Long userId) {
        BilibiliMonitoredUser user = requireUser(userId);
        return collectUser(user, true);
    }

    public List<BilibiliCollectResultView> collectDueUsers() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        return repository.findDueUsers(OffsetDateTime.now(), Math.max(1, properties.getDueBatchSize()))
                .stream()
                .map(user -> collectUser(user, false))
                .toList();
    }

    public BilibiliUserTrendView trend(Long userId, OffsetDateTime from, OffsetDateTime to, int limit) {
        BilibiliMonitoredUser user = requireUser(userId);
        List<BilibiliFollowerSnapshot> snapshots = repository.findSnapshots(user.id(), from, to, normalizeLimit(limit));
        return new BilibiliUserTrendView(toUserView(user, snapshots), toPointViews(snapshots));
    }

    public List<BilibiliUserTrendView> trends(List<Long> userIds, OffsetDateTime from, OffsetDateTime to, int limitPerUser) {
        List<BilibiliMonitoredUser> users = userIds == null || userIds.isEmpty()
                ? repository.findAll()
                : userIds.stream().map(this::requireUser).toList();
        int limit = normalizeLimit(limitPerUser);
        return users.stream()
                .map(user -> {
                    List<BilibiliFollowerSnapshot> snapshots = repository.findSnapshots(user.id(), from, to, limit);
                    return new BilibiliUserTrendView(toUserView(user, snapshots), toPointViews(snapshots));
                })
                .toList();
    }

    private BilibiliCollectResultView collectUser(BilibiliMonitoredUser user, boolean throwOnFailure) {
        try {
            BilibiliFetchedUserSnapshot snapshot = fetchForExistingUser(user);
            OffsetDateTime nextCollectAt = snapshot.fetchedAt().plusSeconds(user.intervalSeconds());
            repository.updateSuccessfulSnapshot(user.id(), snapshot, nextCollectAt);
            repository.upsertSnapshot(user.id(), snapshot);
            log.info("Collected Bilibili follower snapshot. mid={}, followers={}, source={}",
                    snapshot.mid(), snapshot.followerCount(), snapshot.sourceEndpoint());
            return new BilibiliCollectResultView(
                    user.id(),
                    user.mid(),
                    true,
                    snapshot.followerCount(),
                    snapshot.fetchedAt(),
                    snapshot.sourceEndpoint(),
                    "ok"
            );
        } catch (BilibiliFetchException exception) {
            OffsetDateTime nextCollectAt = OffsetDateTime.now().plusSeconds(failureBackoffSeconds(user));
            repository.markFailure(user.id(), exception, nextCollectAt);
            log.warn("Bilibili follower collection failed. mid={}, errorType={}, message={}",
                    user.mid(), exception.errorType(), exception.getMessage());
            if (throwOnFailure) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, exception.getMessage());
            }
            return new BilibiliCollectResultView(
                    user.id(),
                    user.mid(),
                    false,
                    user.currentFollowerCount(),
                    OffsetDateTime.now(),
                    exception.endpointKey(),
                    exception.getMessage()
            );
        }
    }

    private BilibiliFetchedUserSnapshot fetchForExistingUser(BilibiliMonitoredUser user) {
        try {
            return fetchCardWithRetry(user.mid());
        } catch (BilibiliFetchException exception) {
            if (canFallbackToRelationStat(exception)) {
                log.info("Falling back to relation/stat for mid={} after {} from card endpoint.",
                        user.mid(), exception.errorType());
                return fetchRelationStatWithRetry(user);
            }
            throw exception;
        }
    }

    private BilibiliFetchedUserSnapshot fetchCardWithRetry(Long mid) {
        return fetchWithRetry(
                BilibiliApiClient.CARD_ENDPOINT,
                () -> apiClient.fetchUserCard(mid),
                Map.of("mid", mid)
        );
    }

    private BilibiliFetchedUserSnapshot fetchRelationStatWithRetry(BilibiliMonitoredUser user) {
        return fetchWithRetry(
                BilibiliApiClient.RELATION_STAT_ENDPOINT,
                () -> apiClient.fetchRelationStat(user),
                Map.of("mid", user.mid())
        );
    }

    private BilibiliFetchedUserSnapshot fetchWithRetry(
            String endpointKey,
            FetchOperation operation,
            Map<String, Object> requestMeta
    ) {
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        BilibiliFetchException lastException = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                rateLimitService.acquireMinInterval(
                        "bilibili",
                        endpointKey,
                        Duration.ofMillis(Math.max(0, properties.getRequestMinIntervalMs()))
                );
                BilibiliFetchedUserSnapshot snapshot = operation.fetch();
                repository.recordApiCall(endpointKey, 200, elapsedMs(startedAt), null, false, requestMeta,
                        Map.of("success", true, "sourceEndpoint", snapshot.sourceEndpoint()));
                return snapshot;
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
                ? new BilibiliFetchException(FetchErrorType.UNKNOWN, false, com.socialmonitor.platform.enums.RiskLevel.LOW,
                endpointKey, null, null,
                "Unknown Bilibili fetch failure.", null)
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

    private boolean canFallbackToRelationStat(BilibiliFetchException exception) {
        return exception.errorType() == FetchErrorType.PARSE_ERROR
                || exception.errorType() == FetchErrorType.NETWORK_ERROR
                || exception.errorType() == FetchErrorType.SERVER_ERROR;
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
                    "采集间隔不能低于 " + minimum + " 秒，避免过于频繁请求 B站接口。");
        }
        if (interval > maximum) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "采集间隔不能超过 " + maximum + " 秒。");
        }
        if (interval < Math.max(1, properties.getShortIntervalWarningSeconds())) {
            log.warn("Bilibili follower monitor short interval configured. intervalSeconds={}, minRequestIntervalMs={}",
                    interval, properties.getRequestMinIntervalMs());
        }
        return interval;
    }

    private int failureBackoffSeconds(BilibiliMonitoredUser user) {
        return Math.max(300, Math.min(user.intervalSeconds(), properties.getFailureBackoffSeconds()));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 500;
        }
        return Math.min(limit, 2000);
    }

    private BilibiliMonitoredUser requireUser(Long userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "监控用户不存在：" + userId));
    }

    private BilibiliMonitorUserView toUserView(BilibiliMonitoredUser user, List<BilibiliFollowerSnapshot> snapshots) {
        List<BilibiliFollowerSnapshot> sorted = snapshots.stream()
                .sorted(Comparator.comparing(BilibiliFollowerSnapshot::capturedAt))
                .toList();
        Long previous = sorted.size() < 2 ? null : sorted.get(sorted.size() - 2).followerCount();
        Long current = user.currentFollowerCount();
        Long delta = previous == null || current == null ? null : current - previous;
        Double rate = previous == null || previous == 0 || delta == null ? null : delta * 1.0 / previous;
        return new BilibiliMonitorUserView(
                user.id(),
                user.mid(),
                user.nickname(),
                user.avatarUrl(),
                user.profileUrl(),
                current,
                user.followingCount(),
                delta,
                rate,
                user.lastSnapshotAt(),
                user.lastSuccessAt(),
                user.nextCollectAt(),
                user.monitorStatus(),
                user.intervalSeconds(),
                user.lastErrorType(),
                user.lastErrorMessage(),
                user.lastErrorAt(),
                user.sourceEndpoint(),
                toPointViews(sorted)
        );
    }

    private List<BilibiliFollowerPointView> toPointViews(List<BilibiliFollowerSnapshot> snapshots) {
        return snapshots.stream()
                .map(snapshot -> new BilibiliFollowerPointView(
                        snapshot.capturedAt(),
                        snapshot.followerCount(),
                        snapshot.followingCount(),
                        snapshot.sourceEndpoint()
                ))
                .toList();
    }

    @FunctionalInterface
    private interface FetchOperation {
        BilibiliFetchedUserSnapshot fetch();
    }
}
