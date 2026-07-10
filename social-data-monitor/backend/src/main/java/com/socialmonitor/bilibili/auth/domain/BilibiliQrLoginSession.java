package com.socialmonitor.bilibili.auth.domain;

import com.socialmonitor.bilibili.auth.dto.QrLoginStatusView;
import java.net.CookieManager;
import java.time.OffsetDateTime;

public record BilibiliQrLoginSession(
        String loginId,
        String qrcodeKey,
        String qrUrl,
        CookieManager cookieManager,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        QrLoginStatusView completedView
) {
    public BilibiliQrLoginSession withCompletedView(QrLoginStatusView view) {
        return new BilibiliQrLoginSession(loginId, qrcodeKey, qrUrl, cookieManager, createdAt, expiresAt, view);
    }
}
