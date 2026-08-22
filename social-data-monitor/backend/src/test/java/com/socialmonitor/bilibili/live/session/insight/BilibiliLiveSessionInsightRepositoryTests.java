package com.socialmonitor.bilibili.live.session.insight;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BilibiliLiveSessionInsightRepositoryTests {

    @Test
    void userSegmentsAggregateEveryVerifiedUidWithoutAResultLimit() {
        String sql = BilibiliLiveSessionInsightRepository.USER_SEGMENTS_SQL
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(sql)
                .contains("sender_uid IS NOT NULL AND sender_uid > 0")
                .contains("GROUP BY sender_uid")
                .contains("paid_amount_milli_yuan > 0 AND danmaku_count > 0")
                .contains("paid_amount_milli_yuan = 0 AND danmaku_count >= 3")
                .doesNotContain("LIMIT");
    }

    @Test
    void danmakuDepthUsesVerifiedUsersAndExplainableEngagementThresholds() {
        String sql = BilibiliLiveSessionInsightRepository.DANMAKU_DEPTH_SQL
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(sql)
                .contains("sender_uid IS NOT NULL AND sender_uid > 0")
                .contains("danmaku_count >= 3")
                .contains("active_stage_count >= 2")
                .contains("LOWER(BTRIM(message_text))");
    }

    @Test
    void repeatedMessagesRequireAtLeastTwoOccurrencesAndStayBounded() {
        String sql = BilibiliLiveSessionInsightRepository.REPEATED_MESSAGES_SQL
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(sql)
                .contains("GROUP BY LOWER(BTRIM(message_text))")
                .contains("HAVING COUNT(*) >= 2")
                .contains("LIMIT 5");
    }

    @Test
    void paymentDepthUsesCurrentVerifiedPayersAndRecordedPriorSessions() {
        String sql = BilibiliLiveSessionInsightRepository.PAYMENT_DEPTH_SQL
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(sql)
                .contains("event.sender_uid IS NOT NULL AND event.sender_uid > 0")
                .contains("prior_session.monitor_id = current_session.monitor_id")
                .contains("prior_session.started_at < current_session.started_at")
                .contains("first_danmaku_at <= first_paid_at");
    }

    @Test
    void spendTiersUseDocumentedMilliYuanBoundaries() {
        String sql = BilibiliLiveSessionInsightRepository.SPEND_TIERS_SQL
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(sql)
                .contains("paid_amount_milli_yuan < 1000")
                .contains("paid_amount_milli_yuan < 10000")
                .contains("ELSE 'CORE'");
    }
}
