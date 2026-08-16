package com.socialmonitor.bilibili.live.danmaku.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BilibiliLiveDanmakuEventParserTests {

    private static final OffsetDateTime RECEIVED_AT = OffsetDateTime.parse("2026-08-16T12:00:00+08:00");

    private final BilibiliLiveDanmakuEventParser parser = new BilibiliLiveDanmakuEventParser(new ObjectMapper());

    @Test
    void eventExposesNormalizedValueObjectContract() {
        Set<String> methods = Arrays.stream(BilibiliLiveDanmakuEvent.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> nestedTypes = Arrays.stream(BilibiliLiveDanmakuEvent.class.getDeclaredClasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertThat(methods).contains(
                "kind", "eventKey", "sourceEventId", "rawJson", "receivedAt",
                "liveStartedAt", "liveKey", "actor", "gift", "metrics",
                "amountMilliYuan", "guardLevel", "quantity",
                "isPersistable", "isDanmaku", "giftMetricDelta", "superChatMetricDelta",
                "hasStrongSourceId", "persistenceKey"
        );
        assertThat(nestedTypes).contains("EventKind", "Actor", "Gift", "Metrics");
    }

    @Test
    void danmuUsesPlatformIdAndKeepsNormalizedIdentityAndTiming() {
        long sentAtMillis = 1_776_312_000_123L;
        String rawJson = """
                {
                  "cmd": "DANMU_MSG:4:0:2:2:2:0",
                  "info": [
                    [0, 0, 0, 0, 1776312000123, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                      {
                        "user": {
                          "base": {"name": "完整昵称"},
                          "medal": {"name": "详细粉丝牌"}
                        },
                        "extra": {"id_str": "dm-123"}
                      }
                    ],
                    "hello danmaku",
                    [1001, "遮***称"],
                    [0, "数组粉丝牌"]
                  ]
                }
                """;

        BilibiliLiveDanmakuEvent event = parse(rawJson);

        assertThat(event.command()).isEqualTo("DANMU_MSG:4:0:2:2:2:0");
        assertThat(event.kind()).isEqualTo(BilibiliLiveDanmakuEvent.EventKind.DANMAKU);
        assertThat(event.sourceEventId()).isEqualTo("dm-123");
        assertThat(event.eventKey()).isEqualTo("DANMU_MSG:dm-123");
        assertThat(event.rawJson()).isEqualTo(rawJson);
        assertThat(event.receivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(event.occurredAt()).isEqualTo(atMillis(sentAtMillis));
        assertThat(event.actor()).isEqualTo(new BilibiliLiveDanmakuEvent.Actor(1001L, "完整昵称", "详细粉丝牌"));
        assertThat(event.messageText()).isEqualTo("hello danmaku");
        assertThat(event.isPersistable()).isTrue();
        assertThat(event.isDanmaku()).isTrue();
    }

    @Test
    void identicalPacketsWithoutStrongIdsUseDistinctReceiptKeys() {
        String rawJson = """
                {"cmd":"DANMU_MSG","info":[[],"same text",[1001,"name"],[]]}
                """;

        BilibiliLiveDanmakuEvent first = parser.parse(rawJson, RECEIVED_AT).orElseThrow();
        BilibiliLiveDanmakuEvent second = parser.parse(rawJson, RECEIVED_AT.plusHours(1)).orElseThrow();

        assertThat(first.sourceEventId()).isNull();
        assertThat(first.eventKey()).isNull();
        assertThat(first.hasStrongSourceId()).isFalse();
        assertThat(first.persistenceKey(71L, 1L)).isEqualTo("receipt:71:1");
        assertThat(second.persistenceKey(71L, 2L)).isEqualTo("receipt:71:2");
    }

    @Test
    void paidGiftKeepsSourceValuesAndUsesQuantityAsMetricDelta() {
        String rawJson = """
                {
                  "cmd": "SEND_GIFT",
                  "data": {
                    "tid": "gift-tid-1",
                    "rnd": "gift-rnd-ignored",
                    "timestamp": 1776312000,
                    "uid": 2001,
                    "uname": "送礼用户",
                    "medal_info": {"medal_name": "礼物粉丝牌"},
                    "giftId": 31036,
                    "giftName": "小花花",
                    "num": 3,
                    "coin_type": "gold",
                    "price": 1000,
                    "total_coin": 3000
                  }
                }
                """;

        BilibiliLiveDanmakuEvent event = parse(rawJson);

        assertThat(event.kind()).isEqualTo(BilibiliLiveDanmakuEvent.EventKind.GIFT);
        assertThat(event.sourceEventId()).isEqualTo("gift-tid-1");
        assertThat(event.actor()).isEqualTo(new BilibiliLiveDanmakuEvent.Actor(2001L, "送礼用户", "礼物粉丝牌"));
        assertThat(event.gift()).isEqualTo(new BilibiliLiveDanmakuEvent.Gift(
                31036L, "小花花", 3, "gold", 1000L, 3000L, true, 3000L
        ));
        assertThat(event.amountMilliYuan()).isEqualTo(3000L);
        assertThat(event.quantity()).isEqualTo(3);
        assertThat(event.giftMetricDelta()).isEqualTo(1);
        assertThat(event.superChatMetricDelta()).isZero();
    }

    @Test
    void giftFallsBackToUinfoBaseName() {
        BilibiliLiveDanmakuEvent event = parse("""
                {
                  "cmd": "SEND_GIFT",
                  "data": {
                    "tid": "gift-new-shape",
                    "uid": 2003,
                    "uinfo": {"base": {"name": "新版礼物昵称"}},
                    "giftId": 31036,
                    "giftName": "小花花",
                    "num": 1,
                    "coin_type": "gold",
                    "price": 1000,
                    "total_coin": 1000
                  }
                }
                """);

        assertThat(event.actor().displayName()).isEqualTo("新版礼物昵称");
    }

    @Test
    void silverGiftIsFreeButStillCountsGiftQuantity() {
        BilibiliLiveDanmakuEvent event = parse("""
                {
                  "cmd": "SEND_GIFT",
                  "data": {
                    "rnd": 778899,
                    "uid": 2002,
                    "uname": "免费礼物用户",
                    "gift_id": 1,
                    "gift_name": "免费心心",
                    "num": 2,
                    "coin_type": "silver",
                    "price": 500,
                    "total_coin": 1000
                  }
                }
                """);

        assertThat(event.sourceEventId()).isEqualTo("778899");
        assertThat(event.gift().paid()).isFalse();
        assertThat(event.gift().price()).isEqualTo(500L);
        assertThat(event.gift().totalCoin()).isEqualTo(1000L);
        assertThat(event.gift().amountMilliYuan()).isZero();
        assertThat(event.amountMilliYuan()).isZero();
        assertThat(event.giftMetricDelta()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPER_CHAT_MESSAGE", "SUPER_CHAT_MESSAGE_JPN"})
    void superChatVariantsKeepIdentityMessageAndMilliYuanAmount(String command) {
        BilibiliLiveDanmakuEvent event = parse("""
                {
                  "cmd": "%s",
                  "data": {
                    "id": "sc-9001",
                    "uid": 3001,
                    "message": "醒目留言内容",
                    "price": 30,
                    "start_time": 1776312000,
                    "user_info": {
                      "uname": "SC 用户",
                      "medal_info": {"medal_name": "SC 粉丝牌"}
                    }
                  }
                }
                """.formatted(command));

        assertThat(event.kind()).isEqualTo(BilibiliLiveDanmakuEvent.EventKind.SUPER_CHAT);
        assertThat(event.sourceEventId()).isEqualTo("sc-9001");
        assertThat(event.actor()).isEqualTo(new BilibiliLiveDanmakuEvent.Actor(3001L, "SC 用户", "SC 粉丝牌"));
        assertThat(event.messageText()).isEqualTo("醒目留言内容");
        assertThat(event.amountMilliYuan()).isEqualTo(30_000L);
        assertThat(event.quantity()).isEqualTo(1);
        assertThat(event.giftMetricDelta()).isZero();
        assertThat(event.superChatMetricDelta()).isEqualTo(1);
    }

    @Test
    void superChatReadsMedalFromDataLevelShape() {
        BilibiliLiveDanmakuEvent event = parse("""
                {
                  "cmd": "SUPER_CHAT_MESSAGE",
                  "data": {
                    "id": "sc-data-medal",
                    "uid": 3002,
                    "message": "真实层级",
                    "price": 50,
                    "user_info": {"uname": "SC 新用户"},
                    "medal_info": {"medal_name": "顶层 SC 粉丝牌"}
                  }
                }
                """);

        assertThat(event.actor().medalName()).isEqualTo("顶层 SC 粉丝牌");
    }

    @Test
    void guardBuyUsesPayflowAndMultipliesMilliYuanPriceByQuantity() {
        BilibiliLiveDanmakuEvent event = parse("""
                {
                  "cmd": "GUARD_BUY",
                  "data": {
                    "payflow_id": "guard-payflow-1",
                    "uid": 4001,
                    "username": "上舰用户",
                    "guard_level": 3,
                    "num": 2,
                    "price": 198000,
                    "start_time": 1776312000
                  }
                }
                """);

        assertThat(event.kind()).isEqualTo(BilibiliLiveDanmakuEvent.EventKind.GUARD_BUY);
        assertThat(event.sourceEventId()).isEqualTo("guard-payflow-1");
        assertThat(event.actor()).isEqualTo(new BilibiliLiveDanmakuEvent.Actor(4001L, "上舰用户", null));
        assertThat(event.guardLevel()).isEqualTo(3);
        assertThat(event.quantity()).isEqualTo(2);
        assertThat(event.amountMilliYuan()).isEqualTo(396_000L);
        assertThat(event.giftMetricDelta()).isZero();
        assertThat(event.superChatMetricDelta()).isZero();
    }

    @Test
    void guardBuyWithoutPayflowGetsStableSemanticSourceId() {
        String rawJson = """
                {"cmd":"GUARD_BUY","data":{"uid":4001,"guard_level":3,"num":1,"price":198000,"start_time":1776312000}}
                """;

        BilibiliLiveDanmakuEvent first = parser.parse(rawJson, RECEIVED_AT).orElseThrow();
        BilibiliLiveDanmakuEvent second = parser.parse(rawJson, RECEIVED_AT.plusMinutes(5)).orElseThrow();

        assertThat(first.sourceEventId()).startsWith("semantic:").isEqualTo(second.sourceEventId());
        assertThat(first.eventKey()).isEqualTo(second.eventKey());
        assertThat(first.hasStrongSourceId()).isFalse();
        assertThat(first.persistenceKey(71L, 4L)).isEqualTo("receipt:71:4");
    }

    @Test
    void liveControlKeepsLiveBoundaryIdentity() {
        long liveAtSeconds = 1_776_312_000L;
        BilibiliLiveDanmakuEvent event = parse("""
                {
                  "cmd": "LIVE",
                  "msg_id": "live-msg-1",
                  "live_key": "live-key-1",
                  "live_time": 1776312000
                }
                """);

        assertThat(event.kind()).isEqualTo(BilibiliLiveDanmakuEvent.EventKind.LIVE);
        assertThat(event.sourceEventId()).isEqualTo("live-msg-1");
        assertThat(event.eventKey()).isEqualTo("LIVE:live-msg-1");
        assertThat(event.liveKey()).isEqualTo("live-key-1");
        assertThat(event.liveStartedAt()).isEqualTo(atSeconds(liveAtSeconds));
        assertThat(event.isPersistable()).isTrue();
    }

    @Test
    void liveControlKeepsReceiptEventAndPlatformStartTimesSeparate() {
        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-08-16T12:10:00+08:00");
        BilibiliLiveDanmakuEvent event = parser.parse("""
                {
                  "cmd": "LIVE",
                  "msg_id": "live-three-times",
                  "timestamp": 1776312300,
                  "live_time": 1776312000
                }
                """, receivedAt).orElseThrow();

        assertThat(event.receivedAt()).isEqualTo(receivedAt);
        assertThat(event.occurredAt()).isEqualTo(atSeconds(1_776_312_300L));
        assertThat(event.liveStartedAt()).isEqualTo(atSeconds(1_776_312_000L));
    }

    @Test
    void liveWithoutEventTimestampUsesReceiptTimeNotPlatformStartTime() {
        OffsetDateTime receivedAt = OffsetDateTime.parse("2026-08-16T12:10:00+08:00");
        BilibiliLiveDanmakuEvent event = parser.parse("""
                {"cmd":"LIVE","msg_id":"live-no-event-time","live_time":1776312000}
                """, receivedAt).orElseThrow();

        assertThat(event.receivedAt()).isEqualTo(receivedAt);
        assertThat(event.occurredAt()).isEqualTo(receivedAt);
        assertThat(event.liveStartedAt()).isEqualTo(atSeconds(1_776_312_000L));
    }

    @Test
    void preparingControlFallsBackToLiveKeyIdentity() {
        BilibiliLiveDanmakuEvent event = parse("""
                {"cmd":"PREPARING","live_key":"live-key-1"}
                """);

        assertThat(event.kind()).isEqualTo(BilibiliLiveDanmakuEvent.EventKind.PREPARING);
        assertThat(event.sourceEventId()).isEqualTo("live-key-1");
        assertThat(event.eventKey()).isEqualTo("PREPARING:live-key-1");
        assertThat(event.liveKey()).isEqualTo("live-key-1");
        assertThat(event.liveStartedAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIVE", "PREPARING"})
    void minimalControlReceiptsWaitForTransportIdentity(String command) {
        BilibiliLiveDanmakuEvent event = parse("{\"cmd\":\"" + command + "\"}");

        assertThat(event.sourceEventId()).isNull();
        assertThat(event.eventKey()).isNull();
        assertThat(event.persistenceKey(71L, 9L)).isEqualTo("receipt:71:9");
    }

    @Test
    void watchedChangeParsesLocalizedMetricText() {
        BilibiliLiveDanmakuEvent event = parse("""
                {"cmd":"WATCHED_CHANGE","data":{"text_large":"1.2万"}}
                """);

        assertThat(event.kind()).isEqualTo(BilibiliLiveDanmakuEvent.EventKind.METRICS);
        assertThat(event.metrics()).isEqualTo(new BilibiliLiveDanmakuEvent.Metrics(null, null, 12_000L));
    }

    @Test
    void likeUpdateKeepsTotalAndIncrement() {
        BilibiliLiveDanmakuEvent event = parse("""
                {"cmd":"LIKE_INFO_V3_UPDATE","data":{"click_count":120,"increment":7}}
                """);

        assertThat(event.metrics()).isEqualTo(new BilibiliLiveDanmakuEvent.Metrics(120L, 7L, null));
    }

    @Test
    void roomRealtimeUpdateKeepsLikeAndWatchedMetrics() {
        BilibiliLiveDanmakuEvent event = parse("""
                {"cmd":"ROOM_REAL_TIME_MESSAGE_UPDATE","data":{"like_count":88,"watched_count":999}}
                """);

        assertThat(event.metrics()).isEqualTo(new BilibiliLiveDanmakuEvent.Metrics(88L, null, 999L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMBO_SEND", "USER_TOAST_MSG"})
    void knownConsumptionNotificationsStayOutOfCanonicalFinance(String command) {
        BilibiliLiveDanmakuEvent event = parse("""
                {"cmd":"%s","data":{"uid":5001,"username":"通知用户","num":10,"price":198000}}
                """.formatted(command));

        assertThat(event.kind()).isEqualTo(BilibiliLiveDanmakuEvent.EventKind.NOTIFICATION);
        assertThat(event.isPersistable()).isTrue();
        assertThat(event.giftMetricDelta()).isEqualTo("COMBO_SEND".equals(command) ? 1 : 0);
        assertThat(event.superChatMetricDelta()).isZero();
        assertThat(event.gift()).isNull();
        assertThat(event.amountMilliYuan()).isNull();
        assertThat(event.rawJson()).contains(command);
    }

    @Test
    void unknownCommandKeepsRawButIsNotPersistable() {
        String rawJson = """
                {"cmd":"FUTURE_EVENT","data":{"new_field":"new-value"}}
                """;

        BilibiliLiveDanmakuEvent event = parse(rawJson);

        assertThat(event.kind()).isEqualTo(BilibiliLiveDanmakuEvent.EventKind.UNKNOWN);
        assertThat(event.isPersistable()).isFalse();
        assertThat(event.rawJson()).isEqualTo(rawJson);
        assertThat(event.eventKey()).isNull();
        assertThat(event.persistenceKey(71L, 3L)).isEqualTo("receipt:71:3");
    }

    @Test
    void comboSendPreservesLegacyPacketGiftMetricWithoutCanonicalGift() {
        BilibiliLiveDanmakuEvent event = parse("""
                {
                  "cmd": "COMBO_SEND",
                  "data": {
                    "uid": 1001,
                    "gift_id": 31036,
                    "combo_num": 10,
                    "batch_combo_id": "combo-1"
                  }
                }
                """);

        assertThat(event.giftCount()).isEqualTo(1);
        assertThat(event.gift()).isNull();
        assertThat(event.amountMilliYuan()).isNull();
    }

    @Test
    void malformedNegativeProviderNumbersAreNormalizedBeforePersistence() {
        BilibiliLiveDanmakuEvent event = parse("""
                {
                  "cmd": "SEND_GIFT",
                  "data": {
                    "tid": "negative-gift",
                    "uid": -9,
                    "gift_id": -7,
                    "num": -3,
                    "coin_type": "gold",
                    "price": -100,
                    "total_coin": -300
                  }
                }
                """);

        assertThat(event.senderUid()).isNull();
        assertThat(event.gift().giftId()).isNull();
        assertThat(event.gift().quantity()).isEqualTo(1);
        assertThat(event.gift().price()).isNull();
        assertThat(event.gift().totalCoin()).isNull();
        assertThat(event.amountMilliYuan()).isZero();
    }

    private BilibiliLiveDanmakuEvent parse(String rawJson) {
        return parser.parse(rawJson, RECEIVED_AT).orElseThrow();
    }

    private OffsetDateTime atMillis(long epochMillis) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.ofHours(8));
    }

    private OffsetDateTime atSeconds(long epochSeconds) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.ofHours(8));
    }
}
