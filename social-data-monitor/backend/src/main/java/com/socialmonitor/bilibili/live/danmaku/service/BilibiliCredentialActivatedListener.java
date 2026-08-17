package com.socialmonitor.bilibili.live.danmaku.service;

import com.socialmonitor.bilibili.auth.event.BilibiliCredentialActivatedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliCredentialActivatedListener {

    private final BilibiliLiveDanmakuService danmakuService;

    public BilibiliCredentialActivatedListener(BilibiliLiveDanmakuService danmakuService) {
        this.danmakuService = danmakuService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCredentialActivated(BilibiliCredentialActivatedEvent event) {
        danmakuService.reconnectAnonymousConnectionsAsync();
    }
}
