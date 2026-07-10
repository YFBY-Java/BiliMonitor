package com.socialmonitor.bilibili.auth.service;

import com.socialmonitor.bilibili.auth.client.BilibiliPassportClient;
import com.socialmonitor.bilibili.auth.client.BilibiliPassportClient.NavResult;
import com.socialmonitor.bilibili.auth.client.BilibiliPassportClient.QrGenerateResult;
import com.socialmonitor.bilibili.auth.client.BilibiliPassportClient.QrPollResult;
import com.socialmonitor.bilibili.auth.config.BilibiliAuthProperties;
import com.socialmonitor.bilibili.auth.domain.BilibiliAccount;
import com.socialmonitor.bilibili.auth.domain.BilibiliCookie;
import com.socialmonitor.bilibili.auth.domain.BilibiliCookieState;
import com.socialmonitor.bilibili.auth.domain.BilibiliQrLoginSession;
import com.socialmonitor.bilibili.auth.domain.PersistedBilibiliCredential;
import com.socialmonitor.bilibili.auth.dto.BilibiliAccountView;
import com.socialmonitor.bilibili.auth.dto.BilibiliAuthRefreshView;
import com.socialmonitor.bilibili.auth.dto.BilibiliAuthStatusView;
import com.socialmonitor.bilibili.auth.dto.BilibiliCookieView;
import com.socialmonitor.bilibili.auth.dto.BilibiliCredentialFullView;
import com.socialmonitor.bilibili.auth.dto.QrLoginStartView;
import com.socialmonitor.bilibili.auth.dto.QrLoginStatusView;
import com.socialmonitor.bilibili.auth.repository.BilibiliCredentialRepository;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import java.net.CookieManager;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BilibiliAuthService {

    private static final ZoneOffset DISPLAY_OFFSET = ZoneOffset.ofHours(8);

    private final BilibiliAuthProperties properties;
    private final BilibiliPassportClient passportClient;
    private final BilibiliQrLoginSessionStore sessionStore;
    private final BilibiliCredentialRepository credentialRepository;

    public BilibiliAuthService(
            BilibiliAuthProperties properties,
            BilibiliPassportClient passportClient,
            BilibiliQrLoginSessionStore sessionStore,
            BilibiliCredentialRepository credentialRepository
    ) {
        this.properties = properties;
        this.passportClient = passportClient;
        this.sessionStore = sessionStore;
        this.credentialRepository = credentialRepository;
    }

    public QrLoginStartView startQrLogin() {
        ensureEnabled();
        String loginId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(DISPLAY_OFFSET);
        CookieManager cookieManager = passportClient.newCookieManager();
        QrGenerateResult result = passportClient.generateQrCode(cookieManager);
        BilibiliQrLoginSession session = new BilibiliQrLoginSession(
                loginId,
                result.qrcodeKey(),
                result.qrUrl(),
                cookieManager,
                now,
                now.plusSeconds(properties.getQrExpireSeconds()),
                null
        );
        sessionStore.put(session);
        return new QrLoginStartView(loginId, result.qrUrl(), properties.getQrExpireSeconds(), properties.getPollIntervalMs());
    }

    @Transactional
    public QrLoginStatusView pollQrLogin(String loginId) {
        ensureEnabled();
        BilibiliQrLoginSession session = sessionStore.find(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "QR login session not found."));
        if (session.completedView() != null) {
            return session.completedView();
        }
        int expiresIn = expiresInSeconds(session);
        if (expiresIn <= 0) {
            QrLoginStatusView expired = new QrLoginStatusView("EXPIRED", "二维码已过期，请重新扫码", 0, null, null);
            sessionStore.update(session.withCompletedView(expired));
            return expired;
        }
        QrPollResult result = passportClient.pollQrCode(session.cookieManager(), session.qrcodeKey());
        return switch (result.code()) {
            case 86101 -> new QrLoginStatusView("WAITING", "等待使用 Bilibili 手机客户端扫码", expiresIn, null, null);
            case 86090 -> new QrLoginStatusView("SCANNED", "已扫码，请在手机端确认登录", expiresIn, null, null);
            case 86038 -> {
                QrLoginStatusView expired = new QrLoginStatusView("EXPIRED", "二维码已过期，请重新扫码", 0, null, null);
                sessionStore.update(session.withCompletedView(expired));
                yield expired;
            }
            case 0 -> completeLogin(session, result);
            default -> new QrLoginStatusView("FAILED", "Bilibili 返回未知扫码状态: " + result.message(), expiresIn, null, null);
        };
    }

    public BilibiliAuthStatusView currentStatus() {
        return credentialRepository.findActive()
                .map(credential -> {
                    try {
                        PersistedBilibiliCredential refreshed = validateAndPersist(credential);
                        return new BilibiliAuthStatusView(
                                true,
                                refreshed.credentialId(),
                                toAccountView(refreshed.state().account()),
                                refreshed.state().lastValidatedAt(),
                                refreshed.state().lastRefreshCheckedAt(),
                                refreshed.state().expiresAt(),
                                refreshed.status(),
                                toCredentialView(refreshed)
                        );
                    } catch (BusinessException exception) {
                        credentialRepository.markStatus(credential.credentialId(), "EXPIRED");
                        return new BilibiliAuthStatusView(false, credential.credentialId(), toAccountView(credential.state().account()),
                                credential.state().lastValidatedAt(), credential.state().lastRefreshCheckedAt(),
                                credential.state().expiresAt(), "EXPIRED", null);
                    }
                })
                .orElseGet(() -> new BilibiliAuthStatusView(false, null, null, null, null, null, "NONE", null));
    }

    public BilibiliCredentialFullView currentCredential() {
        PersistedBilibiliCredential credential = credentialRepository.findActive()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "No active Bilibili credential."));
        return toCredentialView(validateAndPersist(credential));
    }

    public BilibiliAuthRefreshView refreshCurrentCredential() {
        PersistedBilibiliCredential credential = credentialRepository.findActive()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "No active Bilibili credential."));
        PersistedBilibiliCredential validated = validateAndPersist(credential);
        return new BilibiliAuthRefreshView(false, true, "登录态已重新校验；Cookie 刷新链路将在后续阶段接入。", toAccountView(validated.state().account()));
    }

    public void revokeCurrentCredential() {
        credentialRepository.revokeActive();
    }

    private QrLoginStatusView completeLogin(BilibiliQrLoginSession session, QrPollResult result) {
        if (result.cookies().isEmpty()) {
            return new QrLoginStatusView("FAILED", "登录成功但未收到 Bilibili Cookie，请重试", expiresInSeconds(session), null, null);
        }
        OffsetDateTime now = OffsetDateTime.now(DISPLAY_OFFSET);
        OffsetDateTime expiresAt = result.cookies().stream()
                .map(BilibiliCookie::expiresAt)
                .filter(value -> value != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        BilibiliCookieState provisionalState = new BilibiliCookieState(
                result.cookies(),
                result.refreshToken(),
                null,
                expiresAt,
                now,
                null,
                Map.of()
        );
        requireMinimumCookies(provisionalState);
        NavResult nav = passportClient.fetchNav(provisionalState);
        BilibiliCookieState state = new BilibiliCookieState(
                result.cookies(),
                result.refreshToken(),
                nav.account(),
                expiresAt,
                now,
                null,
                nav.rawPayload()
        );
        PersistedBilibiliCredential credential = credentialRepository.saveActive(state);
        QrLoginStatusView success = new QrLoginStatusView(
                "SUCCESS",
                "Bilibili 登录态已保存",
                0,
                toAccountView(credential.state().account()),
                toCredentialView(credential)
        );
        sessionStore.update(session.withCompletedView(success));
        return success;
    }

    private PersistedBilibiliCredential validateAndPersist(PersistedBilibiliCredential credential) {
        NavResult nav = passportClient.fetchNav(credential.state());
        BilibiliCookieState state = new BilibiliCookieState(
                credential.state().cookies(),
                credential.state().refreshToken(),
                nav.account(),
                credential.state().expiresAt(),
                OffsetDateTime.now(DISPLAY_OFFSET),
                credential.state().lastRefreshCheckedAt(),
                nav.rawPayload()
        );
        credentialRepository.updateState(credential.credentialId(), state, "ACTIVE");
        return new PersistedBilibiliCredential(
                credential.credentialId(),
                credential.platformId(),
                "ACTIVE",
                state,
                credential.createdAt(),
                OffsetDateTime.now(DISPLAY_OFFSET)
        );
    }

    private void requireMinimumCookies(BilibiliCookieState state) {
        if (state.cookieValue("SESSDATA") == null || state.cookieValue("bili_jct") == null || state.cookieValue("DedeUserID") == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Bilibili login cookie is incomplete.");
        }
    }

    private BilibiliCredentialFullView toCredentialView(PersistedBilibiliCredential credential) {
        Map<String, Object> rawPayload = credentialRepository.toPlainPayload(credential.state(), credential.createdAt());
        return new BilibiliCredentialFullView(
                credential.credentialId(),
                toAccountView(credential.state().account()),
                credential.state().cookieHeader(),
                credential.state().cookies().stream().map(this::toCookieView).toList(),
                credential.state().csrf(),
                credential.state().refreshToken(),
                credential.state().expiresAt(),
                rawPayload
        );
    }

    private BilibiliCookieView toCookieView(BilibiliCookie cookie) {
        return new BilibiliCookieView(
                cookie.name(),
                cookie.value(),
                cookie.domain(),
                cookie.path(),
                cookie.expiresAt(),
                cookie.httpOnly(),
                cookie.secure(),
                cookie.sameSite()
        );
    }

    private BilibiliAccountView toAccountView(BilibiliAccount account) {
        if (account == null) {
            return null;
        }
        return new BilibiliAccountView(account.mid(), account.uname(), account.face(), account.level(), account.vipStatus());
    }

    private int expiresInSeconds(BilibiliQrLoginSession session) {
        long seconds = Duration.between(OffsetDateTime.now(DISPLAY_OFFSET), session.expiresAt()).toSeconds();
        return (int) Math.max(0, seconds);
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Bilibili auth is disabled.");
        }
    }
}
