package com.socialmonitor.bilibili.live.rank.service;

import com.socialmonitor.bilibili.client.BilibiliFetchException;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.rank.client.BilibiliLiveRankApiClient;
import com.socialmonitor.bilibili.live.rank.config.BilibiliLiveRankProperties;
import com.socialmonitor.bilibili.live.rank.domain.BilibiliLiveRankEntry;
import com.socialmonitor.bilibili.live.rank.domain.BilibiliLiveRankFetchedSnapshot;
import com.socialmonitor.bilibili.live.rank.domain.BilibiliLiveRankSnapshot;
import com.socialmonitor.bilibili.live.rank.dto.BilibiliLiveRankEntryView;
import com.socialmonitor.bilibili.live.rank.dto.BilibiliLiveRankRefreshRequest;
import com.socialmonitor.bilibili.live.rank.dto.BilibiliLiveRankRefreshResultView;
import com.socialmonitor.bilibili.live.rank.dto.BilibiliLiveRankSnapshotView;
import com.socialmonitor.bilibili.live.rank.dto.BilibiliLiveRankSummaryView;
import com.socialmonitor.bilibili.live.rank.repository.BilibiliLiveRankRepository;
import com.socialmonitor.bilibili.live.repository.BilibiliLiveMonitorRepository;
import com.socialmonitor.collector.service.RateLimitService;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = {"storage-enabled", "rank.enabled"}, matchIfMissing = true)
public class BilibiliLiveRankService {

    private static final int SUMMARY_ENTRY_LIMIT = 100;

    private static final List<RankKind> DEFAULT_KINDS = List.of(
            new RankKind("AUDIENCE", "online_rank", "contribution_rank", "REALTIME", null, "房间观众在线贡献榜"),
            new RankKind("AUDIENCE", "online_rank", "entry_time_rank", "REALTIME", null, "房间观众进房时间榜"),
            new RankKind("AUDIENCE", "daily_rank", "today_rank", "CURRENT", null, "房间观众日榜"),
            new RankKind("AUDIENCE", "weekly_rank", "current_week_rank", "CURRENT", null, "房间观众周榜"),
            new RankKind("AUDIENCE", "monthly_rank", "current_month_rank", "CURRENT", null, "房间观众月榜"),
            new RankKind("GUARD", "guard_weekly", null, "CURRENT", 4, "大航海周榜"),
            new RankKind("GUARD", "guard_monthly", null, "CURRENT", 3, "大航海月榜"),
            new RankKind("GUARD", "guard_accompany", null, "CURRENT", 1, "大航海陪伴榜")
    );

    private final BilibiliLiveMonitorRepository monitorRepository;
    private final BilibiliLiveRankRepository rankRepository;
    private final BilibiliLiveRankApiClient rankApiClient;
    private final BilibiliLiveRankProperties properties;
    private final RateLimitService rateLimitService;

    public BilibiliLiveRankService(
            BilibiliLiveMonitorRepository monitorRepository,
            BilibiliLiveRankRepository rankRepository,
            BilibiliLiveRankApiClient rankApiClient,
            BilibiliLiveRankProperties properties,
            RateLimitService rateLimitService
    ) {
        this.monitorRepository = monitorRepository;
        this.rankRepository = rankRepository;
        this.rankApiClient = rankApiClient;
        this.properties = properties;
        this.rateLimitService = rateLimitService;
    }

    public BilibiliLiveRankSummaryView summary(Long roomMonitorId) {
        BilibiliLiveRoomMonitor room = requireRoom(roomMonitorId);
        return toSummaryView(room, SUMMARY_ENTRY_LIMIT);
    }

    public BilibiliLiveRankSnapshotView latest(
            Long roomMonitorId,
            String family,
            String type,
            String rankSwitch,
            int limit
    ) {
        requireRoom(roomMonitorId);
        return rankRepository.findLatestSnapshot(
                        roomMonitorId,
                        normalizeFamily(family),
                        blankToNull(type),
                        blankToNull(rankSwitch)
                )
                .map(snapshot -> toSnapshotView(snapshot, normalizeLimit(limit)))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "暂无直播间榜单数据，请先刷新榜单。"));
    }

    public BilibiliLiveRankRefreshResultView refresh(Long roomMonitorId, BilibiliLiveRankRefreshRequest request) {
        BilibiliLiveRoomMonitor room = requireRoom(roomMonitorId);
        List<RankKind> kinds = selectedKinds(request);
        if (kinds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有匹配的榜单类型。");
        }

        int maxPages = Math.min(
                Math.max(request == null || request.maxPages() == null ? properties.getMaxPages() : request.maxPages(), 1),
                5
        );
        boolean force = request != null && Boolean.TRUE.equals(request.force());
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        for (RankKind kind : kinds) {
            for (int page = 1; page <= maxPages; page++) {
                try {
                    BilibiliLiveRankFetchedSnapshot snapshot = fetchKind(room, kind, page, force);
                    rankRepository.upsertSnapshot(room.id(), room.roomId(), room.uid(), snapshot);
                    successCount++;
                } catch (BilibiliFetchException exception) {
                    errors.add(kind.label() + "：" + exception.getMessage());
                    break;
                }
            }
        }
        return new BilibiliLiveRankRefreshResultView(room.id(), successCount, errors, toSummaryView(room, SUMMARY_ENTRY_LIMIT));
    }

    private BilibiliLiveRankFetchedSnapshot fetchKind(
            BilibiliLiveRoomMonitor room,
            RankKind kind,
            int page,
            boolean force
    ) {
        if ("GUARD".equals(kind.family())) {
            int pageSize = Math.max(1, Math.min(properties.getGuardPageSize(), 30));
            return fetchWithLog(
                    BilibiliLiveRankApiClient.GUARD_RANK_ENDPOINT,
                    () -> rankApiClient.fetchGuardRank(room.roomId(), room.uid(), kind.type(), kind.periodScope(), kind.guardTyp(), page, pageSize),
                    Map.of("roomId", room.roomId(), "ruid", room.uid(), "type", kind.type(), "page", page, "pageSize", pageSize)
            );
        }

        int pageSize = Math.max(1, Math.min(properties.getPageSize(), 100));
        try {
            return fetchWithLog(
                    BilibiliLiveRankApiClient.AUDIENCE_RANK_ENDPOINT,
                    () -> rankApiClient.fetchAudienceRank(
                            room.roomId(), room.uid(), kind.type(), kind.rankSwitch(), kind.periodScope(), page, pageSize, force
                    ),
                    Map.of("roomId", room.roomId(), "ruid", room.uid(), "type", kind.type(), "switch", kind.rankSwitch(), "page", page, "pageSize", pageSize)
            );
        } catch (BilibiliFetchException exception) {
            if (!force && exception.errorType().name().equals("RISK_CONTROL")) {
                return fetchWithLog(
                        BilibiliLiveRankApiClient.AUDIENCE_RANK_ENDPOINT,
                        () -> rankApiClient.fetchAudienceRank(
                                room.roomId(), room.uid(), kind.type(), kind.rankSwitch(), kind.periodScope(), page, pageSize, true
                        ),
                        Map.of("roomId", room.roomId(), "ruid", room.uid(), "type", kind.type(), "switch", kind.rankSwitch(), "page", page, "forceWbi", true)
                );
            }
            if ("online_rank".equals(kind.type()) && "contribution_rank".equals(kind.rankSwitch())) {
                return fetchWithLog(
                        BilibiliLiveRankApiClient.ONLINE_GOLD_RANK_ENDPOINT,
                        () -> rankApiClient.fetchOnlineGoldRankFallback(room.roomId(), room.uid(), page, Math.min(pageSize, 50)),
                        Map.of("roomId", room.roomId(), "ruid", room.uid(), "page", page, "fallback", true)
                );
            }
            throw exception;
        }
    }

    private <T> T fetchWithLog(String endpointKey, FetchOperation<T> operation, Map<String, Object> requestMeta) {
        long startedAt = System.nanoTime();
        try {
            rateLimitService.acquireMinInterval(
                    "bilibili-live",
                    "live-rank",
                    Duration.ofMillis(Math.max(0, properties.getRequestMinIntervalMs()))
            );
            T result = operation.fetch();
            monitorRepository.recordApiCall(endpointKey, 200, elapsedMs(startedAt), null, false, requestMeta,
                    Map.of("success", true));
            return result;
        } catch (BilibiliFetchException exception) {
            monitorRepository.recordApiCall(
                    exception.endpointKey() == null ? endpointKey : exception.endpointKey(),
                    exception.statusCode(),
                    elapsedMs(startedAt),
                    exception.errorType().name(),
                    exception.retryable(),
                    requestMeta,
                    failureMeta(exception)
            );
            throw exception;
        }
    }

    private BilibiliLiveRankSummaryView toSummaryView(BilibiliLiveRoomMonitor room, int entryLimit) {
        Map<String, List<BilibiliLiveRankSnapshot>> pageGroups = new LinkedHashMap<>();
        rankRepository.findLatestSnapshotPages(room.id()).stream()
                .sorted(Comparator
                        .comparing(BilibiliLiveRankSnapshot::rankFamily)
                        .thenComparing(BilibiliLiveRankSnapshot::rankType)
                        .thenComparing(snapshot -> snapshot.rankSwitch() == null ? "" : snapshot.rankSwitch())
                        .thenComparing(snapshot -> snapshot.periodScope() == null ? "" : snapshot.periodScope())
                        .thenComparing(snapshot -> snapshot.pageNo() == null ? Integer.MAX_VALUE : snapshot.pageNo()))
                .forEach(snapshot -> pageGroups
                        .computeIfAbsent(snapshotGroupKey(snapshot), ignored -> new ArrayList<>())
                        .add(snapshot));

        List<BilibiliLiveRankSnapshotView> snapshots = pageGroups.values().stream()
                .map(pages -> toSnapshotView(pages, entryLimit))
                .toList();
        BilibiliLiveRankSnapshotView audience = snapshots.stream()
                .filter(snapshot -> "AUDIENCE".equals(snapshot.rankFamily()))
                .filter(snapshot -> "online_rank".equals(snapshot.rankType()) && "contribution_rank".equals(snapshot.rankSwitch()))
                .findFirst()
                .orElseGet(() -> snapshots.stream().filter(snapshot -> "AUDIENCE".equals(snapshot.rankFamily())).findFirst().orElse(null));
        BilibiliLiveRankSnapshotView guard = snapshots.stream()
                .filter(snapshot -> "GUARD".equals(snapshot.rankFamily()))
                .findFirst()
                .orElse(null);
        OffsetDateTime updatedAt = snapshots.stream()
                .map(BilibiliLiveRankSnapshotView::capturedAt)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        return new BilibiliLiveRankSummaryView(
                room.id(),
                room.roomId(),
                room.uid(),
                audience == null ? null : audience.totalCount(),
                audience == null ? null : audience.countText(),
                guard == null ? null : guard.totalCount(),
                guard == null ? null : guard.countText(),
                updatedAt,
                snapshots
        );
    }

    private BilibiliLiveRankSnapshotView toSnapshotView(BilibiliLiveRankSnapshot snapshot, int entryLimit) {
        return toSnapshotView(List.of(snapshot), entryLimit);
    }

    private BilibiliLiveRankSnapshotView toSnapshotView(List<BilibiliLiveRankSnapshot> pageSnapshots, int entryLimit) {
        BilibiliLiveRankSnapshot snapshot = pageSnapshots.stream()
                .min(Comparator.comparing(page -> page.pageNo() == null ? Integer.MAX_VALUE : page.pageNo()))
                .orElseThrow();
        List<BilibiliLiveRankEntryView> entries = mergedEntries(pageSnapshots, entryLimit).stream()
                .map(this::toEntryView)
                .toList();
        return new BilibiliLiveRankSnapshotView(
                snapshot.id(),
                snapshot.monitorId(),
                snapshot.roomId(),
                snapshot.ruid(),
                snapshot.rankFamily(),
                snapshot.rankType(),
                snapshot.rankSwitch(),
                snapshot.periodScope(),
                snapshot.pageNo(),
                snapshot.pageSize(),
                snapshot.totalCount(),
                snapshot.countText(),
                snapshot.valueText(),
                snapshot.remindMsg(),
                snapshot.sourceEndpoint(),
                snapshot.signedRequired(),
                snapshot.capturedAt(),
                entries
        );
    }

    private List<BilibiliLiveRankEntry> mergedEntries(List<BilibiliLiveRankSnapshot> pageSnapshots, int entryLimit) {
        Map<String, BilibiliLiveRankEntry> entriesByKey = new LinkedHashMap<>();
        pageSnapshots.stream()
                .sorted(Comparator.comparing(snapshot -> snapshot.pageNo() == null ? Integer.MAX_VALUE : snapshot.pageNo()))
                .forEach(snapshot -> rankRepository.findEntries(snapshot.id(), entryLimit).forEach(entry -> {
                    String key = entryKey(entry);
                    entriesByKey.putIfAbsent(key, entry);
                }));
        return entriesByKey.values().stream()
                .sorted(Comparator
                        .comparing((BilibiliLiveRankEntry entry) -> entry.rankNo() == null ? Integer.MAX_VALUE : entry.rankNo())
                        .thenComparing(entry -> entry.id() == null ? Long.MAX_VALUE : entry.id()))
                .limit(entryLimit)
                .toList();
    }

    private String snapshotGroupKey(BilibiliLiveRankSnapshot snapshot) {
        return String.join(":",
                snapshot.rankFamily(),
                snapshot.rankType(),
                snapshot.rankSwitch() == null ? "" : snapshot.rankSwitch(),
                snapshot.periodScope() == null ? "" : snapshot.periodScope()
        );
    }

    private String entryKey(BilibiliLiveRankEntry entry) {
        if (entry.userUid() != null) {
            return "uid:" + entry.userUid();
        }
        if (entry.rankNo() != null) {
            return "rank:" + entry.rankNo();
        }
        return "name:" + (entry.displayName() == null ? "" : entry.displayName());
    }

    private BilibiliLiveRankEntryView toEntryView(BilibiliLiveRankEntry entry) {
        return new BilibiliLiveRankEntryView(
                entry.userUid(),
                entry.rankNo(),
                entry.entryKind(),
                entry.displayName(),
                entry.faceUrl(),
                entry.score(),
                entry.guardLevel(),
                entry.wealthLevel(),
                entry.medalName(),
                entry.medalLevel(),
                entry.guardExpiredText(),
                entry.accompanyDays()
        );
    }

    private List<RankKind> selectedKinds(BilibiliLiveRankRefreshRequest request) {
        Set<String> families = normalizeSet(request == null ? null : request.families());
        Set<String> types = normalizeSet(request == null ? null : request.types());
        return DEFAULT_KINDS.stream()
                .filter(kind -> families.isEmpty() || families.contains(kind.family()))
                .filter(kind -> types.isEmpty() || types.contains(kind.type()) || types.contains(kind.rankSwitch()))
                .toList();
    }

    private Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toLowerCase(Locale.ROOT));
                normalized.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private String normalizeFamily(String family) {
        return family == null || family.isBlank() ? null : family.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        return Math.min(limit, 200);
    }

    private BilibiliLiveRoomMonitor requireRoom(Long roomMonitorId) {
        return monitorRepository.findById(roomMonitorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "监控直播间不存在：" + roomMonitorId));
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
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

    @FunctionalInterface
    private interface FetchOperation<T> {
        T fetch();
    }

    private record RankKind(
            String family,
            String type,
            String rankSwitch,
            String periodScope,
            Integer guardTyp,
            String label
    ) {
    }
}
