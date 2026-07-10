package com.socialmonitor.bilibili.live.service;

import com.socialmonitor.bilibili.live.dto.BilibiliLiveCollectResultView;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.bilibili.live-monitor",
        name = {"enabled", "storage-enabled"},
        matchIfMissing = true
)
public class BilibiliLiveMonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(BilibiliLiveMonitorScheduler.class);

    private final BilibiliLiveMonitorService monitorService;

    public BilibiliLiveMonitorScheduler(BilibiliLiveMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Scheduled(fixedDelayString = "${app.bilibili.live-monitor.scheduler-delay-ms:1000}")
    public void collectDueRooms() {
        List<BilibiliLiveCollectResultView> results = monitorService.collectDueRooms();
        if (!results.isEmpty()) {
            long successCount = results.stream().filter(BilibiliLiveCollectResultView::success).count();
            log.info("Bilibili live monitor tick finished. total={}, success={}", results.size(), successCount);
        }
    }
}
