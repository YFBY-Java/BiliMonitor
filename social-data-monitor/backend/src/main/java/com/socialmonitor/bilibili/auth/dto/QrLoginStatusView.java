package com.socialmonitor.bilibili.auth.dto;

public record QrLoginStatusView(
        String status,
        String message,
        Integer expiresInSeconds,
        BilibiliAccountView account,
        BilibiliCredentialFullView credential
) {
}
