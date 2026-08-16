package com.socialmonitor.bilibili.live.session.query;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionUserView;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSessionQueryService {

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

    private int normalizeLimit(int limit, int defaultValue, int maximum) {
        if (limit <= 0) {
            return defaultValue;
        }
        return Math.min(limit, maximum);
    }
}
