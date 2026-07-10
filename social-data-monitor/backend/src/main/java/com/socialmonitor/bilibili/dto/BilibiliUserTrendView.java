package com.socialmonitor.bilibili.dto;

import java.util.List;

public record BilibiliUserTrendView(
        BilibiliMonitorUserView user,
        List<BilibiliFollowerPointView> points
) {
}
