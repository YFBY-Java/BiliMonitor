package com.socialmonitor.bilibili.live.session.dto;

import java.util.List;

public record BilibiliLiveSessionEventPageView(
        List<BilibiliLiveSessionEventView> items,
        long total,
        int page,
        int size
) {
}
