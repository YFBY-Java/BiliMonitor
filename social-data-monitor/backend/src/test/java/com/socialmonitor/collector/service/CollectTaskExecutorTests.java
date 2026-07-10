package com.socialmonitor.collector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialmonitor.collector.dto.CollectTaskCommand;
import com.socialmonitor.collector.dto.CollectTaskExecutionView;
import com.socialmonitor.collector.enums.TaskStatus;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.platform.dto.FetchResult;
import com.socialmonitor.platform.port.SocialPlatformAdapter;
import com.socialmonitor.platform.service.PlatformAdapterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollectTaskExecutorTests {

    private final PlatformAdapterRegistry adapterRegistry = mock(PlatformAdapterRegistry.class);
    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final ApiCallLogService apiCallLogService = mock(ApiCallLogService.class);
    private final RawPayloadService rawPayloadService = mock(RawPayloadService.class);
    private final TaskCheckpointService checkpointService = mock(TaskCheckpointService.class);
    private final SocialPlatformAdapter adapter = mock(SocialPlatformAdapter.class);

    private final CollectTaskExecutor executor = new CollectTaskExecutor(
            adapterRegistry,
            rateLimitService,
            apiCallLogService,
            rawPayloadService,
            checkpointService
    );

    @Test
    void rejectsMissingExternalIdForEntityTask() {
        CollectTaskCommand command = new CollectTaskCommand("bilibili", "account", null, null);

        assertThatThrownBy(() -> executor.execute(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void executesAccountTaskThroughAdapter() {
        CollectTaskCommand command = new CollectTaskCommand("bilibili", "account", "123", null);
        FetchResult<Map<String, Object>> result = FetchResult.success(Map.of("id", "123"), "{\"id\":\"123\"}");
        when(adapterRegistry.getRequired("bilibili")).thenReturn(adapter);
        when(adapter.fetchAccount("123")).thenReturn(result);

        CollectTaskExecutionView view = executor.execute(command);

        assertThat(view.status()).isEqualTo(TaskStatus.SUCCESS);
        verify(rateLimitService).acquire("bilibili", "account");
        verify(apiCallLogService).record(command, result);
        verify(rawPayloadService).storeIfPresent(command, result);
        verify(checkpointService).saveCursor(command, null);
    }
}
