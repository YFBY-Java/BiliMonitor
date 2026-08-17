package com.socialmonitor.bilibili.live.session.export;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

final class BilibiliLiveSessionXlsxWriter {

    private final BilibiliLiveSessionExportRepository repository;
    private final SXSSFWorkbook workbook;
    private final CellStyle headerStyle;

    BilibiliLiveSessionXlsxWriter(BilibiliLiveSessionExportRepository repository) {
        this.repository = repository;
        this.workbook = new SXSSFWorkbook(100);
        this.workbook.setCompressTempFiles(true);
        this.headerStyle = createHeaderStyle();
    }

    void write(BilibiliLiveSessionSummaryView summary, OutputStream outputStream) throws IOException {
        try {
            writeSummary(summary);
            writeDanmaku(summary.id());
            writeGifts(summary.id());
            writeUsers(summary.id());
            workbook.write(outputStream);
            outputStream.flush();
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private void writeSummary(BilibiliLiveSessionSummaryView summary) {
        SheetRows rows = sheet("场次摘要",
                "id", "monitor_id", "uid", "room_id", "state", "started_at", "ended_at",
                "start_source", "end_source", "coverage_status", "transport_session_count",
                "capture_started_at", "capture_ended_at", "danmaku_count", "gift_event_count", "gift_count",
                "free_gift_count", "gift_sender_count", "paid_user_count", "interacting_user_count",
                "unresolved_interacting_event_count", "unresolved_gift_event_count",
                "unresolved_paid_event_count", "paid_event_count", "paid_amount_milli_yuan",
                "first_event_at", "last_event_at");
        rows.write(
                identifier(summary.id()), identifier(summary.monitorId()), identifier(summary.uid()),
                identifier(summary.roomId()), summary.state(), summary.startedAt(), summary.endedAt(),
                summary.startSource(), summary.endSource(), summary.coverageStatus(),
                summary.transportSessionCount(), summary.captureStartedAt(), summary.captureEndedAt(),
                summary.danmakuCount(), summary.giftEventCount(), summary.giftCount(), summary.freeGiftCount(),
                summary.giftSenderCount(), summary.paidUserCount(), summary.interactingUserCount(),
                summary.unresolvedInteractingEventCount(), summary.unresolvedGiftEventCount(),
                summary.unresolvedPaidEventCount(), summary.paidEventCount(),
                summary.paidAmountMilliYuan(), summary.firstEventAt(), summary.lastEventAt());
        rows.finish();
    }

    private void writeDanmaku(Long sessionId) throws IOException {
        SheetRows rows = sheet("弹幕",
                "occurred_at", "received_at", "sender_uid", "sender_name", "medal_name", "message_text",
                "command", "protocol_version", "source_event_id");
        repository.streamDanmaku(sessionId, row -> rows.write(
                row.occurredAt(), row.receivedAt(), identifier(row.senderUid()), row.senderName(), row.medalName(),
                row.messageText(), row.command(), row.protocolVersion(), row.sourceEventId()));
        rows.finish();
    }

    private void writeGifts(Long sessionId) throws IOException {
        SheetRows rows = sheet("礼物",
                "occurred_at", "received_at", "event_kind", "sender_uid", "sender_name", "medal_name",
                "message_text", "gift_id", "gift_name", "gift_count", "coin_type",
                "unit_price_milli_yuan", "paid_amount_milli_yuan", "paid", "guard_level", "amount_source",
                "command", "protocol_version", "source_event_id", "event_key", "transport_session_id");
        repository.streamGifts(sessionId, row -> rows.write(
                row.occurredAt(), row.receivedAt(), row.eventKind(), identifier(row.senderUid()), row.senderName(),
                row.medalName(), row.messageText(), identifier(row.giftId()), row.giftName(), row.giftCount(),
                row.coinType(), row.unitPriceMilliYuan(), row.paidAmountMilliYuan(), row.paid(), row.guardLevel(),
                row.amountSource(), row.command(), row.protocolVersion(), row.sourceEventId(), row.eventKey(),
                identifier(row.transportSessionId())));
        rows.finish();
    }

    private void writeUsers(Long sessionId) throws IOException {
        SheetRows rows = sheet("用户",
                "actor_key", "identity_quality", "user_uid", "display_name", "danmaku_count",
                "gift_event_count", "gift_count", "free_gift_count", "paid_event_count",
                "paid_amount_milli_yuan", "first_seen_at", "last_seen_at");
        repository.streamUsers(sessionId, row -> rows.write(
                row.actorKey(), row.identityQuality(), identifier(row.userUid()), row.displayName(),
                row.danmakuCount(), row.giftEventCount(), row.giftCount(), row.freeGiftCount(),
                row.paidEventCount(), row.paidAmountMilliYuan(), row.firstSeenAt(), row.lastSeenAt()));
        rows.finish();
    }

    private SheetRows sheet(String name, String... headers) {
        Sheet sheet = workbook.createSheet(name);
        sheet.createFreezePane(0, 1);
        for (int index = 0; index < headers.length; index++) {
            sheet.setColumnWidth(index, columnWidth(headers[index]) * 256);
        }
        return new SheetRows(sheet, headers);
    }

    private CellStyle createHeaderStyle() {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private int columnWidth(String header) {
        if (header.endsWith("_at")) {
            return 30;
        }
        if (header.endsWith("_id") || header.endsWith("_uid") || "event_key".equals(header)) {
            return 24;
        }
        if ("message_text".equals(header)) {
            return 56;
        }
        if (header.contains("name")) {
            return 24;
        }
        return 18;
    }

    private String identifier(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void setValue(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Boolean booleanValue) {
            cell.setCellValue(booleanValue);
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof OffsetDateTime timestamp) {
            cell.setCellValue(timestamp.toString());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private final class SheetRows {

        private final Sheet sheet;
        private final int columnCount;
        private int nextRow = 1;

        private SheetRows(Sheet sheet, String[] headers) {
            this.sheet = sheet;
            this.columnCount = headers.length;
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(headers[index]);
                cell.setCellStyle(headerStyle);
            }
        }

        private void write(Object... values) {
            Row row = sheet.createRow(nextRow++);
            for (int index = 0; index < values.length; index++) {
                setValue(row.createCell(index), values[index]);
            }
        }

        private void finish() {
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, nextRow - 1), 0, columnCount - 1));
        }
    }
}
