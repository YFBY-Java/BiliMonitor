package com.socialmonitor.bilibili.auth.dto;

public record BilibiliAuthRefreshView(
        boolean refreshed,
        boolean loggedIn,
        String message,
        BilibiliAccountView account
) {
}
