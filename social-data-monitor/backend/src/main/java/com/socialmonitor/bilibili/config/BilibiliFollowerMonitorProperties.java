package com.socialmonitor.bilibili.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.bilibili.follower-monitor")
public class BilibiliFollowerMonitorProperties {

    private boolean enabled = true;
    private int schedulerDelayMs = 60000;
    private int dueBatchSize = 10;
    private int defaultIntervalSeconds = 3600;
    private int minIntervalSeconds = 1;
    private int maxIntervalSeconds = 2_592_000;
    private int shortIntervalWarningSeconds = 60;
    private int failureBackoffSeconds = 900;
    private int requestMinIntervalMs = 1500;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private int maxAttempts = 3;
    private int retryBackoffMs = 1500;
    private String userAgent = "SocialDataMonitor/0.1 (+low-frequency public Bilibili follower monitor)";
    private String referer = "https://www.bilibili.com/";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSchedulerDelayMs() {
        return schedulerDelayMs;
    }

    public void setSchedulerDelayMs(int schedulerDelayMs) {
        this.schedulerDelayMs = schedulerDelayMs;
    }

    public int getDueBatchSize() {
        return dueBatchSize;
    }

    public void setDueBatchSize(int dueBatchSize) {
        this.dueBatchSize = dueBatchSize;
    }

    public int getDefaultIntervalSeconds() {
        return defaultIntervalSeconds;
    }

    public void setDefaultIntervalSeconds(int defaultIntervalSeconds) {
        this.defaultIntervalSeconds = defaultIntervalSeconds;
    }

    public int getMinIntervalSeconds() {
        return minIntervalSeconds;
    }

    public void setMinIntervalSeconds(int minIntervalSeconds) {
        this.minIntervalSeconds = minIntervalSeconds;
    }

    public int getMaxIntervalSeconds() {
        return maxIntervalSeconds;
    }

    public void setMaxIntervalSeconds(int maxIntervalSeconds) {
        this.maxIntervalSeconds = maxIntervalSeconds;
    }

    public int getShortIntervalWarningSeconds() {
        return shortIntervalWarningSeconds;
    }

    public void setShortIntervalWarningSeconds(int shortIntervalWarningSeconds) {
        this.shortIntervalWarningSeconds = shortIntervalWarningSeconds;
    }

    public int getFailureBackoffSeconds() {
        return failureBackoffSeconds;
    }

    public void setFailureBackoffSeconds(int failureBackoffSeconds) {
        this.failureBackoffSeconds = failureBackoffSeconds;
    }

    public int getRequestMinIntervalMs() {
        return requestMinIntervalMs;
    }

    public void setRequestMinIntervalMs(int requestMinIntervalMs) {
        this.requestMinIntervalMs = requestMinIntervalMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(int retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
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
}
