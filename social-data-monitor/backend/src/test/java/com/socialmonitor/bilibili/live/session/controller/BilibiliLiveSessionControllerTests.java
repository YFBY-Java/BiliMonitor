package com.socialmonitor.bilibili.live.session.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionEventPageView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionEventView;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionUserView;
import com.socialmonitor.bilibili.live.session.query.BilibiliLiveSessionQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveSessionControllerTests {

    @Mock
    private BilibiliLiveSessionQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BilibiliLiveSessionController(queryService)).build();
    }

    @Test
    void exposesRecentSessionsThroughTheStableApiEnvelope() throws Exception {
        when(queryService.sessions(7L, 20)).thenReturn(List.of(summary()));

        mockMvc.perform(get("/api/bilibili/live-monitor/rooms/{monitorId}/sessions", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(42L))
                .andExpect(jsonPath("$.data[0].giftSenderCount").value(2L))
                .andExpect(jsonPath("$.data[0].coverageStatus").value("RECEIVED_WHILE_ONLINE"))
                .andExpect(jsonPath("$.data[0].unresolvedPaidEventCount").value(1L))
                .andExpect(jsonPath("$.data[0].paidAmountMilliYuan").value(12345L));

        verify(queryService).sessions(7L, 20);
    }

    @Test
    void exposesSessionDetailAndDefaultTopUsersLimit() throws Exception {
        when(queryService.session(42L)).thenReturn(summary());
        when(queryService.users(42L, 100)).thenReturn(List.of(user()));

        mockMvc.perform(get("/api/bilibili/live-monitor/sessions/{sessionId}", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monitorId").value(7L));
        mockMvc.perform(get("/api/bilibili/live-monitor/sessions/{sessionId}/users", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].actorKey").value("uid:99"))
                .andExpect(jsonPath("$.data[0].identityQuality").value("VERIFIED_UID"))
                .andExpect(jsonPath("$.data[0].displayName").value("viewer"));

        verify(queryService).session(42L);
        verify(queryService).users(42L, 100);
    }

    @Test
    void exposesFilteredSessionEventsWithStablePagingDefaults() throws Exception {
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-16T12:01:00+08:00");
        BilibiliLiveSessionEventView event = new BilibiliLiveSessionEventView(
                9L, 42L, "DANMAKU", "DANMU_MSG", 99L, "viewer", "舰长",
                "今晚好热闹", null, null, null, false, 0L, null,
                occurredAt, occurredAt.plusSeconds(1));
        when(queryService.events(42L, "DANMAKU", "热闹", 99L, false, 1, 50))
                .thenReturn(new BilibiliLiveSessionEventPageView(List.of(event), 1L, 1, 50));

        mockMvc.perform(get("/api/bilibili/live-monitor/sessions/{sessionId}/events", 42L)
                        .param("kind", "DANMAKU")
                        .param("keyword", "热闹")
                        .param("userUid", "99")
                        .param("paid", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1L))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.items[0].messageText").value("今晚好热闹"));

        verify(queryService).events(42L, "DANMAKU", "热闹", 99L, false, 1, 50);
    }

    private BilibiliLiveSessionSummaryView summary() {
        OffsetDateTime startedAt = OffsetDateTime.parse("2026-08-16T12:00:00+08:00");
        return new BilibiliLiveSessionSummaryView(
                42L, 7L, 1001L, 2002L, "CLOSED", startedAt, startedAt.plusHours(1),
                "WEBSOCKET", "WEBSOCKET", "RECEIVED_WHILE_ONLINE", 2L,
                startedAt.plusSeconds(30), startedAt.plusMinutes(59).plusSeconds(30),
                3L, 2L, 4L, 1L, 2L, 2L, 3L, 1L, 1L, 1L, 2L, 12_345L,
                startedAt.plusMinutes(1), startedAt.plusMinutes(59));
    }

    private BilibiliLiveSessionUserView user() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-08-16T12:01:00+08:00");
        return new BilibiliLiveSessionUserView(
                "uid:99", "VERIFIED_UID", 99L, "viewer", 3L, 2L, 4L, 1L, 1L, 12_345L,
                timestamp, timestamp.plusMinutes(10));
    }
}
