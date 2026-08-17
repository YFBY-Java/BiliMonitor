package com.socialmonitor.bilibili.live.session.export;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.socialmonitor.bilibili.live.session.dto.BilibiliLiveSessionSummaryView;
import com.socialmonitor.bilibili.live.session.query.BilibiliLiveSessionQueryService;
import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class BilibiliLiveSessionExportService {

    public static final String SCHEMA_VERSION = "1";
    public static final String CAPTURE_SCOPE = "received_while_websocket_online_since_deployment";

    private static final List<String> ALL_ENTRY_NAMES = List.of(
            "manifest.json", "summary.csv", "danmaku.csv", "gifts.csv", "users.csv"
    );

    private final BilibiliLiveSessionQueryService queryService;
    private final BilibiliLiveSessionExportRepository repository;

    public BilibiliLiveSessionExportService(
            BilibiliLiveSessionQueryService queryService,
            BilibiliLiveSessionExportRepository repository
    ) {
        this.queryService = queryService;
        this.repository = repository;
    }

    public BilibiliLiveSessionSummaryView prepare(Long sessionId) {
        return queryService.session(sessionId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void export(Long sessionId, BilibiliLiveSessionExportCategory category, OutputStream outputStream)
            throws IOException {
        exportPrepared(prepare(sessionId), category, outputStream);
    }

    public void exportPrepared(
            BilibiliLiveSessionSummaryView summary,
            BilibiliLiveSessionExportCategory category,
            OutputStream outputStream
    ) throws IOException {
        switch (category) {
            case DANMAKU -> writeDanmakuCsv(summary.id(), outputStream);
            case GIFTS -> writeGiftsCsv(summary.id(), outputStream);
            case USERS -> writeUsersCsv(summary.id(), outputStream);
            case XLSX -> new BilibiliLiveSessionXlsxWriter(repository).write(summary, outputStream);
            case ALL -> writeZip(summary, outputStream);
        }
    }

    private void writeZip(BilibiliLiveSessionSummaryView summary, OutputStream outputStream) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(outputStream);

        putEntry(zip, "manifest.json");
        writeManifest(zip, summary);
        zip.closeEntry();

        putEntry(zip, "summary.csv");
        writeSummaryCsv(summary, zip);
        zip.closeEntry();

        putEntry(zip, "danmaku.csv");
        writeDanmakuCsv(summary.id(), zip);
        zip.closeEntry();

        putEntry(zip, "gifts.csv");
        writeGiftsCsv(summary.id(), zip);
        zip.closeEntry();

        putEntry(zip, "users.csv");
        writeUsersCsv(summary.id(), zip);
        zip.closeEntry();

        zip.finish();
        zip.flush();
    }

    private void writeManifest(OutputStream outputStream, BilibiliLiveSessionSummaryView summary) throws IOException {
        JsonGenerator json = new JsonFactory().createGenerator(outputStream);
        json.writeStartObject();
        json.writeStringField("schema_version", SCHEMA_VERSION);
        json.writeStringField("amount_unit", "milli_yuan");
        json.writeStringField("capture_scope", CAPTURE_SCOPE);
        json.writeStringField("coverage_status", summary.coverageStatus());
        json.writeNumberField("transport_session_count", summary.transportSessionCount());
        writeOptionalString(json, "capture_started_at", summary.captureStartedAt());
        writeOptionalString(json, "capture_ended_at", summary.captureEndedAt());
        json.writeNumberField("session_id", summary.id());
        json.writeStringField("generated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        json.writeArrayFieldStart("files");
        for (String entryName : ALL_ENTRY_NAMES) {
            if (!"manifest.json".equals(entryName)) {
                json.writeString(entryName);
            }
        }
        json.writeEndArray();
        json.writeEndObject();
        json.flush();
    }

    private void writeSummaryCsv(BilibiliLiveSessionSummaryView summary, OutputStream outputStream)
            throws IOException {
        BilibiliLiveSessionCsvWriter csv = new BilibiliLiveSessionCsvWriter(outputStream);
        csv.writeRow(
                "id", "monitor_id", "uid", "room_id", "state", "started_at", "ended_at",
                "start_source", "end_source", "coverage_status", "transport_session_count",
                "capture_started_at", "capture_ended_at", "danmaku_count", "gift_event_count", "gift_count",
                "free_gift_count", "gift_sender_count", "paid_user_count", "interacting_user_count",
                "unresolved_interacting_event_count", "unresolved_gift_event_count",
                "unresolved_paid_event_count", "paid_event_count", "paid_amount_milli_yuan",
                "first_event_at", "last_event_at"
        );
        csv.writeRow(
                summary.id(), summary.monitorId(), summary.uid(), summary.roomId(), summary.state(),
                summary.startedAt(), summary.endedAt(), summary.startSource(), summary.endSource(),
                summary.coverageStatus(), summary.transportSessionCount(), summary.captureStartedAt(),
                summary.captureEndedAt(),
                summary.danmakuCount(), summary.giftEventCount(), summary.giftCount(), summary.freeGiftCount(),
                summary.giftSenderCount(), summary.paidUserCount(), summary.interactingUserCount(),
                summary.unresolvedInteractingEventCount(), summary.unresolvedGiftEventCount(),
                summary.unresolvedPaidEventCount(), summary.paidEventCount(),
                summary.paidAmountMilliYuan(), summary.firstEventAt(), summary.lastEventAt()
        );
        csv.flush();
    }

    private void writeDanmakuCsv(Long sessionId, OutputStream outputStream) throws IOException {
        BilibiliLiveSessionCsvWriter csv = new BilibiliLiveSessionCsvWriter(outputStream);
        csv.writeRow(
                "occurred_at", "received_at", "sender_uid", "sender_name", "medal_name", "message_text",
                "command", "protocol_version", "source_event_id"
        );
        repository.streamDanmaku(sessionId, row -> csv.writeRow(
                row.occurredAt(), row.receivedAt(), row.senderUid(), row.senderName(), row.medalName(),
                row.messageText(), row.command(), row.protocolVersion(), row.sourceEventId()
        ));
        csv.flush();
    }

    private void writeGiftsCsv(Long sessionId, OutputStream outputStream) throws IOException {
        BilibiliLiveSessionCsvWriter csv = new BilibiliLiveSessionCsvWriter(outputStream);
        csv.writeRow(
                "occurred_at", "received_at", "event_kind", "sender_uid", "sender_name", "medal_name",
                "message_text", "gift_id", "gift_name", "gift_count", "coin_type",
                "unit_price_milli_yuan", "paid_amount_milli_yuan",
                "paid", "guard_level", "amount_source", "command", "protocol_version", "source_event_id",
                "event_key", "transport_session_id"
        );
        repository.streamGifts(sessionId, row -> csv.writeRow(
                row.occurredAt(), row.receivedAt(), row.eventKind(), row.senderUid(), row.senderName(),
                row.medalName(), row.messageText(), row.giftId(), row.giftName(), row.giftCount(),
                row.coinType(), row.unitPriceMilliYuan(),
                row.paidAmountMilliYuan(), row.paid(), row.guardLevel(), row.amountSource(), row.command(),
                row.protocolVersion(), row.sourceEventId(), row.eventKey(), row.transportSessionId()
        ));
        csv.flush();
    }

    private void writeUsersCsv(Long sessionId, OutputStream outputStream) throws IOException {
        BilibiliLiveSessionCsvWriter csv = new BilibiliLiveSessionCsvWriter(outputStream);
        csv.writeRow(
                "actor_key", "identity_quality", "user_uid", "display_name", "danmaku_count",
                "gift_event_count", "gift_count",
                "free_gift_count", "paid_event_count", "paid_amount_milli_yuan", "first_seen_at", "last_seen_at"
        );
        repository.streamUsers(sessionId, row -> csv.writeRow(
                row.actorKey(), row.identityQuality(), row.userUid(), row.displayName(), row.danmakuCount(),
                row.giftEventCount(),
                row.giftCount(), row.freeGiftCount(), row.paidEventCount(), row.paidAmountMilliYuan(),
                row.firstSeenAt(), row.lastSeenAt()
        ));
        csv.flush();
    }

    private void putEntry(ZipOutputStream zip, String name) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
    }

    private void writeOptionalString(JsonGenerator json, String fieldName, OffsetDateTime value) throws IOException {
        if (value == null) {
            json.writeNullField(fieldName);
        } else {
            json.writeStringField(fieldName, value.toString());
        }
    }
}
