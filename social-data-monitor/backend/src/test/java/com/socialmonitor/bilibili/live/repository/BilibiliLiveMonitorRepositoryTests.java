package com.socialmonitor.bilibili.live.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.live.client.BilibiliLiveApiClient;
import com.socialmonitor.bilibili.live.domain.BilibiliFetchedLiveRoomSnapshot;
import com.socialmonitor.bilibili.live.domain.BilibiliLiveRoomMonitor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveMonitorRepositoryTests {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private ObjectMapper objectMapper;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void upsertRejectsConflictUpdateOlderThanPersistedMonitorTime() {
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)
        )).thenReturn(List.of());
        BilibiliLiveMonitorRepository repository = new BilibiliLiveMonitorRepository(
                jdbcTemplate, objectMapper
        );
        OffsetDateTime fetchedAt = OffsetDateTime.of(
                2026, 8, 16, 20, 0, 0, 0, ZoneOffset.ofHours(8)
        );
        BilibiliFetchedLiveRoomSnapshot snapshot = new BilibiliFetchedLiveRoomSnapshot(
                22L, 33L, null, "anchor", null, "title", null, null,
                null, null, null, null, 1, fetchedAt.minusHours(1), 100L, 200L,
                fetchedAt, BilibiliLiveApiClient.STATUS_BY_UIDS_ENDPOINT, "{}"
        );

        var result = repository.upsertMonitorFromSnapshot(
                snapshot, 300, fetchedAt.plusSeconds(300)
        );

        assertThat(result).isEmpty();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class)
        );
        assertThat(sql.getValue())
                .contains("WHERE EXCLUDED.last_success_at >= GREATEST")
                .contains("bilibili_live_room_monitor.last_snapshot_at")
                .contains("bilibili_live_room_monitor.last_success_at")
                .doesNotContain("SELECT MAX(session.end_signal_at)");
    }

    @Test
    void initializationUsesTransactionScopedAdvisoryLockForAbsentUidRows() {
        BilibiliLiveMonitorRepository repository = new BilibiliLiveMonitorRepository(
                jdbcTemplate, objectMapper
        );

        repository.lockInitializationUid(22L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), eq(java.util.Map.of("uid", 22L)));
        assertThat(sql.getValue())
                .contains("pg_advisory_xact_lock")
                .contains("-CAST(:uid AS bigint)");
    }
}
