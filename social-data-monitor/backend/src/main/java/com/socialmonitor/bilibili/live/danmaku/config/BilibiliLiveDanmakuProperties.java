package com.socialmonitor.bilibili.live.danmaku.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.bilibili.live-monitor.danmaku")
public class BilibiliLiveDanmakuProperties {

    private boolean enabled = true;
    private boolean autoStartEnabled = true;
    private int schedulerDelayMs = 5000;
    private int maxConnections = 10;
    private int heartbeatSeconds = 30;
    private int connectTimeoutMs = 8000;
    private int bucketSeconds = 60;
    private int recentMessageLimitPerRoom = 200;
    private int listenProbeSeconds = 20;
    private int protocolVersion = 3;
    private int wbiCacheSeconds = 43_200;
    private int buvidCacheSeconds = 43_200;
    private boolean useLoginCredential = true;
    private String webLocation = "444.8";
    private String clientVersion = "1.14.3";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStartEnabled() {
        return autoStartEnabled;
    }

    public void setAutoStartEnabled(boolean autoStartEnabled) {
        this.autoStartEnabled = autoStartEnabled;
    }

    public int getSchedulerDelayMs() {
        return schedulerDelayMs;
    }

    public void setSchedulerDelayMs(int schedulerDelayMs) {
        this.schedulerDelayMs = schedulerDelayMs;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getHeartbeatSeconds() {
        return heartbeatSeconds;
    }

    public void setHeartbeatSeconds(int heartbeatSeconds) {
        this.heartbeatSeconds = heartbeatSeconds;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getBucketSeconds() {
        return bucketSeconds;
    }

    public void setBucketSeconds(int bucketSeconds) {
        this.bucketSeconds = bucketSeconds;
    }

    public int getRecentMessageLimitPerRoom() {
        return recentMessageLimitPerRoom;
    }

    public void setRecentMessageLimitPerRoom(int recentMessageLimitPerRoom) {
        this.recentMessageLimitPerRoom = recentMessageLimitPerRoom;
    }

    public int getListenProbeSeconds() {
        return listenProbeSeconds;
    }

    public void setListenProbeSeconds(int listenProbeSeconds) {
        this.listenProbeSeconds = listenProbeSeconds;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public int getWbiCacheSeconds() {
        return wbiCacheSeconds;
    }

    public void setWbiCacheSeconds(int wbiCacheSeconds) {
        this.wbiCacheSeconds = wbiCacheSeconds;
    }

    public int getBuvidCacheSeconds() {
        return buvidCacheSeconds;
    }

    public void setBuvidCacheSeconds(int buvidCacheSeconds) {
        this.buvidCacheSeconds = buvidCacheSeconds;
    }

    public boolean isUseLoginCredential() {
        return useLoginCredential;
    }

    public void setUseLoginCredential(boolean useLoginCredential) {
        this.useLoginCredential = useLoginCredential;
    }

    public String getWebLocation() {
        return webLocation;
    }

    public void setWebLocation(String webLocation) {
        this.webLocation = webLocation;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }
}
