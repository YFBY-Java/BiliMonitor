package com.socialmonitor.bilibili.auth.domain;

public record BilibiliAccount(
        Long mid,
        String uname,
        String face,
        Integer level,
        Integer vipStatus
) {
}
