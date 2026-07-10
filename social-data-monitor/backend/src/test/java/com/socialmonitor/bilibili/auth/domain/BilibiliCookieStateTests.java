package com.socialmonitor.bilibili.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BilibiliCookieStateTests {

    @Test
    void buildsCookieHeaderInStableBilibiliOrderAndKeepsOriginalValues() {
        BilibiliCookieState state = new BilibiliCookieState(
                List.of(
                        new BilibiliCookie("sid", "sid", ".bilibili.com", "/", null, false, true, null),
                        new BilibiliCookie("DedeUserID", "123456", ".bilibili.com", "/", null, false, true, null),
                        new BilibiliCookie("bili_jct", "csrf", ".bilibili.com", "/", null, false, true, null),
                        new BilibiliCookie("SESSDATA", "sess", ".bilibili.com", "/", null, true, true, null),
                        new BilibiliCookie("buvid3", "", ".bilibili.com", "/", null, false, true, null)
                ),
                "refresh",
                null,
                null,
                null,
                null,
                Map.of()
        );

        assertThat(state.cookieHeader())
                .isEqualTo("SESSDATA=sess; bili_jct=csrf; DedeUserID=123456; sid=sid");
        assertThat(state.csrf()).isEqualTo("csrf");
        assertThat(state.cookieValue("SESSDATA")).isEqualTo("sess");
    }
}
