package com.socialmonitor.douyin.auth.service;

import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import com.socialmonitor.douyin.auth.domain.DouyinAuthSession;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.dto.DouyinQrStartView;
import com.socialmonitor.douyin.auth.dto.DouyinQrStatusView;
import com.socialmonitor.douyin.auth.dto.DouyinValidationView;
import com.socialmonitor.douyin.auth.repository.DouyinAuthSessionRepository;
import com.socialmonitor.douyin.worker.client.DouyinWorkerClient;
import com.socialmonitor.douyin.worker.dto.WorkerConsume;
import com.socialmonitor.douyin.worker.dto.WorkerHealth;
import com.socialmonitor.douyin.worker.dto.WorkerQrImage;
import com.socialmonitor.douyin.worker.dto.WorkerSessionStart;
import com.socialmonitor.douyin.worker.dto.WorkerStatus;
import com.socialmonitor.douyin.worker.dto.WorkerValidation;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.douyin.auth", name = "enabled", havingValue = "true")
public class DouyinWebAuthService {

    private static final String WEB_FLOW = "WEB_QR";
    private static final Set<String> TERMINAL_STATES = Set.of("SUCCESS", "EXPIRED", "FAILED");

    private final DouyinAuthProperties properties;
    private final DouyinWorkerClient worker;
    private final DouyinAuthSessionRepository sessions;
    private final DouyinCredentialService credentials;
    private final Clock clock;

    @Autowired
    public DouyinWebAuthService(
            DouyinAuthProperties properties,
            DouyinWorkerClient worker,
            DouyinAuthSessionRepository sessions,
            DouyinCredentialService credentials
    ) {
        this(properties, worker, sessions, credentials, Clock.systemUTC());
    }

    DouyinWebAuthService(
            DouyinAuthProperties properties,
            DouyinWorkerClient worker,
            DouyinAuthSessionRepository sessions,
            DouyinCredentialService credentials,
            Clock clock
    ) {
        this.properties = properties;
        this.worker = worker;
        this.sessions = sessions;
        this.credentials = credentials;
        this.clock = clock;
    }

    public DouyinQrStartView start() {
        OffsetDateTime now = now();
        OffsetDateTime expiresAt = now.plusSeconds(properties.qrExpireSeconds());
        UUID loginId = UUID.randomUUID();
        DouyinAuthSession session = new DouyinAuthSession(
                loginId,
                WEB_FLOW,
                "live",
                null,
                null,
                "STARTING",
                expiresAt,
                null,
                null,
                null,
                Map.of("requestedAt", now.toString()),
                now,
                now
        );
        sessions.create(session);

        WorkerSessionStart started;
        try {
            started = worker.start(properties.qrExpireSeconds());
        } catch (BusinessException exception) {
            Map<String, Object> failure = failureResult("WORKER_UNAVAILABLE", exception);
            sessions.completeFailure(loginId, "WORKER_UNAVAILABLE", exception.getMessage(), failure);
            throw exception;
        }

        try {
            sessions.attachWorkerSession(loginId, started.workerSessionId(), started.rawResult());
        } catch (RuntimeException exception) {
            safeDelete(started.workerSessionId());
            throw exception;
        }
        return new DouyinQrStartView(
                loginId,
                normalizeStatus(started.status()),
                "/api/douyin/auth/web/qr/" + loginId + "/image",
                properties.qrExpireSeconds(),
                properties.pollIntervalMs(),
                expiresAt,
                started.rawResult()
        );
    }

    public WorkerQrImage qr(UUID loginId) {
        DouyinAuthSession session = requireWebSession(loginId);
        if (isExpired(session)) {
            Map<String, Object> raw = expirationResult(session);
            sessions.updateStatus(loginId, "EXPIRED", raw);
            safeDelete(session.workerSessionId());
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Douyin QR code has expired.");
        }
        return worker.qr(requireWorkerSessionId(session));
    }

    public DouyinQrStatusView poll(UUID loginId) {
        DouyinAuthSession session = requireWebSession(loginId);
        if (isExpired(session) && !"SUCCESS".equals(session.status())) {
            Map<String, Object> raw = expirationResult(session);
            sessions.updateStatus(loginId, "EXPIRED", raw);
            safeDelete(session.workerSessionId());
            return statusView(session, "EXPIRED", "Douyin QR code has expired.", raw, null);
        }
        if (TERMINAL_STATES.contains(session.status())) {
            DouyinStoredCredential stored = "SUCCESS".equals(session.status())
                    ? credentials.requireActiveWeb()
                    : null;
            return statusView(
                    session,
                    session.status(),
                    session.errorMessage(),
                    session.rawResult(),
                    stored
            );
        }

        WorkerStatus status;
        try {
            status = worker.status(requireWorkerSessionId(session));
        } catch (BusinessException exception) {
            DouyinQrStatusView completed = completedByAnotherPoll(loginId);
            if (completed != null) {
                return completed;
            }
            Map<String, Object> failure = failureResult("WORKER_UNAVAILABLE", exception);
            sessions.completeFailure(loginId, "WORKER_UNAVAILABLE", exception.getMessage(), failure);
            throw exception;
        }

        if (!"SUCCESS".equals(status.status())) {
            sessions.updateStatus(loginId, normalizeStatus(status.status()), status.rawResult());
            if ("EXPIRED".equals(status.status()) || "FAILED".equals(status.status())) {
                safeDelete(session.workerSessionId());
            }
            return statusView(
                    session,
                    normalizeStatus(status.status()),
                    status.message(),
                    status.rawResult(),
                    null
            );
        }

        try {
            WorkerConsume consumed = worker.consume(session.workerSessionId());
            Map<String, Object> completionRaw = new LinkedHashMap<>(status.rawResult());
            completionRaw.put("consume", consumed.rawResult());
            DouyinStoredCredential stored = credentials.completeWebLogin(
                    loginId,
                    consumed.bundle(),
                    completionRaw
            );
            safeDelete(session.workerSessionId());
            return statusView(
                    session,
                    "SUCCESS",
                    status.message(),
                    completionRaw,
                    stored
            );
        } catch (RuntimeException exception) {
            DouyinQrStatusView completed = completedByAnotherPoll(loginId);
            if (completed != null) {
                return completed;
            }
            Map<String, Object> failure = failureResult("WEB_SESSION_CAPTURE_FAILED", exception);
            failure.put("workerStatus", status.rawResult());
            sessions.completeFailure(
                    loginId,
                    "WEB_SESSION_CAPTURE_FAILED",
                    exception.getMessage(),
                    failure
            );
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Failed to persist Douyin Web login state: " + exception.getMessage());
        }
    }

    public DouyinValidationView validateCurrent() {
        DouyinStoredCredential current = credentials.requireActiveWeb();
        WorkerValidation validation = worker.validate(current.payload());
        if (!validation.valid()) {
            credentials.markWebInvalid(current.credentialId());
            return new DouyinValidationView(false, validation.message(), null, validation.rawResult());
        }
        if (validation.bundle() == null || validation.bundle().isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Douyin Worker validated the state without returning the complete bundle.");
        }
        DouyinStoredCredential refreshed = credentials.replaceActiveWeb(
                current.credentialId(),
                validation.bundle()
        );
        return new DouyinValidationView(
                true,
                validation.message(),
                DouyinCredentialService.viewOf(refreshed),
                validation.rawResult()
        );
    }

    public WorkerHealth workerHealth() {
        try {
            return worker.health();
        } catch (BusinessException exception) {
            return new WorkerHealth("DOWN", failureResult("WORKER_UNAVAILABLE", exception));
        }
    }

    private DouyinQrStatusView completedByAnotherPoll(UUID loginId) {
        DouyinAuthSession latest = sessions.findByLoginId(loginId).orElse(null);
        if (latest == null || !"SUCCESS".equals(latest.status())) {
            return null;
        }
        return statusView(
                latest,
                "SUCCESS",
                latest.errorMessage(),
                latest.rawResult(),
                credentials.requireActiveWeb()
        );
    }

    private DouyinAuthSession requireWebSession(UUID loginId) {
        DouyinAuthSession session = sessions.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Douyin QR login session not found."));
        if (!WEB_FLOW.equals(session.flowType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Douyin login session is not a Web QR flow.");
        }
        return session;
    }

    private String requireWorkerSessionId(DouyinAuthSession session) {
        if (session.workerSessionId() == null || session.workerSessionId().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Douyin login session has no Worker session id.");
        }
        return session.workerSessionId();
    }

    private DouyinQrStatusView statusView(
            DouyinAuthSession session,
            String status,
            String message,
            Map<String, Object> rawResult,
            DouyinStoredCredential credential
    ) {
        return new DouyinQrStatusView(
                session.loginId(),
                status,
                message == null ? defaultMessage(status) : message,
                "SUCCESS".equals(status) ? 0 : expiresInSeconds(session),
                rawResult,
                credential == null ? null : DouyinCredentialService.viewOf(credential)
        );
    }

    private int expiresInSeconds(DouyinAuthSession session) {
        return (int) Math.max(0, Duration.between(now(), session.expiresAt()).toSeconds());
    }

    private boolean isExpired(DouyinAuthSession session) {
        return !session.expiresAt().isAfter(now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String normalizeStatus(String status) {
        return switch (status == null ? "" : status) {
            case "STARTING", "WAITING", "SCANNED", "VALIDATING", "SUCCESS",
                    "EXPIRED", "USER_ACTION_REQUIRED", "FAILED" -> status;
            default -> "FAILED";
        };
    }

    private String defaultMessage(String status) {
        return switch (status) {
            case "WAITING" -> "Scan the QR code with Douyin.";
            case "SCANNED" -> "Confirm the login on the phone.";
            case "VALIDATING" -> "Douyin is validating the login.";
            case "SUCCESS" -> "Douyin Web login state has been saved.";
            case "EXPIRED" -> "Douyin QR code has expired.";
            case "USER_ACTION_REQUIRED" -> "Complete the verification in the visible browser.";
            default -> "Douyin Web login failed.";
        };
    }

    private Map<String, Object> expirationResult(DouyinAuthSession session) {
        Map<String, Object> result = new LinkedHashMap<>(session.rawResult());
        result.put("expiredAt", now().toString());
        return result;
    }

    private Map<String, Object> failureResult(String code, Exception exception) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("errorCode", code);
        result.put("errorType", exception.getClass().getName());
        result.put("errorMessage", exception.getMessage());
        result.put("failedAt", now().toString());
        return result;
    }

    private void safeDelete(String workerSessionId) {
        if (workerSessionId == null || workerSessionId.isBlank()) {
            return;
        }
        try {
            worker.delete(workerSessionId);
        } catch (RuntimeException ignored) {
            // Worker sessions also have their own TTL cleanup; credential persistence must remain successful.
        }
    }
}
