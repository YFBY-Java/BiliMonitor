package com.socialmonitor.bilibili.live.session.repository;

import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.EventKind;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.Gift;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.bilibili.live-monitor", name = "storage-enabled", matchIfMissing = true)
public class JdbcBilibiliLiveSessionEventRepository implements BilibiliLiveSessionEventRepository {

    static final String INSERT_SQL = """
            INSERT INTO bilibili_live_session_event (
                live_session_id, transport_session_id, receipt_ordinal, monitor_id, room_id,
                event_key, source_event_id, event_kind, command, protocol_version,
                sender_uid, sender_name, medal_name, message_text,
                gift_id, gift_name, gift_count, coin_type,
                unit_price_milli_yuan, paid_amount_milli_yuan, paid,
                guard_level, amount_source, occurred_at, received_at, raw_payload_json
            ) VALUES (
                :liveSessionId, :connectionSessionId, :receiptOrdinal, :monitorId, :roomId,
                :eventKey, :sourceEventId, :eventKind, :command, :protocolVersion,
                :senderUid, :senderName, :medalName, :messageText,
                :giftId, :giftName, :giftCount, :coinType,
                :unitPriceMilliYuan, :paidAmountMilliYuan, :paid,
                :guardLevel, :amountSource, :occurredAt, :receivedAt, CAST(:rawJson AS JSONB)
            )
            ON CONFLICT DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcBilibiliLiveSessionEventRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsByStrongSourceId(Long monitorId, EventKind eventKind, String sourceEventId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM bilibili_live_session_event
                    WHERE monitor_id = :monitorId
                      AND event_kind = :eventKind
                      AND source_event_id = :sourceEventId
                )
                """, new MapSqlParameterSource()
                .addValue("monitorId", monitorId)
                .addValue("eventKind", eventKind.name())
                .addValue("sourceEventId", truncate(sourceEventId, 240)), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean insertIfAbsent(
            Long liveSessionId,
            Long monitorId,
            Long roomId,
            Long connectionSessionId,
            long receiptOrdinal,
            Integer protocolVersion,
            BilibiliLiveDanmakuEvent event,
            String resolvedDisplayName
    ) {
        Gift gift = event.gift();
        OffsetDateTime occurredAt = firstNonNull(event.occurredAt(), event.receivedAt());
        OffsetDateTime receivedAt = firstNonNull(event.receivedAt(), occurredAt);
        Long paidAmount = nonNegative(firstNonNull(
                event.amountMilliYuan(), gift == null ? null : gift.amountMilliYuan()
        ));
        Long quantity = persistedQuantity(event);
        boolean paid = isPaid(event, gift, paidAmount);

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("liveSessionId", liveSessionId)
                .addValue("connectionSessionId", connectionSessionId)
                .addValue("receiptOrdinal", receiptOrdinal)
                .addValue("monitorId", monitorId)
                .addValue("roomId", roomId)
                .addValue("eventKey", truncate(event.persistenceKey(connectionSessionId, receiptOrdinal), 240))
                .addValue("sourceEventId", truncate(event.sourceEventId(), 240))
                .addValue("eventKind", event.kind().name())
                .addValue("command", truncate(event.command(), 128))
                .addValue("protocolVersion", protocolVersion == null || protocolVersion < 0 ? null : protocolVersion)
                .addValue("senderUid", nonNegative(event.senderUid()))
                .addValue("senderName", truncate(
                        hasText(resolvedDisplayName) ? resolvedDisplayName : event.displayName(), 200
                ))
                .addValue("medalName", truncate(event.medalName(), 160))
                .addValue("messageText", event.messageText())
                .addValue("giftId", gift == null ? null : nonNegative(gift.giftId()))
                .addValue("giftName", truncate(gift == null ? null : gift.giftName(), 200))
                .addValue("giftCount", quantity)
                .addValue("coinType", truncate(gift == null ? null : gift.coinType(), 32))
                .addValue("unitPriceMilliYuan", unitPrice(event, gift, paidAmount, quantity))
                .addValue("paidAmountMilliYuan", paidAmount)
                .addValue("paid", paid)
                .addValue("guardLevel", nonNegative(event.guardLevel()))
                .addValue("amountSource", amountSource(event, gift, paid))
                .addValue("occurredAt", occurredAt)
                .addValue("receivedAt", receivedAt)
                .addValue("rawJson", event.rawJson() == null ? "{}" : event.rawJson());
        return jdbcTemplate.update(INSERT_SQL, parameters) == 1;
    }

    private Long persistedQuantity(BilibiliLiveDanmakuEvent event) {
        if (event.kind() == EventKind.GIFT && event.gift() != null && event.gift().quantity() != null) {
            return nonNegative(event.gift().quantity().longValue());
        }
        if ((event.kind() == EventKind.SUPER_CHAT || event.kind() == EventKind.GUARD_BUY)
                && event.quantity() != null) {
            return nonNegative(event.quantity().longValue());
        }
        return null;
    }

    private boolean isPaid(BilibiliLiveDanmakuEvent event, Gift gift, Long paidAmount) {
        if (event.kind() == EventKind.GIFT) {
            return gift != null && gift.paid();
        }
        return (event.kind() == EventKind.SUPER_CHAT || event.kind() == EventKind.GUARD_BUY)
                && paidAmount != null
                && paidAmount > 0;
    }

    private Long unitPrice(BilibiliLiveDanmakuEvent event, Gift gift, Long paidAmount, Long quantity) {
        if (event.kind() == EventKind.GIFT) {
            return gift == null || !gift.paid() ? null : nonNegative(gift.price());
        }
        if ((event.kind() == EventKind.SUPER_CHAT || event.kind() == EventKind.GUARD_BUY)
                && paidAmount != null && quantity != null && quantity > 0) {
            return paidAmount / quantity;
        }
        return null;
    }

    private String amountSource(BilibiliLiveDanmakuEvent event, Gift gift, boolean paid) {
        return switch (event.kind()) {
            case GIFT -> !paid
                    ? "FREE_COIN_TYPE"
                    : gift != null && gift.totalCoin() != null ? "TOTAL_COIN" : "PRICE_X_QUANTITY";
            case SUPER_CHAT -> "SUPER_CHAT_PRICE";
            case GUARD_BUY -> "GUARD_PRICE_X_QUANTITY";
            default -> null;
        };
    }

    private OffsetDateTime firstNonNull(OffsetDateTime left, OffsetDateTime right) {
        if (left != null) return left;
        if (right != null) return right;
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private Long firstNonNull(Long left, Long right) {
        return left == null ? right : left;
    }

    private Long nonNegative(Long value) {
        return value == null || value < 0 ? null : value;
    }

    private Integer nonNegative(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
