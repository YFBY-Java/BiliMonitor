package com.socialmonitor.bilibili.live.session.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.Actor;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.EventKind;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.Gift;
import com.socialmonitor.bilibili.live.danmaku.parser.BilibiliLiveDanmakuEvent.Metrics;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class JdbcBilibiliLiveSessionEventRepositoryTests {

    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.of(
            2026, 8, 16, 20, 0, 0, 0, ZoneOffset.ofHours(8)
    );

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcBilibiliLiveSessionEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcBilibiliLiveSessionEventRepository(jdbcTemplate);
    }

    @Test
    void atomicallyInsertsCanonicalEventAndReturnsTrue() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        boolean inserted = repository.insertIfAbsent(
                91L, 11L, 33L, 71L, 4L, 3, paidGift(), "Resolved Alice"
        );

        assertThat(inserted).isTrue();
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(sql.capture(), parameters.capture());
        assertThat(sql.getValue())
                .contains("INSERT INTO bilibili_live_session_event")
                .contains("CAST(:rawJson AS JSONB)")
                .contains("transport_session_id", "receipt_ordinal")
                .contains("ON CONFLICT DO NOTHING");
        assertThat(parameters.getValue().getValues())
                .containsEntry("liveSessionId", 91L)
                .containsEntry("monitorId", 11L)
                .containsEntry("roomId", 33L)
                .containsEntry("connectionSessionId", 71L)
                .containsEntry("receiptOrdinal", 4L)
                .containsEntry("eventKey", "SEND_GIFT:tid-1")
                .containsEntry("sourceEventId", "tid-1")
                .containsEntry("eventKind", "GIFT")
                .containsEntry("command", "SEND_GIFT")
                .containsEntry("protocolVersion", 3)
                .containsEntry("senderUid", 22L)
                .containsEntry("senderName", "Resolved Alice")
                .containsEntry("medalName", "Fans")
                .containsEntry("giftId", 7L)
                .containsEntry("giftName", "Battery")
                .containsEntry("giftCount", 2L)
                .containsEntry("coinType", "gold")
                .containsEntry("unitPriceMilliYuan", 250L)
                .containsEntry("paidAmountMilliYuan", 500L)
                .containsEntry("paid", true)
                .containsEntry("amountSource", "TOTAL_COIN")
                .containsEntry("rawJson", "{\"cmd\":\"SEND_GIFT\"}");
    }

    @Test
    void returnsFalseWhenUniqueEventKeyAlreadyExists() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);

        assertThat(repository.insertIfAbsent(91L, 11L, 33L, 71L, 4L, 3, paidGift(), null)).isFalse();
    }

    @Test
    void blankResolvedDisplayNameFallsBackToParsedActorName() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        repository.insertIfAbsent(91L, 11L, 33L, 71L, 4L, 3, paidGift(), "  ");

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("senderName")).isEqualTo("Alice");
    }

    @Test
    void noIdEventsPersistWithTransportReceiptIdentity() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        BilibiliLiveDanmakuEvent noId = new BilibiliLiveDanmakuEvent(
                "LIVE", EventKind.LIVE, null, null, "{}", OCCURRED_AT, OCCURRED_AT,
                null, null, null, null, Metrics.empty(), null, null, null, null
        );

        repository.insertIfAbsent(91L, 11L, 33L, 71L, 8L, 3, noId, null);

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValues())
                .containsEntry("eventKey", "receipt:71:8")
                .containsEntry("connectionSessionId", 71L)
                .containsEntry("receiptOrdinal", 8L);
    }

    @Test
    void silverGiftNeverPersistsCoinUnitsAsYuanUnitPrice() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        BilibiliLiveDanmakuEvent silver = new BilibiliLiveDanmakuEvent(
                "SEND_GIFT", EventKind.GIFT, "SEND_GIFT:silver-1", "silver-1", "{}",
                OCCURRED_AT, OCCURRED_AT, null, null, null,
                new Gift(1L, "free", 2, "silver", 500L, 1000L, false, 0L),
                Metrics.empty(), null, 0L, null, 2
        );

        repository.insertIfAbsent(91L, 11L, 33L, 71L, 5L, 3, silver, null);

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("unitPriceMilliYuan")).isNull();
        assertThat(parameters.getValue().getValue("paidAmountMilliYuan")).isEqualTo(0L);
    }

    @Test
    void checksStrongIdentityByMonitorKindAndSourceId() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.eq(Boolean.class)))
                .thenReturn(true);

        assertThat(repository.existsByStrongSourceId(11L, EventKind.GIFT, "tid-1")).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), parameters.capture(), org.mockito.ArgumentMatchers.eq(Boolean.class));
        assertThat(sql.getValue()).contains("monitor_id = :monitorId", "event_kind = :eventKind", "source_event_id = :sourceEventId");
        assertThat(parameters.getValue().getValues())
                .containsEntry("monitorId", 11L)
                .containsEntry("eventKind", "GIFT")
                .containsEntry("sourceEventId", "tid-1");
    }

    private BilibiliLiveDanmakuEvent paidGift() {
        return new BilibiliLiveDanmakuEvent(
                "SEND_GIFT",
                EventKind.GIFT,
                "SEND_GIFT:tid-1",
                "tid-1",
                "{\"cmd\":\"SEND_GIFT\"}",
                OCCURRED_AT,
                OCCURRED_AT.plusSeconds(1),
                null,
                null,
                new Actor(22L, "Alice", "Fans"),
                new Gift(7L, "Battery", 2, "gold", 250L, 500L, true, 500L),
                Metrics.empty(),
                null,
                500L,
                null,
                2
        );
    }
}
