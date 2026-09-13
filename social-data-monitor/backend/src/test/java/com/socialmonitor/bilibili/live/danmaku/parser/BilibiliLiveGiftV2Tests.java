package com.socialmonitor.bilibili.live.danmaku.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class BilibiliLiveGiftV2Tests {
    private final BilibiliLiveDanmakuEventParser parser = new BilibiliLiveDanmakuEventParser(new ObjectMapper());
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-13T12:15:00+08:00");

    @Test
    void decodesFanLightWithIdentityQuantityAmountAndSourceTime() {
        String json = envelope(gift("灯牌-1", "粉丝团灯牌", 1, "gold", 100L));
        var event = parser.parse(json, NOW).orElseThrow();
        assertThat(event.kind()).isEqualTo(BilibiliLiveDanmakuEvent.EventKind.GIFT);
        assertThat(event.senderUid()).isEqualTo(166415666L);
        assertThat(event.displayName()).isEqualTo("测试用户");
        assertThat(event.medalName()).isEqualTo("粉丝牌");
        assertThat(event.gift().giftName()).isEqualTo("粉丝团灯牌");
        assertThat(event.gift().quantity()).isEqualTo(1);
        assertThat(event.gift().paid()).isTrue();
        assertThat(event.amountMilliYuan()).isEqualTo(100L);
        assertThat(event.sourceEventId()).isEqualTo("灯牌-1");
        assertThat(event.occurredAt().toEpochSecond()).isEqualTo(1789272900L);
        assertThat(event.rawJson()).isEqualTo(json);
        assertThat(event.giftMetricDelta()).isEqualTo(1);
    }

    // Synthetic protobuf fixtures: protocol field numbers, not captured user transactions.
    @Test
    void expandsBatchWithoutDroppingOrCombiningGifts() {
        var events = parser.parseAll(envelope(
                gift("a", "礼物一", 2, "gold", 200L),
                gift("b", "礼物二", 3, "silver", 300L)), NOW);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(BilibiliLiveDanmakuEvent::sourceEventId).containsExactly("a", "b");
        assertThat(events).extracting(BilibiliLiveDanmakuEvent::amountMilliYuan).containsExactly(200L, 0L);
        assertThat(events.get(1).gift().quantity()).isEqualTo(3);
        assertThat(events.get(1).gift().paid()).isFalse();
    }

    @Test
    void preservesZeroTotalAndOnlyFallsBackToPriceWhenMissing() {
        var events = parser.parseAll(envelope(
                gift("a", "零金额", 2, "gold", 0L),
                gift("b", "无总额", 3, "gold", null)), NOW);
        assertThat(events).extracting(BilibiliLiveDanmakuEvent::amountMilliYuan).containsExactly(0L, 300L);
    }

    @Test
    void oldAndNewProtocolShareTransactionDedupKey() {
        var current = parser.parse(envelope(gift("same-tid", "礼物", 1, "gold", 100L)), NOW).orElseThrow();
        var legacy = parser.parse("""
                {"cmd":"SEND_GIFT","data":{"tid":"same-tid","giftId":31036,"num":1}}
                """, NOW).orElseThrow();
        assertThat(current.kind()).isEqualTo(legacy.kind());
        assertThat(current.sourceEventId()).isEqualTo(legacy.sourceEventId());
        assertThat(current.persistenceKey(1L, 1)).isEqualTo(legacy.persistenceKey(2L, 2));
    }

    @Test
    void missingTransactionIdsUseDistinctReceiptKeys() {
        var events = parser.parseAll(envelope(
                gift("", "一", 1, "gold", 100L), gift("", "二", 1, "gold", 100L)), NOW);
        assertThat(events.get(0).hasStrongSourceId()).isFalse();
        assertThat(events.get(0).persistenceKey(1L, 1)).isNotEqualTo(events.get(1).persistenceKey(1L, 2));
    }

    @Test
    void malformedPayloadDoesNotInventGiftOrBreakNextMessage() {
        for (String payload : new String[]{"?not-base64", "UgU=", "gICAgICAgICAgIA=", "AA==", ""}) {
            assertThat(parser.parseAll("{\"cmd\":\"SEND_GIFT_V2\",\"data\":{\"pb\":\"" + payload + "\"}}", NOW)).isEmpty();
        }
        assertThat(parser.parseAll(envelope(gift("ok", "礼物", 1, "gold", 100L)), NOW)).hasSize(1);
        assertThat(parser.parseAll(envelope(), NOW)).isEmpty();
        assertThat(parser.parseAll(envelope(gift("bad", "错误数量", 0, "gold", 0L)), NOW)).isEmpty();
    }

    @Test
    void skipsUnknownProtobufFieldsAndAcceptsCommandSuffix() {
        var item = new ByteArrayOutputStream();
        item.writeBytes(gift("id", "礼物", 1, "gold", 100L));
        item.writeBytes(number(123, 8));
        item.writeBytes(string(124, "future field"));
        String json = envelope(item.toByteArray()).replace("SEND_GIFT_V2", "SEND_GIFT_V2:1");
        var event = parser.parseAll(json, NOW).get(0);
        assertThat(event.command()).isEqualTo("SEND_GIFT_V2:1");
        assertThat(event.gift().giftName()).isEqualTo("礼物");
    }

    static String envelope(byte[]... gifts) {
        var data = new ByteArrayOutputStream();
        data.writeBytes(number(1, 166415666));
        data.writeBytes(string(2, "测试用户"));
        data.writeBytes(bytes(8, string(6, "粉丝牌")));
        for (byte[] gift : gifts) data.writeBytes(bytes(10, gift));
        return "{\"cmd\":\"SEND_GIFT_V2\",\"data\":{\"pb\":\""
                + Base64.getEncoder().encodeToString(data.toByteArray()) + "\"}}";
    }

    static byte[] gift(String tid, String name, int quantity, String coin, Long total) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(number(1, 31036));
        out.writeBytes(string(2, name));
        out.writeBytes(number(3, quantity));
        out.writeBytes(number(5, 100));
        if (total != null) out.writeBytes(number(7, total));
        out.writeBytes(string(8, coin));
        out.writeBytes(string(9, tid));
        out.writeBytes(number(10, 1789272900));
        return out.toByteArray();
    }

    static byte[] number(int field, long value) {
        var out = new ByteArrayOutputStream();
        varint(out, field * 8L);
        varint(out, value);
        return out.toByteArray();
    }

    static byte[] string(int field, String value) {
        return bytes(field, value.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] bytes(int field, byte[] value) {
        var out = new ByteArrayOutputStream();
        varint(out, field * 8L + 2);
        varint(out, value.length);
        out.writeBytes(value);
        return out.toByteArray();
    }

    static void varint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7fL) != 0) {
            out.write((int) (value & 0x7f) | 0x80);
            value >>>= 7;
        }
        out.write((int) value);
    }
}
