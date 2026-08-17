package com.socialmonitor.bilibili.live.danmaku.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.socialmonitor.bilibili.auth.event.BilibiliCredentialActivatedEvent;
import org.junit.jupiter.api.Test;

class BilibiliCredentialActivatedListenerTests {

    @Test
    void schedulesAnonymousConnectionUpgradeAfterCredentialActivation() {
        BilibiliLiveDanmakuService danmakuService = mock(BilibiliLiveDanmakuService.class);
        BilibiliCredentialActivatedListener listener = new BilibiliCredentialActivatedListener(danmakuService);

        listener.onCredentialActivated(new BilibiliCredentialActivatedEvent(7L));

        verify(danmakuService).reconnectAnonymousConnectionsAsync();
    }
}
