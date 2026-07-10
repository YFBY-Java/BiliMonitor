package com.socialmonitor.bilibili.live.danmaku.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveDanmakuScheduler {

    private static final Logger log = LoggerFactory.getLogger(BilibiliLiveDanmakuScheduler.class);

    private final BilibiliLiveDanmakuService danmakuService;

    public BilibiliLiveDanmakuScheduler(BilibiliLiveDanmakuService danmakuService) {
        this.danmakuService = danmakuService;
    }

    @Scheduled(fixedDelayString = "${app.bilibili.live-monitor.danmaku.scheduler-delay-ms:5000}")
    public void syncAutoConnections() {
        try {
            danmakuService.syncAutoConnections();
        } catch (Exception exception) {
            log.warn("Bilibili danmaku auto connection sync failed: {}", exception.getMessage());
        }
    }
}
