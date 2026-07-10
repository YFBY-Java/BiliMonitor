package com.socialmonitor.bilibili.auth.domain;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BilibiliCookieState(
        List<BilibiliCookie> cookies,
        String refreshToken,
        BilibiliAccount account,
        OffsetDateTime expiresAt,
        OffsetDateTime lastValidatedAt,
        OffsetDateTime lastRefreshCheckedAt,
        Map<String, Object> lastNav
) {

    public String cookieHeader() {
        Map<String, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < BilibiliAuthConstants.COOKIE_ORDER.size(); i++) {
            order.put(BilibiliAuthConstants.COOKIE_ORDER.get(i), i);
        }
        return cookies.stream()
                .filter(cookie -> cookie.value() != null && !cookie.value().isBlank())
                .sorted(Comparator.comparingInt(cookie -> order.getOrDefault(cookie.name(), 100)))
                .map(cookie -> cookie.name() + "=" + cookie.value())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
    }

    public String csrf() {
        return cookieValue("bili_jct");
    }

    public String cookieValue(String name) {
        return cookies.stream()
                .filter(cookie -> cookie.name().equals(name))
                .map(BilibiliCookie::value)
                .findFirst()
                .orElse(null);
    }
}
