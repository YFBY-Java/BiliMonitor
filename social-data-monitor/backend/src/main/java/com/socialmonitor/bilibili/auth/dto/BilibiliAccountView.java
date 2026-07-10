package com.socialmonitor.bilibili.auth.dto;

public record BilibiliAccountView(
        Long mid,
        String uname,
        String face,
        Integer level,
        Integer vipStatus
) {
}
