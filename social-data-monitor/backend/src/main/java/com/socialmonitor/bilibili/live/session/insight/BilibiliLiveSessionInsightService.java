package com.socialmonitor.bilibili.live.session.insight;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionInsightView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionUserView;
import com.socialmonitor.bilibili.live.session.query.BilibiliLiveSessionQueryRepository;
import com.socialmonitor.bilibili.live.session.query.BilibiliLiveSessionQueryService;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSessionInsightService {

    private static final Set<Integer> BUCKET_SECONDS = Set.of(60, 300, 900);

    private final BilibiliLiveSessionQueryService queryService;
    private final BilibiliLiveSessionQueryRepository queryRepository;
    private final BilibiliLiveSessionInsightRepository insightRepository;
    private final BilibiliLiveSessionInsightCalculator calculator;

    public BilibiliLiveSessionInsightService(
            BilibiliLiveSessionQueryService queryService,
            BilibiliLiveSessionQueryRepository queryRepository,
            BilibiliLiveSessionInsightRepository insightRepository,
            BilibiliLiveSessionInsightCalculator calculator
    ) {
        this.queryService = queryService;
        this.queryRepository = queryRepository;
        this.insightRepository = insightRepository;
        this.calculator = calculator;
    }

    public BilibiliLiveSessionInsightView insight(Long sessionId, int bucketSeconds) {
        if (!BUCKET_SECONDS.contains(bucketSeconds)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "时间粒度仅支持 60、300 或 900 秒");
        }
        BilibiliLiveSessionSummaryView summary = queryService.session(sessionId);
        List<BilibiliLiveSessionUserView> users = queryRepository.findUsers(sessionId, 500);
        long coveredSeconds = calculator.mergeCoveredSeconds(insightRepository.findCoverageIntervals(sessionId));
        List<BilibiliLiveSessionInsightView.TimeBucket> timeline =
                insightRepository.findTimeline(sessionId, bucketSeconds);
        BilibiliLiveSessionInsightView.Kpis kpis = calculator.calculateKpis(summary, coveredSeconds, users);
        List<BilibiliLiveSessionInsightView.GiftMix> giftMix = giftMix(
                insightRepository.findGiftMix(sessionId), summary.paidAmountMilliYuan());
        BilibiliLiveSessionInsightView.DanmakuDepth danmakuDepth = calculator.calculateDanmakuDepth(
                summary,
                insightRepository.findDanmakuDepth(sessionId),
                insightRepository.findDanmakuStages(sessionId),
                insightRepository.findRepeatedMessages(sessionId));
        BilibiliLiveSessionInsightView.PaymentDepth paymentDepth = calculator.calculatePaymentDepth(
                summary,
                insightRepository.findPaymentDepth(sessionId),
                insightRepository.findSpendTiers(sessionId));
        BilibiliLiveSessionInsightRepository.QualityStats stats = insightRepository.findQuality(sessionId);
        return new BilibiliLiveSessionInsightView(
                sessionId,
                bucketSeconds,
                kpis,
                timeline,
                calculator.peaks(timeline),
                insightRepository.findUserSegments(sessionId),
                giftMix,
                calculator.findings(timeline, kpis, danmakuDepth, paymentDepth),
                danmakuDepth,
                paymentDepth,
                quality(summary, coveredSeconds, stats)
        );
    }

    private List<BilibiliLiveSessionInsightView.GiftMix> giftMix(
            List<BilibiliLiveSessionInsightRepository.GiftAggregate> rows,
            Long totalPaidAmount
    ) {
        return rows.stream().map(row -> new BilibiliLiveSessionInsightView.GiftMix(
                row.giftName(),
                row.eventKind(),
                row.giftCount(),
                row.paidAmountMilliYuan(),
                totalPaidAmount == null || totalPaidAmount == 0
                        ? 0.0
                        : Math.round(row.paidAmountMilliYuan() * 100_000.0 / totalPaidAmount) / 100_000.0
        )).toList();
    }

    private BilibiliLiveSessionInsightView.Quality quality(
            BilibiliLiveSessionSummaryView summary,
            long coveredSeconds,
            BilibiliLiveSessionInsightRepository.QualityStats stats
    ) {
        Double resolvedShare = stats.supportedEventCount() == 0
                ? null
                : Math.round(stats.resolvedEventCount() * 100_000.0 / stats.supportedEventCount()) / 100_000.0;
        return new BilibiliLiveSessionInsightView.Quality(
                summary.coverageStatus(),
                coveredSeconds,
                resolvedShare,
                stats.supportedEventCount(),
                summary.unresolvedInteractingEventCount() == null ? 0L : summary.unresolvedInteractingEventCount(),
                stats.latencyP95Millis(),
                caveat(summary.coverageStatus())
        );
    }

    private String caveat(String coverageStatus) {
        return switch (coverageStatus) {
            case "RECEIVED_WHILE_ONLINE" -> "仅覆盖 WebSocket 在线期间成功解析并持久化的受支持事件，不代表平台全量。";
            case "BOUNDARY_ONLY" -> "仅有历史场次边界，没有可验证的在线采集区间；空白指标不能解释为 0。";
            default -> "没有可验证的在线采集区间，当前指标不具备完整覆盖保证。";
        };
    }
}
