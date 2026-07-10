package com.socialmonitor.bilibili.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.bilibili.auth")
public class BilibiliAuthProperties {

    private boolean enabled = true;
    private int qrExpireSeconds = 180;
    private int pollIntervalMs = 1500;
    private int sessionCleanupDelayMs = 60000;
    private int connectTimeoutMs = 5000;
    private int requestTimeoutMs = 10000;
    private int refreshCheckIntervalHours = 24;
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36";
    private String referer = "https://www.bilibili.com/";
    private String credentialEncryptionKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getQrExpireSeconds() {
        return qrExpireSeconds;
    }

    public void setQrExpireSeconds(int qrExpireSeconds) {
        this.qrExpireSeconds = qrExpireSeconds;
    }

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(int pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getSessionCleanupDelayMs() {
        return sessionCleanupDelayMs;
    }

    public void setSessionCleanupDelayMs(int sessionCleanupDelayMs) {
        this.sessionCleanupDelayMs = sessionCleanupDelayMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public int getRefreshCheckIntervalHours() {
        return refreshCheckIntervalHours;
    }

    public void setRefreshCheckIntervalHours(int refreshCheckIntervalHours) {
        this.refreshCheckIntervalHours = refreshCheckIntervalHours;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public String getCredentialEncryptionKey() {
        return credentialEncryptionKey;
    }

    public void setCredentialEncryptionKey(String credentialEncryptionKey) {
        this.credentialEncryptionKey = credentialEncryptionKey;
    }
}
