package com.socialmonitor.bilibili.live.danmaku.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveDanmakuRepositoryRecoveryTests {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void marksOnlyNonTerminalSessionsAsInterruptedAfterProcessRestart() throws Exception {
        BilibiliLiveDanmakuRepository repository = new BilibiliLiveDanmakuRepository(jdbcTemplate);
        Method recovery = BilibiliLiveDanmakuRepository.class.getMethod("markOrphanedSessionsInterrupted");

        recovery.invoke(repository);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), anyMap());
        assertThat(sql.getValue())
                .contains("status = 'ERROR'")
                .contains("last_error_type = 'PROCESS_RESTART'")
                .contains("WHERE status IN ('CONNECTING', 'AUTHENTICATING', 'CONNECTED')")
                .doesNotContain("WHERE status IN ('STOPPED', 'CLOSED', 'ERROR')");
    }

    @Test
    void interruptedSessionEndsAtLatestObservedTransportActivityRatherThanRestartTime() {
        BilibiliLiveDanmakuRepository repository = new BilibiliLiveDanmakuRepository(jdbcTemplate);

        repository.markOrphanedSessionsInterrupted();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), anyMap());
        String compactSql = sql.getValue().replaceAll("\\s+", "");
        assertThat(compactSql)
                .contains("ended_at=COALESCE(ended_at,GREATEST("
                        + "COALESCE(last_heartbeat_at,connected_at,started_at),"
                        + "COALESCE(connected_at,started_at)))")
                .doesNotContain("ended_at=COALESCE(ended_at,now())")
                .contains("status='ERROR'")
                .contains("last_error_type='PROCESS_RESTART'");
    }

    @Test
    void runtimeSessionErrorEndsAtDetectionTime() {
        BilibiliLiveDanmakuRepository repository = new BilibiliLiveDanmakuRepository(jdbcTemplate);

        repository.markSessionError(71L, "NETWORK_ERROR", "connection lost");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(MapSqlParameterSource.class));
        String compactSql = sql.getValue().replaceAll("\\s+", "");
        assertThat(compactSql)
                .contains("ended_at=COALESCE(ended_at,now())")
                .doesNotContain("GREATEST(");
    }

    @Test
    void authenticationSuccessRecordsConnectedAt() {
        BilibiliLiveDanmakuRepository repository = new BilibiliLiveDanmakuRepository(jdbcTemplate);

        repository.markSessionConnected(71L, OffsetDateTime.parse("2026-08-16T12:00:00+08:00"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), anyMap());
        assertThat(sql.getValue())
                .contains("status = 'CONNECTED'")
                .contains("connected_at = COALESCE(connected_at, :connectedAt)");
    }

    @Test
    void autoDesiredRoomsUnionEnabledBindingsWithCurrentlyLiveDirectMonitors() {
        BilibiliLiveDanmakuRepository repository = new BilibiliLiveDanmakuRepository(jdbcTemplate);

        repository.findAutoStartRoomMonitorIds();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), anyMap(), org.mockito.ArgumentMatchers.eq(Long.class));
        assertThat(sql.getValue())
                .contains("binding.danmu_enabled = true")
                .contains("UNION")
                .contains("room.live_status = 1")
                .contains("room.monitor_status = 'ACTIVE'")
                .contains("CASE WHEN room.live_status = 1 THEN 0 ELSE 1 END AS live_priority")
                .contains("ORDER BY desired.live_priority ASC, desired.monitor_id ASC");
    }
}
