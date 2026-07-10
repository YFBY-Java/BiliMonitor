package com.socialmonitor.bilibili.service;

import com.socialmonitor.bilibili.dto.BilibiliCollectResultView;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.bilibili.follower-monitor",
        name = {"enabled", "storage-enabled"},
        matchIfMissing = true
)
public class BilibiliFollowerMonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(BilibiliFollowerMonitorScheduler.class);

    private final BilibiliFollowerMonitorService monitorService;

    public BilibiliFollowerMonitorScheduler(BilibiliFollowerMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Scheduled(fixedDelayString = "${app.bilibili.follower-monitor.scheduler-delay-ms:60000}")
    public void collectDueUsers() {
        List<BilibiliCollectResultView> results = monitorService.collectDueUsers();
        if (!results.isEmpty()) {
            long successCount = results.stream().filter(BilibiliCollectResultView::success).count();
            log.info("Bilibili follower monitor tick finished. total={}, success={}", results.size(), successCount);
        }
    }
}
