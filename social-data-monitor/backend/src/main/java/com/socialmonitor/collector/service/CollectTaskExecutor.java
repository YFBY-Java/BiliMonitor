package com.socialmonitor.collector.service;

import com.socialmonitor.collector.dto.CollectTaskCommand;
import com.socialmonitor.collector.dto.CollectTaskExecutionView;
import com.socialmonitor.collector.enums.TaskStatus;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.platform.dto.FetchResult;
import com.socialmonitor.platform.port.SocialPlatformAdapter;
import com.socialmonitor.platform.service.PlatformAdapterRegistry;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CollectTaskExecutor {

    private final PlatformAdapterRegistry adapterRegistry;
    private final RateLimitService rateLimitService;
    private final ApiCallLogService apiCallLogService;
    private final RawPayloadService rawPayloadService;
    private final TaskCheckpointService checkpointService;

    public CollectTaskExecutor(
            PlatformAdapterRegistry adapterRegistry,
            RateLimitService rateLimitService,
            ApiCallLogService apiCallLogService,
            RawPayloadService rawPayloadService,
            TaskCheckpointService checkpointService
    ) {
        this.adapterRegistry = adapterRegistry;
        this.rateLimitService = rateLimitService;
        this.apiCallLogService = apiCallLogService;
        this.rawPayloadService = rawPayloadService;
        this.checkpointService = checkpointService;
    }

    public CollectTaskExecutionView execute(CollectTaskCommand command) {
        validateCommand(command);
        SocialPlatformAdapter adapter = adapterRegistry.getRequired(command.platformCode());
        rateLimitService.acquire(command.platformCode(), command.dataType());

        FetchResult<Map<String, Object>> result = switch (command.dataType()) {
            case "account" -> adapter.fetchAccount(command.externalId());
            case "contents" -> adapter.fetchContents(command.externalId(), command.cursor());
            case "content-detail" -> adapter.fetchContentDetail(command.externalId());
            case "comments" -> adapter.fetchComments(command.externalId(), command.cursor());
            case "danmaku" -> adapter.fetchDanmaku(command.externalId(), command.cursor());
            case "interactions" -> adapter.fetchInteractions(command.externalId());
            case "followers" -> adapter.fetchFollowers(command.externalId(), command.cursor());
            case "trends" -> adapter.fetchTrends(command.cursor());
            default -> FetchResult.unsupported("Unsupported task data type: " + command.dataType());
        };

        apiCallLogService.record(command, result);
        rawPayloadService.storeIfPresent(command, result);
        checkpointService.saveCursor(command, result.nextCursor());

        return new CollectTaskExecutionView(result.success() ? TaskStatus.SUCCESS : TaskStatus.MANUAL_REVIEW,
                command.platformCode(), command.dataType(), result);
    }

    private void validateCommand(CollectTaskCommand command) {
        if (!"trends".equals(command.dataType()) && isBlank(command.externalId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "externalId is required for data type: " + command.dataType());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
