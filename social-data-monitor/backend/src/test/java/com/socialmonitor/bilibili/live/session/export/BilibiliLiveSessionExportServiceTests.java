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
                .contains("\"schema_version\":\"2\"")
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
    void csvFilesPlaceChineseColumnCommentsDirectlyBelowMachineHeaders() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.export(42L, BilibiliLiveSessionExportCategory.ALL, output);

        Map<String, byte[]> contents = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                contents.put(entry.getName(), zip.readAllBytes());
            }
        }
        assertFirstTwoCsvRows(contents.get("summary.csv"),
                "id,monitor_id,uid,room_id,state,started_at,ended_at,start_source,end_source,coverage_status,"
                        + "transport_session_count,capture_started_at,capture_ended_at,danmaku_count,"
                        + "gift_event_count,gift_count,free_gift_count,gift_sender_count,paid_user_count,"
                        + "interacting_user_count,unresolved_interacting_event_count,unresolved_gift_event_count,"
                        + "unresolved_paid_event_count,paid_event_count,paid_amount_milli_yuan,first_event_at,"
                        + "last_event_at",
                "场次记录 ID,直播监控记录 ID,主播 B站 UID,直播间 ID,场次状态,场次开始时间,场次结束时间,"
                        + "开始边界来源,结束边界来源,数据覆盖状态,WebSocket 传输会话数,在线采集开始时间,"
                        + "在线采集结束时间,弹幕事件数,礼物事件数,礼物数量,免费礼物数量,已识别送礼用户数,"
                        + "已识别付费用户数,已识别互动用户数,未解析身份的互动事件数,未解析身份的送礼事件数,"
                        + "未解析身份的付费事件数,付费事件数,消费金额（千分之一元）,首条事件时间,末条事件时间");
        assertFirstTwoCsvRows(contents.get("danmaku.csv"),
                "occurred_at,received_at,sender_uid,sender_name,medal_name,message_text,command,protocol_version,"
                        + "source_event_id",
                "事件发生时间,系统接收时间,发送者 B站 UID,发送者昵称,粉丝勋章名称,弹幕内容,B站消息命令,"
                        + "协议版本,上游事件 ID");
        assertFirstTwoCsvRows(contents.get("gifts.csv"),
                "occurred_at,received_at,event_kind,sender_uid,sender_name,medal_name,message_text,gift_id,"
                        + "gift_name,gift_count,coin_type,unit_price_milli_yuan,paid_amount_milli_yuan,paid,"
                        + "guard_level,amount_source,command,protocol_version,source_event_id,event_key,"
                        + "transport_session_id",
                "事件发生时间,系统接收时间,事件类型,发送者 B站 UID,发送者昵称,粉丝勋章名称,消息文本,礼物 ID,"
                        + "礼物名称,礼物数量,平台币类型,单件价格（千分之一元）,实付金额（千分之一元）,是否付费,"
                        + "舰队等级,金额来源,B站消息命令,协议版本,上游事件 ID,事件去重键,WebSocket 传输会话 ID");
        assertFirstTwoCsvRows(contents.get("users.csv"),
                "actor_key,identity_quality,user_uid,display_name,danmaku_count,gift_event_count,gift_count,"
                        + "free_gift_count,paid_event_count,paid_amount_milli_yuan,first_seen_at,last_seen_at",
                "身份聚合键,身份识别质量,用户 B站 UID,用户昵称,弹幕事件数,礼物事件数,礼物数量,免费礼物数量,"
                        + "付费事件数,消费金额（千分之一元）,首次出现时间,最后出现时间");
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
            assertThat(workbook.getSheet("弹幕").getRow(1).getCell(2).getStringCellValue())
                    .isEqualTo("发送者 B站 UID");
            assertThat(workbook.getSheet("场次摘要").getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("场次记录 ID");
            assertThat(workbook.getSheet("礼物").getRow(1).getCell(20).getStringCellValue())
                    .isEqualTo("WebSocket 传输会话 ID");
            assertThat(workbook.getSheet("用户").getRow(1).getCell(3).getStringCellValue())
                    .isEqualTo("用户昵称");
            assertThat(workbook.getSheet("弹幕").getRow(2)).isNotNull();
            assertThat(workbook.getSheet("弹幕").getRow(2).getCell(2).getCellType())
                    .isEqualTo(CellType.STRING);
            assertThat(workbook.getSheet("弹幕").getRow(2).getCell(2).getStringCellValue())
                    .isEqualTo("3493094779521411");
            assertThat(workbook.getSheet("弹幕").getRow(2).getCell(5).getCellType())
                    .isEqualTo(CellType.STRING);
            assertThat(workbook.getSheet("弹幕").getRow(2).getCell(5).getStringCellValue())
                    .isEqualTo("=2+3");
        }
    }

    private void assertFirstTwoCsvRows(byte[] bytes, String header, String comments) {
        String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(csv.split("\\r\\n", -1)).startsWith(header, comments);
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
