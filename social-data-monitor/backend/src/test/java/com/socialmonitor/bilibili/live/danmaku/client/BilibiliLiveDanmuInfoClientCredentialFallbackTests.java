package com.socialmonitor.bilibili.live.danmaku.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.auth.repository.BilibiliCredentialRepository;
import com.socialmonitor.bilibili.live.config.BilibiliLiveMonitorProperties;
import com.socialmonitor.bilibili.live.danmaku.config.BilibiliLiveDanmakuProperties;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveDanmuInfoClientCredentialFallbackTests {

    @Mock
    private BilibiliWbiSigner wbiSigner;
    @Mock
    private BilibiliAnonymousCookieProvider cookieProvider;
    @Mock
    private ObjectProvider<BilibiliCredentialRepository> credentialRepositoryProvider;
    @Mock
    private BilibiliCredentialRepository credentialRepository;

    @Test
    void credentialDecryptionFailureFallsBackToAnonymousCredentialResolution() throws Exception {
        BilibiliLiveDanmakuProperties danmakuProperties = new BilibiliLiveDanmakuProperties();
        danmakuProperties.setUseLoginCredential(true);
        when(credentialRepositoryProvider.getIfAvailable()).thenReturn(credentialRepository);
        when(credentialRepository.findActive()).thenThrow(new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "Failed to decrypt stored Bilibili credential"
        ));
        BilibiliLiveDanmuInfoClient client = new BilibiliLiveDanmuInfoClient(
                new ObjectMapper(),
                new BilibiliLiveMonitorProperties(),
                danmakuProperties,
                wbiSigner,
                cookieProvider,
                credentialRepositoryProvider
        );

        Method loginCredential = BilibiliLiveDanmuInfoClient.class.getDeclaredMethod("loginCredential");
        loginCredential.setAccessible(true);

        Optional<?> resolved = (Optional<?>) loginCredential.invoke(client);

        assertThat(resolved).isEmpty();
    }
}
