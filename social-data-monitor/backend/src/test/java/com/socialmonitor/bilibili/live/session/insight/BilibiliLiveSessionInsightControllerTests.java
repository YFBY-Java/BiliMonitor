package com.socialmonitor.bilibili.live.session.insight;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionInsightView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveSessionInsightControllerTests {

    @Mock
    private BilibiliLiveSessionInsightService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BilibiliLiveSessionInsightController(service)).build();
    }

    @Test
    void exposesSingleSessionInsightsWithRequestedBucket() throws Exception {
        BilibiliLiveSessionInsightView insight = new BilibiliLiveSessionInsightView(
                42L, 300,
                new BilibiliLiveSessionInsightView.Kpis(2.5, 0.2, 5_000L, 10_000L, 0.8),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                new BilibiliLiveSessionInsightView.DanmakuDepth(
                        20L, 100L, 5.0, 0.4, 0.25, 0.1, List.of(), List.of()),
                new BilibiliLiveSessionInsightView.PaymentDepth(
                        10L, 0.4, 0.7, 0.3, 2_500L, 120L, 0.6, List.of()),
                new BilibiliLiveSessionInsightView.Quality(
                        "RECEIVED_WHILE_ONLINE", 1_200L, 0.9, 100L, 2L, 500L,
                        "仅覆盖在线采集区间"));
        when(service.insight(42L, 300)).thenReturn(insight);

        mockMvc.perform(get("/api/bilibili/live-monitor/sessions/{sessionId}/insights", 42L)
                        .param("bucketSeconds", "300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(42L))
                .andExpect(jsonPath("$.data.bucketSeconds").value(300))
                .andExpect(jsonPath("$.data.kpis.danmakuPerMinute").value(2.5))
                .andExpect(jsonPath("$.data.danmakuDepth.repeatInteractionRate").value(0.4))
                .andExpect(jsonPath("$.data.paymentDepth.returningPayerRate").value(0.3));

        verify(service).insight(42L, 300);
    }
}
