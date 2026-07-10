package com.socialmonitor.bilibili.auth.domain;

import java.util.List;

public final class BilibiliAuthConstants {

    public static final String PLATFORM_CODE = "bilibili";
    public static final String AUTH_TYPE = "BILIBILI_WEB_COOKIE";
    public static final List<String> COOKIE_ORDER = List.of(
            "SESSDATA",
            "bili_jct",
            "DedeUserID",
            "DedeUserID__ckMd5",
            "sid",
            "buvid3",
            "buvid4",
            "b_nut"
    );

    private BilibiliAuthConstants() {
    }
}
