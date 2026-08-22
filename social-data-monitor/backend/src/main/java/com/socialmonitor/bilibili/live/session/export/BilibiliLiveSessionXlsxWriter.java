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
    private final CellStyle commentStyle;

    BilibiliLiveSessionXlsxWriter(BilibiliLiveSessionExportRepository repository) {
        this.repository = repository;
        this.workbook = new SXSSFWorkbook(100);
        this.workbook.setCompressTempFiles(true);
        this.headerStyle = createHeaderStyle();
        this.commentStyle = createCommentStyle();
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
        SheetRows rows = sheet("场次摘要", BilibiliLiveSessionExportColumns.SUMMARY);
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
        SheetRows rows = sheet("弹幕", BilibiliLiveSessionExportColumns.DANMAKU);
        repository.streamDanmaku(sessionId, row -> rows.write(
                row.occurredAt(), row.receivedAt(), identifier(row.senderUid()), row.senderName(), row.medalName(),
                row.messageText(), row.command(), row.protocolVersion(), row.sourceEventId()));
        rows.finish();
    }

    private void writeGifts(Long sessionId) throws IOException {
        SheetRows rows = sheet("礼物", BilibiliLiveSessionExportColumns.GIFTS);
        repository.streamGifts(sessionId, row -> rows.write(
                row.occurredAt(), row.receivedAt(), row.eventKind(), identifier(row.senderUid()), row.senderName(),
                row.medalName(), row.messageText(), identifier(row.giftId()), row.giftName(), row.giftCount(),
                row.coinType(), row.unitPriceMilliYuan(), row.paidAmountMilliYuan(), row.paid(), row.guardLevel(),
                row.amountSource(), row.command(), row.protocolVersion(), row.sourceEventId(), row.eventKey(),
                identifier(row.transportSessionId())));
        rows.finish();
    }

    private void writeUsers(Long sessionId) throws IOException {
        SheetRows rows = sheet("用户", BilibiliLiveSessionExportColumns.USERS);
        repository.streamUsers(sessionId, row -> rows.write(
                row.actorKey(), row.identityQuality(), identifier(row.userUid()), row.displayName(),
                row.danmakuCount(), row.giftEventCount(), row.giftCount(), row.freeGiftCount(),
                row.paidEventCount(), row.paidAmountMilliYuan(), row.firstSeenAt(), row.lastSeenAt()));
        rows.finish();
    }

    private SheetRows sheet(String name, BilibiliLiveSessionExportColumns.ColumnSet columns) {
        Sheet sheet = workbook.createSheet(name);
        sheet.createFreezePane(0, 2);
        for (int index = 0; index < columns.headers().size(); index++) {
            sheet.setColumnWidth(index,
                    columnWidth(columns.headers().get(index), columns.comments().get(index)) * 256);
        }
        return new SheetRows(sheet, columns);
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

    private CellStyle createCommentStyle() {
        Font font = workbook.createFont();
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private int columnWidth(String header, String comment) {
        int baseWidth;
        if (header.endsWith("_at")) {
            baseWidth = 30;
        } else if (header.endsWith("_id") || header.endsWith("_uid") || "event_key".equals(header)) {
            baseWidth = 24;
        } else if ("message_text".equals(header)) {
            baseWidth = 56;
        } else if (header.contains("name")) {
            baseWidth = 24;
        } else {
            baseWidth = 18;
        }
        return Math.max(baseWidth, Math.min(36, comment.length() * 2));
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
        private int nextRow = 2;

        private SheetRows(Sheet sheet, BilibiliLiveSessionExportColumns.ColumnSet columns) {
            this.sheet = sheet;
            this.columnCount = columns.headers().size();
            Row header = sheet.createRow(0);
            Row comments = sheet.createRow(1);
            comments.setHeightInPoints(32F);
            for (int index = 0; index < columns.headers().size(); index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(columns.headers().get(index));
                cell.setCellStyle(headerStyle);
                Cell comment = comments.createCell(index);
                comment.setCellValue(columns.comments().get(index));
                comment.setCellStyle(commentStyle);
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
