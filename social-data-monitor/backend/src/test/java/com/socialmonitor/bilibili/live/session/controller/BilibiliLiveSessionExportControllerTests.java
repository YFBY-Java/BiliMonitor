package com.socialmonitor.bilibili.live.session.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialmonitor.bilibili.live.session.export.BilibiliLiveSessionExportCategory;
import com.socialmonitor.bilibili.live.session.export.BilibiliLiveSessionExportService;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.common.exception.GlobalExceptionHandler;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class BilibiliLiveSessionExportControllerTests {

    @Mock
    private BilibiliLiveSessionExportService exportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BilibiliLiveSessionExportController(exportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void streamsCsvWithDownloadAndSecurityHeaders() throws Exception {
        org.mockito.Mockito.when(exportService.prepare(42L)).thenReturn(summary());
        doAnswer(invocation -> {
            OutputStream output = invocation.getArgument(2);
            output.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'x'});
            return null;
        }).when(exportService).exportPrepared(
                eq(summary()), eq(BilibiliLiveSessionExportCategory.DANMAKU), any());

        mockMvc.perform(get("/api/bilibili/live-monitor/sessions/{sessionId}/export", 42L)
                        .queryParam("category", "danmaku"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"bilibili-live-session-42-danmaku.csv\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Export-Schema-Version", "2"))
                .andExpect(header().string("X-Capture-Scope",
                        "received_while_websocket_online_since_deployment"))
                .andExpect(header().string("X-Coverage-Status", "RECEIVED_WHILE_ONLINE"))
                .andExpect(header().string("X-Transport-Session-Count", "2"));

        verify(exportService).exportPrepared(eq(summary()), eq(BilibiliLiveSessionExportCategory.DANMAKU), any());
    }

    @Test
    void streamsUnifiedExportAsZip() throws Exception {
        org.mockito.Mockito.when(exportService.prepare(42L)).thenReturn(summary());
        mockMvc.perform(get("/api/bilibili/live-monitor/sessions/{sessionId}/export", 42L)
                        .queryParam("category", "all"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"bilibili-live-session-42-all.zip\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Export-Schema-Version", "2"))
                .andExpect(header().string("X-Coverage-Status", "RECEIVED_WHILE_ONLINE"));

        InOrder calls = inOrder(exportService);
        calls.verify(exportService).prepare(42L);
        calls.verify(exportService).exportPrepared(
                eq(summary()), eq(BilibiliLiveSessionExportCategory.ALL), any());
    }

    @Test
    void streamsNativeExcelWorkbookWithTheXlsxMediaType() throws Exception {
        org.mockito.Mockito.when(exportService.prepare(42L)).thenReturn(summary());

        mockMvc.perform(get("/api/bilibili/live-monitor/sessions/{sessionId}/export", 42L)
                        .queryParam("category", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"bilibili-live-session-42.xlsx\""));

        verify(exportService).exportPrepared(eq(summary()), eq(BilibiliLiveSessionExportCategory.XLSX), any());
    }

    @Test
    void rejectsCategoryOutsideTheWhitelistBeforeWritingAResponseBody() throws Exception {
        mockMvc.perform(get("/api/bilibili/live-monitor/sessions/{sessionId}/export", 42L)
                        .queryParam("category", "../all"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(exportService, never()).exportPrepared(any(), any(), any());
    }

    @Test
    void missingSessionKeepsJsonErrorHeadersInsteadOfAttachmentHeaders() throws Exception {
        org.mockito.Mockito.when(exportService.prepare(404L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "missing session"));

        mockMvc.perform(get("/api/bilibili/live-monitor/sessions/{sessionId}/export", 404L)
                        .queryParam("category", "danmaku"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(header().doesNotExist("X-Capture-Scope"))
                .andExpect(header().doesNotExist("X-Coverage-Status"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(exportService).prepare(404L);
        verify(exportService, never()).exportPrepared(any(), any(), any());
    }

    @Test
    void actualHttpExportPathUsesOneReadOnlyRepeatableReadTransaction() throws Exception {
        Method export = BilibiliLiveSessionExportController.class.getMethod(
                "export", Long.class, String.class, HttpServletResponse.class);

        Transactional transactional = export.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
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
}
