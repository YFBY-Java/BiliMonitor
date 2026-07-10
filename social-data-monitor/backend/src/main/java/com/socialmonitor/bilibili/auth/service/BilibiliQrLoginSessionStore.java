package com.socialmonitor.bilibili.auth.service;

import com.socialmonitor.bilibili.auth.config.BilibiliAuthProperties;
import com.socialmonitor.bilibili.auth.domain.BilibiliQrLoginSession;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BilibiliQrLoginSessionStore {

    private static final ZoneOffset DISPLAY_OFFSET = ZoneOffset.ofHours(8);

    private final BilibiliAuthProperties properties;
    private final Map<String, BilibiliQrLoginSession> sessions = new ConcurrentHashMap<>();

    public BilibiliQrLoginSessionStore(BilibiliAuthProperties properties) {
        this.properties = properties;
    }

    public void put(BilibiliQrLoginSession session) {
        sessions.put(session.loginId(), session);
    }

    public Optional<BilibiliQrLoginSession> find(String loginId) {
        return Optional.ofNullable(sessions.get(loginId));
    }

    public void update(BilibiliQrLoginSession session) {
        sessions.put(session.loginId(), session);
    }

    public void remove(String loginId) {
        sessions.remove(loginId);
    }

    @Scheduled(fixedDelayString = "${app.bilibili.auth.session-cleanup-delay-ms:60000}")
    public void cleanupExpired() {
        OffsetDateTime now = OffsetDateTime.now(DISPLAY_OFFSET);
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().plusSeconds(properties.getQrExpireSeconds()).isBefore(now));
    }
}
