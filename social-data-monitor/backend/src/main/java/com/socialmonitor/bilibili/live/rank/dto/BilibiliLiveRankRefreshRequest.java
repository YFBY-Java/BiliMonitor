package com.socialmonitor.bilibili.live.rank.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record BilibiliLiveRankRefreshRequest(
        List<String> families,
        List<String> types,
        @Min(1) @Max(5) Integer maxPages,
        Boolean force
) {
}
