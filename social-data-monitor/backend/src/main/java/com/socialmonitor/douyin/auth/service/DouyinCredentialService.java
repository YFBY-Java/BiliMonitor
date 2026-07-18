package com.socialmonitor.douyin.auth.service;

import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import com.socialmonitor.douyin.auth.domain.DouyinAuthConstants;
import com.socialmonitor.douyin.auth.domain.DouyinAuthSession;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.dto.DouyinAuthStatusView;
import com.socialmonitor.douyin.auth.dto.DouyinCredentialFullView;
import com.socialmonitor.douyin.auth.repository.DouyinAuthSessionRepository;
import com.socialmonitor.douyin.auth.repository.DouyinCredentialRepository;
import com.socialmonitor.douyin.worker.dto.WorkerHealth;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.douyin.auth", name = "enabled", havingValue = "true")
public class DouyinCredentialService {

    private static final Set<String> AUTH_COOKIE_NAMES = Set.of(
            "sessionid", "sessionid_ss", "sid_guard"
    );

    private final DouyinAuthProperties properties;
    private final DouyinCredentialRepository credentials;
    private final DouyinAuthSessionRepository sessions;

    public DouyinCredentialService(
            DouyinAuthProperties properties,
            DouyinCredentialRepository credentials,
            DouyinAuthSessionRepository sessions
    ) {
        this.properties = properties;
        this.credentials = credentials;
        this.sessions = sessions;
    }

    @Transactional
    public DouyinStoredCredential completeWebLogin(
            UUID loginId,
            Map<String, Object> bundle,
            Map<String, Object> rawResult
    ) {
        DouyinAuthSession session = sessions.findByLoginIdForUpdate(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Douyin login session not found."));
        requireWebSession(session);
        if ("SUCCESS".equals(session.status())) {
            return requireActiveWeb();
        }
        if ("FAILED".equals(session.status()) || "EXPIRED".equals(session.status())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Douyin login session can no longer be completed: " + session.status());
        }

        requireWebBundle(bundle);
        DouyinStoredCredential stored = credentials.saveActive(
                DouyinAuthConstants.WEB_AUTH_TYPE,
                bundle,
                deriveWebExpiration(bundle)
        );
        Map<String, Object> completedResult = new LinkedHashMap<>();
        if (rawResult != null) {
            completedResult.putAll(rawResult);
        }
        completedResult.put("credentialId", stored.credentialId());
        sessions.completeSuccess(loginId, completedResult);
        return stored;
    }

    public DouyinStoredCredential replaceActiveWeb(Long expectedCredentialId, Map<String, Object> bundle) {
        requireWebBundle(bundle);
        return credentials.saveActiveIfCurrent(
                DouyinAuthConstants.WEB_AUTH_TYPE,
                expectedCredentialId,
                bundle,
                deriveWebExpiration(bundle)
        ).orElseThrow(() -> new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "Active Douyin Web credential changed while validation was running; retry with the current state."
        ));
    }

    public DouyinStoredCredential requireActiveWeb() {
        return requireActive(DouyinAuthConstants.WEB_AUTH_TYPE);
    }

    public DouyinStoredCredential requireActive(String authType) {
        return credentials.findActive(authType)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "No active Douyin credential for " + authType + "."));
    }

    public Optional<DouyinCredentialFullView> activeView(String authType) {
        return credentials.findActive(authType).map(DouyinCredentialService::viewOf);
    }

    public DouyinCredentialFullView current(String authType) {
        return viewOf(requireActive(authType));
    }

    public void revoke(String authType) {
        credentials.revokeActive(authType);
    }

    public void markWebInvalid(Long credentialId) {
        credentials.markStatus(credentialId, "INVALID");
    }

    public DouyinAuthStatusView status(WorkerHealth workerHealth) {
        String workerStatus = workerHealth == null || workerHealth.status() == null
                ? "DOWN"
                : workerHealth.status();
        return new DouyinAuthStatusView(
                properties.enabled(),
                properties.oauthMode(),
                "UP".equalsIgnoreCase(workerStatus),
                workerStatus,
                properties.pollIntervalMs(),
                activeView(DouyinAuthConstants.OAUTH_AUTH_TYPE).orElse(null),
                activeView(DouyinAuthConstants.WEB_AUTH_TYPE).orElse(null),
                workerHealth == null ? Map.of() : workerHealth.rawResult()
        );
    }

    public static DouyinCredentialFullView viewOf(DouyinStoredCredential credential) {
        return new DouyinCredentialFullView(
                credential.credentialId(),
                credential.authType(),
                credential.status(),
                credential.expiresAt(),
                credential.createdAt(),
                credential.updatedAt(),
                credential.payload()
        );
    }

    private void requireWebSession(DouyinAuthSession session) {
        if (!"WEB_QR".equals(session.flowType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Douyin login session is not a Web QR flow.");
        }
    }

    private void requireWebBundle(Map<String, Object> bundle) {
        if (bundle == null || bundle.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Douyin Web credential bundle is empty.");
        }
        Object authType = bundle.get("authType");
        if (authType != null && !DouyinAuthConstants.WEB_AUTH_TYPE.equals(String.valueOf(authType))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Douyin Worker returned an unexpected auth type: " + authType);
        }
    }

    private OffsetDateTime deriveWebExpiration(Map<String, Object> bundle) {
        Object cookieValue = bundle.get("cookies");
        if (!(cookieValue instanceof List<?> cookies)) {
            return null;
        }
        return cookies.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(cookie -> AUTH_COOKIE_NAMES.contains(String.valueOf(cookie.get("name")).toLowerCase()))
                .map(cookie -> cookie.get("expires"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToLong(Number::longValue)
                .filter(epochSeconds -> epochSeconds > 0)
                .mapToObj(epochSeconds -> OffsetDateTime.ofInstant(
                        Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC
                ))
                .min(OffsetDateTime::compareTo)
                .orElse(null);
    }
}
