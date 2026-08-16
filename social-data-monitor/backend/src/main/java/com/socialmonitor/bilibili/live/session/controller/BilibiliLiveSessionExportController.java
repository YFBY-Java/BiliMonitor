package com.socialmonitor.bilibili.live.session.controller;

import com.socialmonitor.bilibili.live.session.export.BilibiliLiveSessionExportCategory;
import com.socialmonitor.bilibili.live.session.export.BilibiliLiveSessionExportService;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bilibili/live-monitor/sessions")
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSessionExportController {

    private final BilibiliLiveSessionExportService exportService;

    public BilibiliLiveSessionExportController(BilibiliLiveSessionExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/{sessionId}/export")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void export(
            @PathVariable Long sessionId,
            @RequestParam String category,
            HttpServletResponse response
    ) throws IOException {
        BilibiliLiveSessionExportCategory exportCategory = BilibiliLiveSessionExportCategory.parse(category);
        BilibiliLiveSessionSummaryView summary = exportService.prepare(sessionId);
        configureHeaders(response, summary, exportCategory);
        exportService.exportPrepared(summary, exportCategory, response.getOutputStream());
        response.flushBuffer();
    }

    private void configureHeaders(
            HttpServletResponse response,
            BilibiliLiveSessionSummaryView summary,
            BilibiliLiveSessionExportCategory category
    ) {
        if (category == BilibiliLiveSessionExportCategory.ALL) {
            response.setContentType("application/zip");
        } else {
            response.setContentType("text/csv");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        }
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"bilibili-live-session-" + summary.id() + "-"
                        + category.wireValue() + "." + category.extension() + "\""
        );
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Export-Schema-Version", BilibiliLiveSessionExportService.SCHEMA_VERSION);
        response.setHeader("X-Capture-Scope", BilibiliLiveSessionExportService.CAPTURE_SCOPE);
        response.setHeader("X-Coverage-Status", summary.coverageStatus());
        response.setHeader("X-Transport-Session-Count", String.valueOf(summary.transportSessionCount()));
        setOptionalHeader(response, "X-Capture-Started-At", summary.captureStartedAt());
        setOptionalHeader(response, "X-Capture-Ended-At", summary.captureEndedAt());
    }

    private void setOptionalHeader(HttpServletResponse response, String name, Object value) {
        if (value != null) {
            response.setHeader(name, value.toString());
        }
    }
}
