package com.socialmonitor.bilibili.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.dto.BilibiliMonitorUserView;
import com.socialmonitor.bilibili.service.BilibiliFollowerMonitorService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BilibiliFollowerMonitorControllerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private BilibiliFollowerMonitorService monitorService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BilibiliFollowerMonitorController(monitorService))
                .build();
    }

    @Test
    void patchSettingsUpdatesCollectionInterval() throws Exception {
        BilibiliMonitorUserView userView = userView(7L, 120);
        when(monitorService.updateInterval(7L, 120)).thenReturn(userView);

        mockMvc.perform(patch("/api/bilibili/follower-monitor/users/{userId}/settings", 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IntervalBody(120))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.intervalSeconds").value(120));

        verify(monitorService).updateInterval(7L, 120);
    }

    @Test
    void putSettingsAlsoUpdatesCollectionInterval() throws Exception {
        BilibiliMonitorUserView userView = userView(8L, 3600);
        when(monitorService.updateInterval(8L, 3600)).thenReturn(userView);

        mockMvc.perform(put("/api/bilibili/follower-monitor/users/{userId}/settings", 8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IntervalBody(3600))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(8))
                .andExpect(jsonPath("$.data.intervalSeconds").value(3600));

        verify(monitorService).updateInterval(8L, 3600);
    }

    private BilibiliMonitorUserView userView(Long userId, Integer intervalSeconds) {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-11T00:00:00+08:00");
        return new BilibiliMonitorUserView(
                userId,
                123456L,
                "测试用户",
                "https://example.com/avatar.jpg",
                "https://space.bilibili.com/123456",
                1000L,
                12L,
                3L,
                0.003,
                now,
                now,
                now.plusSeconds(intervalSeconds),
                "ACTIVE",
                intervalSeconds,
                null,
                null,
                null,
                "x/web-interface/card",
                List.of()
        );
    }

    private record IntervalBody(Integer intervalSeconds) {
    }
}
