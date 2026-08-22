package com.socialmonitor.bilibili.live.session.query;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionUserView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionEventPageView;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSessionQueryService {

    private static final Set<String> EVENT_KINDS = Set.of("DANMAKU", "GIFT", "SUPER_CHAT", "GUARD_BUY");

    private final BilibiliLiveSessionQueryRepository repository;

    public BilibiliLiveSessionQueryService(BilibiliLiveSessionQueryRepository repository) {
        this.repository = repository;
    }

    public List<BilibiliLiveSessionSummaryView> sessions(Long monitorId, int limit) {
        return repository.findRecentSessions(monitorId, normalizeLimit(limit, 20, 100));
    }

    public BilibiliLiveSessionSummaryView session(Long sessionId) {
        return repository.findSession(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "直播场次不存在：" + sessionId));
    }

    public List<BilibiliLiveSessionUserView> users(Long sessionId, int limit) {
        session(sessionId);
        return repository.findUsers(sessionId, normalizeLimit(limit, 100, 500));
    }

    public BilibiliLiveSessionEventPageView events(
            Long sessionId,
            String kind,
            String keyword,
            Long userUid,
            Boolean paid,
            int page,
            int size
    ) {
        session(sessionId);
        String normalizedKind = normalizeKind(kind);
        String normalizedKeyword = normalizeKeyword(keyword);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = normalizeLimit(size, 50, 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        return new BilibiliLiveSessionEventPageView(
                repository.findEvents(sessionId, normalizedKind, normalizedKeyword, userUid, paid, offset, normalizedSize),
                repository.countEvents(sessionId, normalizedKind, normalizedKeyword, userUid, paid),
                normalizedPage,
                normalizedSize
        );
    }

    private String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        String normalized = kind.trim().toUpperCase(Locale.ROOT);
        if (!EVENT_KINDS.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的事件类型：" + kind);
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.substring(0, Math.min(normalized.length(), 100));
    }

    private int normalizeLimit(int limit, int defaultValue, int maximum) {
        if (limit <= 0) {
            return defaultValue;
        }
        return Math.min(limit, maximum);
    }
}
