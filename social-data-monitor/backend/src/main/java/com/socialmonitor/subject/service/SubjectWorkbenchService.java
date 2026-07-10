package com.socialmonitor.subject.service;

import com.socialmonitor.bilibili.domain.BilibiliFollowerSnapshot;
import com.socialmonitor.bilibili.domain.BilibiliMonitoredUser;
import com.socialmonitor.bilibili.live.danmaku.dto.BilibiliLiveDanmakuRecentView;
import com.socialmonitor.bilibili.live.danmaku.dto.BilibiliLiveDanmakuStatusView;
import com.socialmonitor.bilibili.live.danmaku.service.BilibiliLiveDanmakuService;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomSnapshot;
import com.socialmonitor.bilibili.live.repository.BilibiliLiveMonitorRepository;
import com.socialmonitor.bilibili.repository.BilibiliFollowerMonitorRepository;
import com.socialmonitor.subject.domain.MonitoredSubject;
import com.socialmonitor.subject.domain.SubjectBilibiliBinding;
import com.socialmonitor.subject.domain.SubjectWidgetLayout;
import com.socialmonitor.subject.dto.SubjectBilibiliLiveRoomView;
import com.socialmonitor.subject.dto.SubjectBilibiliUserView;
import com.socialmonitor.subject.dto.SubjectDanmuRecentMessageView;
import com.socialmonitor.subject.dto.SubjectDanmuView;
import com.socialmonitor.subject.dto.SubjectHealthEventView;
import com.socialmonitor.subject.dto.SubjectSummaryView;
import com.socialmonitor.subject.dto.SubjectTrendPointView;
import com.socialmonitor.subject.dto.SubjectTrendView;
import com.socialmonitor.subject.dto.SubjectWorkbenchView;
import com.socialmonitor.subject.repository.SubjectRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.subject-monitor", name = "enabled", matchIfMissing = true)
public class SubjectWorkbenchService {

    private static final ZoneOffset DISPLAY_OFFSET = ZoneOffset.ofHours(8);

    private final SubjectRepository repository;
    private final SubjectService subjectService;
    private final BilibiliFollowerMonitorRepository followerRepository;
    private final BilibiliLiveMonitorRepository liveRepository;
    private final ObjectProvider<BilibiliLiveDanmakuService> danmakuServiceProvider;

    public SubjectWorkbenchService(
            SubjectRepository repository,
            SubjectService subjectService,
            BilibiliFollowerMonitorRepository followerRepository,
            BilibiliLiveMonitorRepository liveRepository,
            ObjectProvider<BilibiliLiveDanmakuService> danmakuServiceProvider
    ) {
        this.repository = repository;
        this.subjectService = subjectService;
        this.followerRepository = followerRepository;
        this.liveRepository = liveRepository;
        this.danmakuServiceProvider = danmakuServiceProvider;
    }

    public SubjectWorkbenchView workbench(Long subjectId) {
        MonitoredSubject subject = subjectService.requireSubject(subjectId);
        SubjectBilibiliBinding binding = repository.findBinding(subject.id()).orElse(null);
        BilibiliMonitoredUser user = resolveUser(binding);
        BilibiliLiveRoomMonitor room = resolveLiveRoom(binding, user);
        subject = backfillSubjectAvatar(subject, user, room);

        OffsetDateTime now = OffsetDateTime.now(DISPLAY_OFFSET);
        OffsetDateTime from = now.minusHours(24);
        List<BilibiliFollowerSnapshot> followerSnapshots = user == null
                ? List.of()
                : followerRepository.findSnapshots(user.id(), from, now, 2000);
        List<BilibiliLiveRoomSnapshot> liveSnapshots = room == null
                ? List.of()
                : liveRepository.findSnapshots(room.id(), from, now, 2000);

        Long followerDelta24h = delta(user == null ? null : user.currentFollowerCount(),
                followerSnapshots.stream().map(BilibiliFollowerSnapshot::followerCount).filter(Objects::nonNull).findFirst().orElse(null));
        Long onlinePeak24h = liveSnapshots.stream()
                .map(BilibiliLiveRoomSnapshot::onlineCount)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(room == null ? null : room.onlineCount());
        Long onlineDelta24h = delta(room == null ? null : room.onlineCount(),
                liveSnapshots.stream().map(BilibiliLiveRoomSnapshot::onlineCount).filter(Objects::nonNull).findFirst().orElse(null));

        SubjectDanmuView danmu = danmuView(binding, room);
        List<SubjectHealthEventView> events = recentEvents(user, room);
        BigDecimal healthScore = calculateHealth(subject, binding, user, room);
        OffsetDateTime lastSuccessAt = maxTime(user == null ? null : user.lastSuccessAt(), room == null ? null : room.lastSuccessAt());
        OffsetDateTime lastEventAt = events.stream()
                .map(SubjectHealthEventView::occurredAt)
                .filter(Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        repository.updateSubjectHealth(subject.id(), healthScore, lastSuccessAt, lastEventAt);
        MonitoredSubject updatedSubject = repository.findSubject(subject.id()).orElse(subject);

        int totalModules = 4;
        int enabledModules = 1;
        if (user != null) {
            enabledModules++;
        }
        if (room != null) {
            enabledModules++;
        }
        if (Boolean.TRUE.equals(binding == null ? null : binding.danmuEnabled())) {
            enabledModules++;
        }

        SubjectSummaryView summary = new SubjectSummaryView(
                user == null ? null : user.currentFollowerCount(),
                followerDelta24h,
                room == null ? null : room.liveStatus(),
                room == null ? null : room.onlineCount(),
                onlineDelta24h,
                onlinePeak24h,
                danmu.ratePerMinute(),
                danmu.last5MinutesCount(),
                healthScore,
                enabledModules,
                totalModules,
                lastSuccessAt,
                minFuture(user == null ? null : user.nextCollectAt(), room == null ? null : room.nextCollectAt())
        );

        return new SubjectWorkbenchView(
                subjectService.toSubjectView(updatedSubject, binding),
                binding == null ? null : subjectService.toBindingView(binding),
                toUserView(user, followerDelta24h),
                toLiveRoomView(room, onlineDelta24h, onlinePeak24h),
                summary,
                danmu,
                repository.findLayouts(subject.id()).stream().map(subjectService::toLayoutView).toList(),
                events
        );
    }

    public SubjectTrendView trends(Long subjectId, String metricsValue, String rangeValue, String bucketValue) {
        subjectService.requireSubject(subjectId);
        SubjectBilibiliBinding binding = repository.findBinding(subjectId).orElse(null);
        BilibiliMonitoredUser user = resolveUser(binding);
        BilibiliLiveRoomMonitor room = resolveLiveRoom(binding, user);

        List<String> metrics = normalizeMetrics(metricsValue);
        String range = rangeValue == null || rangeValue.isBlank() ? "24h" : rangeValue;
        String bucket = bucketValue == null || bucketValue.isBlank() ? "5m" : bucketValue;
        OffsetDateTime to = OffsetDateTime.now(DISPLAY_OFFSET);
        OffsetDateTime from = to.minus(parseDuration(range));
        int bucketSeconds = (int) Math.max(60, parseDuration(bucket).toSeconds());

        TreeMap<OffsetDateTime, MutableTrendPoint> points = new TreeMap<>();
        if (user != null && metrics.contains("follower")) {
            followerRepository.findSnapshots(user.id(), from, to, 3000).forEach(snapshot -> {
                OffsetDateTime bucketAt = bucketAt(snapshot.capturedAt(), bucketSeconds);
                points.computeIfAbsent(bucketAt, MutableTrendPoint::new).followerCount = snapshot.followerCount();
            });
        }
        if (room != null && metrics.contains("live_online")) {
            liveRepository.findSnapshots(room.id(), from, to, 3000).forEach(snapshot -> {
                OffsetDateTime bucketAt = bucketAt(snapshot.capturedAt(), bucketSeconds);
                points.computeIfAbsent(bucketAt, MutableTrendPoint::new).liveOnlineCount = snapshot.onlineCount();
            });
        }

        if (points.isEmpty() && (user != null || room != null)) {
            MutableTrendPoint point = new MutableTrendPoint(bucketAt(to, bucketSeconds));
            point.followerCount = user == null ? null : user.currentFollowerCount();
            point.liveOnlineCount = room == null ? null : room.onlineCount();
            points.put(point.bucketAt, point);
        }

        return new SubjectTrendView(
                subjectId,
                metrics,
                range,
                bucket,
                points.values().stream()
                        .map(point -> new SubjectTrendPointView(point.bucketAt, point.followerCount, point.liveOnlineCount))
                        .toList()
        );
    }

    private SubjectBilibiliUserView toUserView(BilibiliMonitoredUser user, Long followerDelta24h) {
        if (user == null) {
            return null;
        }
        return new SubjectBilibiliUserView(
                user.id(),
                user.mid(),
                user.nickname(),
                user.avatarUrl(),
                user.profileUrl(),
                user.currentFollowerCount(),
                user.followingCount(),
                followerDelta24h,
                user.monitorStatus(),
                user.lastSuccessAt(),
                user.nextCollectAt(),
                user.lastErrorType(),
                user.lastErrorMessage()
        );
    }

    private SubjectBilibiliLiveRoomView toLiveRoomView(BilibiliLiveRoomMonitor room, Long onlineDelta24h, Long onlinePeak24h) {
        if (room == null) {
            return null;
        }
        return new SubjectBilibiliLiveRoomView(
                room.id(),
                room.uid(),
                room.roomId(),
                room.uname(),
                room.faceUrl(),
                room.title(),
                room.coverUrl(),
                room.keyframeUrl(),
                room.areaName(),
                room.parentAreaName(),
                room.liveStatus(),
                room.liveTime(),
                room.onlineCount(),
                onlineDelta24h,
                onlinePeak24h,
                room.monitorStatus(),
                room.lastSuccessAt(),
                room.nextCollectAt(),
                room.lastErrorType(),
                room.lastErrorMessage()
        );
    }

    private SubjectDanmuView danmuView(SubjectBilibiliBinding binding, BilibiliLiveRoomMonitor room) {
        boolean enabled = Boolean.TRUE.equals(binding == null ? null : binding.danmuEnabled());
        if (!enabled) {
            return new SubjectDanmuView(
                    false,
                    "disabled",
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.<SubjectDanmuRecentMessageView>of()
            );
        }
        if (room == null) {
            return new SubjectDanmuView(
                    true,
                    "missing_live_room",
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.<SubjectDanmuRecentMessageView>of()
            );
        }
        BilibiliLiveDanmakuService danmakuService = danmakuServiceProvider.getIfAvailable();
        if (danmakuService == null) {
            return new SubjectDanmuView(
                    true,
                    "disabled",
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.<SubjectDanmuRecentMessageView>of()
            );
        }
        try {
            BilibiliLiveDanmakuStatusView status = danmakuService.status(room.id());
            List<BilibiliLiveDanmakuRecentView> recent = danmakuService.recent(room.id(), 8);
            List<SubjectDanmuRecentMessageView> messages = recent.stream()
                    .map(message -> new SubjectDanmuRecentMessageView(
                            message.displayName(),
                            message.messageText(),
                            message.medalName(),
                            message.sentAt()
                    ))
                    .toList();
            OffsetDateTime lastMessageAt = messages.stream()
                    .map(SubjectDanmuRecentMessageView::sentAt)
                    .filter(Objects::nonNull)
                    .max(OffsetDateTime::compareTo)
                    .orElse(null);
            return new SubjectDanmuView(
                    true,
                    normalizeDanmuStatus(status.status(), status.running()),
                    status.ratePerMinute(),
                    status.last5MinutesCount(),
                    status.likeIncrement(),
                    status.watchedCount(),
                    lastMessageAt,
                    messages
            );
        } catch (RuntimeException exception) {
            return new SubjectDanmuView(
                    true,
                    "error",
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.<SubjectDanmuRecentMessageView>of()
            );
        }
    }

    private String normalizeDanmuStatus(String status, boolean running) {
        if (running) {
            return "connected";
        }
        if (status == null || status.isBlank() || "NOT_STARTED".equals(status)) {
            return "waiting";
        }
        if ("ERROR".equals(status)) {
            return "error";
        }
        if ("STOPPED".equals(status) || "CLOSED".equals(status)) {
            return "stopped";
        }
        return status.toLowerCase(Locale.ROOT);
    }

    private List<SubjectHealthEventView> recentEvents(BilibiliMonitoredUser user, BilibiliLiveRoomMonitor room) {
        List<SubjectHealthEventView> events = new ArrayList<>();
        if (room != null) {
            events.addAll(repository.findRecentLiveEvents(room.id(), 6));
            if (room.lastErrorAt() != null) {
                events.add(new SubjectHealthEventView(
                        "LIVE_COLLECT_ERROR",
                        "直播间采集异常",
                        room.lastErrorMessage(),
                        "bilibili-live",
                        room.lastErrorAt(),
                        "warning"
                ));
            }
            if (room.lastSuccessAt() != null) {
                events.add(new SubjectHealthEventView(
                        "LIVE_COLLECT_OK",
                        "直播间采集成功",
                        room.title(),
                        "bilibili-live",
                        room.lastSuccessAt(),
                        "success"
                ));
            }
        }
        if (user != null) {
            if (user.lastErrorAt() != null) {
                events.add(new SubjectHealthEventView(
                        "FOLLOWER_COLLECT_ERROR",
                        "粉丝数采集异常",
                        user.lastErrorMessage(),
                        "bilibili-follower",
                        user.lastErrorAt(),
                        "warning"
                ));
            }
            if (user.lastSuccessAt() != null) {
                events.add(new SubjectHealthEventView(
                        "FOLLOWER_COLLECT_OK",
                        "粉丝数采集成功",
                        user.nickname(),
                        "bilibili-follower",
                        user.lastSuccessAt(),
                        "success"
                ));
            }
        }
        if (events.isEmpty()) {
            events.add(new SubjectHealthEventView(
                    "SUBJECT_CREATED",
                    "工作台已创建",
                    "绑定 B站粉丝监控或直播间监控后会显示采集事件。",
                    "subject",
                    OffsetDateTime.now(DISPLAY_OFFSET),
                    "info"
            ));
        }
        return events.stream()
                .sorted(Comparator.comparing(SubjectHealthEventView::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(8)
                .toList();
    }

    private BigDecimal calculateHealth(
            MonitoredSubject subject,
            SubjectBilibiliBinding binding,
            BilibiliMonitoredUser user,
            BilibiliLiveRoomMonitor room
    ) {
        int score = 100;
        if (!"ACTIVE".equals(subject.monitorStatus())) {
            score -= 25;
        }
        if (binding == null) {
            score -= 30;
        }
        if (user == null) {
            score -= 18;
        } else if (user.lastErrorAt() != null) {
            score -= 12;
        }
        if (room == null) {
            score -= 18;
        } else if (room.lastErrorAt() != null) {
            score -= 12;
        }
        OffsetDateTime lastSuccessAt = maxTime(user == null ? null : user.lastSuccessAt(), room == null ? null : room.lastSuccessAt());
        if (lastSuccessAt == null) {
            score -= 12;
        } else if (Duration.between(lastSuccessAt, OffsetDateTime.now(DISPLAY_OFFSET)).toHours() >= 24) {
            score -= 8;
        }
        return BigDecimal.valueOf(Math.max(0, Math.min(100, score))).setScale(2, RoundingMode.HALF_UP);
    }

    private BilibiliMonitoredUser resolveUser(SubjectBilibiliBinding binding) {
        if (binding == null) {
            return null;
        }
        if (binding.bilibiliUserMonitorId() != null) {
            return followerRepository.findById(binding.bilibiliUserMonitorId()).orElse(null);
        }
        if (binding.mid() != null) {
            return followerRepository.findByMid(binding.mid()).orElse(null);
        }
        return null;
    }

    private MonitoredSubject backfillSubjectAvatar(
            MonitoredSubject subject,
            BilibiliMonitoredUser user,
            BilibiliLiveRoomMonitor room
    ) {
        if (subject.avatarUrl() != null && !subject.avatarUrl().isBlank()) {
            return subject;
        }
        String avatarUrl = null;
        if (user != null && user.avatarUrl() != null && !user.avatarUrl().isBlank()) {
            avatarUrl = user.avatarUrl();
        } else if (room != null && room.faceUrl() != null && !room.faceUrl().isBlank()) {
            avatarUrl = room.faceUrl();
        }
        if (avatarUrl == null) {
            return subject;
        }
        return repository.updateSubject(subject.id(), null, avatarUrl, null, null, null);
    }

    private BilibiliLiveRoomMonitor resolveLiveRoom(SubjectBilibiliBinding binding, BilibiliMonitoredUser user) {
        if (binding == null) {
            return null;
        }
        if (binding.bilibiliLiveRoomMonitorId() != null) {
            return liveRepository.findById(binding.bilibiliLiveRoomMonitorId()).orElse(null);
        }
        if (binding.roomId() != null) {
            return liveRepository.findByRoomId(binding.roomId()).orElse(null);
        }
        Long uid = binding.mid() != null ? binding.mid() : user == null ? null : user.mid();
        return uid == null ? null : liveRepository.findByUid(uid).orElse(null);
    }

    private List<String> normalizeMetrics(String metricsValue) {
        if (metricsValue == null || metricsValue.isBlank()) {
            return List.of("follower", "live_online");
        }
        List<String> metrics = List.of(metricsValue.split(",")).stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> value.equals("follower") || value.equals("live_online"))
                .distinct()
                .toList();
        return metrics.isEmpty() ? List.of("follower", "live_online") : metrics;
    }

    private Duration parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ofHours(24);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        long amount;
        try {
            amount = Long.parseLong(normalized.substring(0, normalized.length() - 1));
        } catch (RuntimeException exception) {
            return Duration.ofHours(24);
        }
        if (normalized.endsWith("m")) {
            return Duration.ofMinutes(Math.max(1, amount));
        }
        if (normalized.endsWith("h")) {
            return Duration.ofHours(Math.max(1, amount));
        }
        if (normalized.endsWith("d")) {
            return Duration.ofDays(Math.max(1, amount));
        }
        return Duration.ofHours(24);
    }

    private OffsetDateTime bucketAt(OffsetDateTime capturedAt, int bucketSeconds) {
        long epoch = capturedAt.toEpochSecond();
        long bucketEpoch = (epoch / bucketSeconds) * bucketSeconds;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(bucketEpoch), DISPLAY_OFFSET);
    }

    private Long delta(Long current, Long previous) {
        return current == null || previous == null ? null : current - previous;
    }

    private OffsetDateTime maxTime(OffsetDateTime first, OffsetDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private OffsetDateTime minFuture(OffsetDateTime first, OffsetDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isBefore(second) ? first : second;
    }

    private static final class MutableTrendPoint {
        private final OffsetDateTime bucketAt;
        private Long followerCount;
        private Long liveOnlineCount;

        private MutableTrendPoint(OffsetDateTime bucketAt) {
            this.bucketAt = bucketAt;
        }
    }
}
