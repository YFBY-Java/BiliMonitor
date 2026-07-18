package com.socialmonitor.douyin.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.douyin.auth")
public record DouyinAuthProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("disabled") String oauthMode,
        @DefaultValue("") String oauthClientKey,
        @DefaultValue("") String oauthClientSecret,
        @DefaultValue("") String oauthRedirectUri,
        @DefaultValue("user_info") String oauthScope,
        @DefaultValue("") String credentialEncryptionKey,
        @DefaultValue("http://127.0.0.1:8787") String workerBaseUrl,
        @DefaultValue("") String workerToken,
        @DefaultValue("180") int qrExpireSeconds,
        @DefaultValue("1500") int pollIntervalMs,
        @DefaultValue("5000") int connectTimeoutMs,
        @DefaultValue("30000") int requestTimeoutMs
) {
}
