package com.socialmonitor.bilibili.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.auth.client.BilibiliPassportClient;
import com.socialmonitor.bilibili.auth.config.BilibiliAuthProperties;
import com.socialmonitor.bilibili.auth.dto.QrLoginStartView;
import com.socialmonitor.bilibili.auth.dto.QrLoginStatusView;
import java.net.CookieManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class BilibiliAuthServiceTests {

    @Test
    void startsQrLoginWithOneGeneratedQrAndStoresSession() {
        BilibiliAuthProperties properties = new BilibiliAuthProperties();
        properties.setQrExpireSeconds(180);
        properties.setPollIntervalMs(1500);
        FakePassportClient passportClient = new FakePassportClient(properties);
        BilibiliQrLoginSessionStore sessionStore = new BilibiliQrLoginSessionStore(properties);
        BilibiliAuthService service = new BilibiliAuthService(properties, passportClient, sessionStore, null);

        QrLoginStartView view = service.startQrLogin();

        assertThat(passportClient.generateCount).isEqualTo(1);
        assertThat(view.qrUrl()).isEqualTo("https://account.bilibili.com/qrcode");
        assertThat(view.expiresInSeconds()).isEqualTo(180);
        assertThat(view.pollIntervalMillis()).isEqualTo(1500);
        assertThat(sessionStore.find(view.loginId()))
                .get()
                .satisfies(session -> {
                    assertThat(session.qrcodeKey()).isEqualTo("qr-key");
                    assertThat(session.qrUrl()).isEqualTo("https://account.bilibili.com/qrcode");
                });
    }

    @Test
    void mapsQrPollWaitingScannedExpiredAndFailedStatuses() {
        BilibiliAuthProperties properties = new BilibiliAuthProperties();
        FakePassportClient passportClient = new FakePassportClient(properties);
        BilibiliQrLoginSessionStore sessionStore = new BilibiliQrLoginSessionStore(properties);
        BilibiliAuthService service = new BilibiliAuthService(properties, passportClient, sessionStore, null);
        QrLoginStartView view = service.startQrLogin();

        passportClient.nextPoll = new BilibiliPassportClient.QrPollResult(86101, "waiting", null, null, List.of());
        assertThat(service.pollQrLogin(view.loginId()).status()).isEqualTo("WAITING");

        passportClient.nextPoll = new BilibiliPassportClient.QrPollResult(86090, "scanned", null, null, List.of());
        assertThat(service.pollQrLogin(view.loginId()).status()).isEqualTo("SCANNED");

        passportClient.nextPoll = new BilibiliPassportClient.QrPollResult(86038, "expired", null, null, List.of());
        QrLoginStatusView expired = service.pollQrLogin(view.loginId());
        assertThat(expired.status()).isEqualTo("EXPIRED");

        assertThat(service.pollQrLogin(view.loginId()).status()).isEqualTo("EXPIRED");

        QrLoginStartView second = service.startQrLogin();
        passportClient.nextPoll = new BilibiliPassportClient.QrPollResult(12345, "risk", null, null, List.of());
        assertThat(service.pollQrLogin(second.loginId()).status()).isEqualTo("FAILED");
    }

    private static class FakePassportClient extends BilibiliPassportClient {
        private int generateCount;
        private BilibiliPassportClient.QrPollResult nextPoll =
                new BilibiliPassportClient.QrPollResult(86101, "waiting", null, null, List.of());

        private FakePassportClient(BilibiliAuthProperties properties) {
            super(properties, new ObjectMapper());
        }

        @Override
        public CookieManager newCookieManager() {
            return new CookieManager();
        }

        @Override
        public QrGenerateResult generateQrCode(CookieManager cookieManager) {
            generateCount++;
            return new QrGenerateResult("https://account.bilibili.com/qrcode", "qr-key");
        }

        @Override
        public QrPollResult pollQrCode(CookieManager cookieManager, String qrcodeKey) {
            return nextPoll;
        }
    }
}
