package com.socialmonitor.bilibili.live.session.query;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveSessionQueryServiceTests {

    @Mock
    private BilibiliLiveSessionQueryRepository repository;

    private BilibiliLiveSessionQueryService service;

    @BeforeEach
    void setUp() {
        service = new BilibiliLiveSessionQueryService(repository);
    }

    @Test
    void capsRecentSessionAndUserLimits() {
        when(repository.findRecentSessions(7L, 100)).thenReturn(List.of());
        service.sessions(7L, 10_000);
        verify(repository).findRecentSessions(7L, 100);

        BilibiliLiveSessionSummaryView summary = summary(42L);
        when(repository.findSession(42L)).thenReturn(Optional.of(summary));
        when(repository.findUsers(42L, 500)).thenReturn(List.of());
        service.users(42L, 10_000);
        verify(repository).findUsers(42L, 500);
    }

    @Test
    void reportsMissingSessionBeforeQueryingItsUsers() {
        when(repository.findSession(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.users(404L, 100))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void normalizesEventFiltersAndCapsPageSize() {
        when(repository.findSession(42L)).thenReturn(Optional.of(summary(42L)));
        when(repository.findEvents(42L, "GIFT", "小电视", 99L, true, 0, 100))
                .thenReturn(List.of());
        when(repository.countEvents(42L, "GIFT", "小电视", 99L, true)).thenReturn(0L);

        service.events(42L, " gift ", "  小电视  ", 99L, true, -1, 999);

        verify(repository).findEvents(42L, "GIFT", "小电视", 99L, true, 0, 100);
        verify(repository).countEvents(42L, "GIFT", "小电视", 99L, true);
    }

    private BilibiliLiveSessionSummaryView summary(Long id) {
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-08-16T12:00:00+08:00");
        return new BilibiliLiveSessionSummaryView(
                id, 7L, 1001L, 2002L, "CLOSED", startedAt, startedAt.plusHours(1),
                "WEBSOCKET", "WEBSOCKET", "RECEIVED_WHILE_ONLINE", 2L,
                startedAt.plusSeconds(30), startedAt.plusMinutes(59).plusSeconds(30),
                3L, 2L, 4L, 1L, 2L, 2L, 3L, 1L, 1L, 1L, 2L, 12_345L,
                startedAt.plusMinutes(1), startedAt.plusMinutes(59)
        );
    }
}
