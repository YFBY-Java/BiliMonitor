package com.socialmonitor.bilibili.live.session.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.bilibili.live.session.query.BilibiliLiveSessionQueryService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class BilibiliLiveSessionExportServiceTests {

    private final BilibiliLiveSessionQueryService queryService = mock(BilibiliLiveSessionQueryService.class);
    private final BilibiliLiveSessionExportRepository repository = mock(BilibiliLiveSessionExportRepository.class);
    private final BilibiliLiveSessionExportService service =
            new BilibiliLiveSessionExportService(queryService, repository);

    @BeforeEach
    void setUp() {
        when(queryService.session(42L)).thenReturn(summary());
    }

    @Test
    void allExportHasTheFixedSafeEntryOrder() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.export(42L, BilibiliLiveSessionExportCategory.ALL, output);

        List<String> entries = new ArrayList<>();
        Map<String, byte[]> contents = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                contents.put(entry.getName(), zip.readAllBytes());
            }
        }
        assertThat(entries).containsExactly(
                "manifest.json", "summary.csv", "danmaku.csv", "gifts.csv", "users.csv");
        assertThat(new String(contents.get("manifest.json"), StandardCharsets.UTF_8))
                .contains("\"schema_version\":\"1\"")
                .contains("\"amount_unit\":\"milli_yuan\"")
                .contains("\"capture_scope\":\"received_while_websocket_online_since_deployment\"")
                .contains("\"coverage_status\":\"RECEIVED_WHILE_ONLINE\"")
                .contains("\"transport_session_count\":2")
                .contains("\"capture_started_at\":\"2026-08-16T12:00:30+08:00\"")
                .contains("\"capture_ended_at\":\"2026-08-16T12:59:30+08:00\"");
        assertThat(contents.get("summary.csv")).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(contents.get("danmaku.csv")).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(contents.get("gifts.csv")).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(contents.get("users.csv")).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        verify(repository).streamDanmaku(eq(42L), any());
        verify(repository).streamGifts(eq(42L), any());
        verify(repository).streamUsers(eq(42L), any());
    }

    @Test
    void exportUsesOneRepeatableReadSnapshot() throws Exception {
        Method export = BilibiliLiveSessionExportService.class.getMethod(
                "export", Long.class, BilibiliLiveSessionExportCategory.class, OutputStream.class);

        Transactional transactional = export.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    @Test
    void giftsCsvIncludesCaptureProvenanceAndBlanksSilverUnitPrice() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-08-16T12:01:00+08:00");
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            BilibiliLiveSessionExportRepository.ExportRowConsumer<BilibiliLiveSessionGiftExportRow> consumer =
                    invocation.getArgument(1);
            consumer.accept(new BilibiliLiveSessionGiftExportRow(
                    timestamp, timestamp, "GIFT", 99L, "viewer", "medal", "thanks", 11L,
                    "Silver gift", 2L, "silver", null, 0L, false, null, "FREE",
                    "SEND_GIFT", "3", "event-1", "key-1", 77L));
            return null;
        }).when(repository).streamGifts(eq(42L), any());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.export(42L, BilibiliLiveSessionExportCategory.GIFTS, output);

        String csv = new String(output.toByteArray(), 3, output.size() - 3, StandardCharsets.UTF_8);
        assertThat(csv).contains(
                "event_kind", "message_text", "medal_name", "event_key", "transport_session_id");
        assertThat(csv).contains(
                "GIFT,99,viewer,medal,thanks,11,Silver gift,2,silver,,0,false,,FREE,SEND_GIFT,3,event-1,key-1,77");
    }

    @Test
    void streamsAHighVolumeCategoryRowByRowToTheCallerOutput() throws Exception {
        int rowCount = 100_000;
        AtomicInteger consumed = new AtomicInteger();
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-08-16T12:00:00+08:00");
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            BilibiliLiveSessionExportRepository.ExportRowConsumer<BilibiliLiveSessionDanmakuExportRow> consumer =
                    invocation.getArgument(1);
            for (int index = 0; index < rowCount; index++) {
                consumer.accept(new BilibiliLiveSessionDanmakuExportRow(
                        timestamp, timestamp, 1000L + index, "user", null, "message-" + index,
                        "DANMU_MSG", "1", "event-" + index));
                consumed.incrementAndGet();
            }
            return null;
        }).when(repository).streamDanmaku(eq(42L), any());

        service.export(42L, BilibiliLiveSessionExportCategory.DANMAKU, OutputStream.nullOutputStream());

        verify(repository).streamDanmaku(eq(42L), any());
        assertThat(consumed).hasValue(rowCount);
    }

    @Test
    void xlsxExportIsAReadableWorkbookAndPreservesLongUidsAsText() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-08-16T12:01:00+08:00");
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            BilibiliLiveSessionExportRepository.ExportRowConsumer<BilibiliLiveSessionDanmakuExportRow> consumer =
                    invocation.getArgument(1);
            consumer.accept(new BilibiliLiveSessionDanmakuExportRow(
                    timestamp, timestamp, 3_493_094_779_521_411L, "viewer", "medal", "=2+3",
                    "DANMU_MSG", "3", "event-1"));
            return null;
        }).when(repository).streamDanmaku(eq(42L), any());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.export(42L, BilibiliLiveSessionExportCategory.XLSX, output);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheetName(0)).isEqualTo("场次摘要");
            assertThat(workbook.getSheetName(1)).isEqualTo("弹幕");
            assertThat(workbook.getSheetName(2)).isEqualTo("礼物");
            assertThat(workbook.getSheetName(3)).isEqualTo("用户");
            assertThat(workbook.getSheet("弹幕").getRow(0).getCell(2).getStringCellValue())
                    .isEqualTo("sender_uid");
            assertThat(workbook.getSheet("弹幕").getRow(1).getCell(2).getCellType())
                    .isEqualTo(CellType.STRING);
            assertThat(workbook.getSheet("弹幕").getRow(1).getCell(2).getStringCellValue())
                    .isEqualTo("3493094779521411");
            assertThat(workbook.getSheet("弹幕").getRow(1).getCell(5).getCellType())
                    .isEqualTo(CellType.STRING);
            assertThat(workbook.getSheet("弹幕").getRow(1).getCell(5).getStringCellValue())
                    .isEqualTo("=2+3");
        }
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
