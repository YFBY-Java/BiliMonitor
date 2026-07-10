package com.socialmonitor.bilibili.live.dto;

import java.util.List;

public record BilibiliLiveRoomTrendView(
        BilibiliLiveRoomView room,
        List<BilibiliLiveTrendPointView> points
) {
}
