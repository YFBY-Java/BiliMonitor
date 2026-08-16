package com.socialmonitor.bilibili.live.session.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class BilibiliLiveSessionExportRepositoryTests {

    @Test
    void giftExportContainsOnlyFinancialGiftFactsAndNoRawPayload() {
        String sql = compact(BilibiliLiveSessionExportRepository.GIFTS_SQL);

        assertThat(sql).contains("EVENT_KIND IN ('GIFT', 'SUPER_CHAT', 'GUARD_BUY')");
        assertThat(sql).contains(
                "MESSAGE_TEXT", "MEDAL_NAME", "EVENT_KEY", "TRANSPORT_SESSION_ID",
                "WHEN COALESCE(PAID, FALSE) = FALSE", "LOWER(COALESCE(COIN_TYPE, '')) = 'SILVER'");
        assertThat(sql).doesNotContain("NOTIFICATION", "COMBO", "USER_TOAST", "RAW_PAYLOAD_JSON", "COOKIE");
    }

    @Test
    void everyExportQueryIsSessionScopedAndDeterministicallyOrdered() {
        assertThat(compact(BilibiliLiveSessionExportRepository.DANMAKU_SQL))
                .contains("LIVE_SESSION_ID = ?", "ORDER BY OCCURRED_AT ASC, ID ASC");
        assertThat(compact(BilibiliLiveSessionExportRepository.GIFTS_SQL))
                .contains("LIVE_SESSION_ID = ?", "ORDER BY OCCURRED_AT ASC, ID ASC");
        assertThat(compact(BilibiliLiveSessionExportRepository.USERS_SQL))
                .contains("LIVE_SESSION_ID = ?", "GROUP BY ACTOR_KEY", "ORDER BY PAID_AMOUNT_MILLI_YUAN DESC")
                .contains("'EVENT:' || EVENT.ID::TEXT", "'VERIFIED_UID'", "'UNRESOLVED_EVENT'", "DISTINCT ON (ACTOR_KEY)")
                .doesNotContain("ARRAY_AGG");
    }

    private String compact(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }
}
