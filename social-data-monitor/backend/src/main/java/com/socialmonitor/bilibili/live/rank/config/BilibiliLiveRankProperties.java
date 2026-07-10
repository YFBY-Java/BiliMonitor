package com.socialmonitor.bilibili.live.rank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.bilibili.live-monitor.rank")
public class BilibiliLiveRankProperties {

    private boolean enabled = true;
    private int pageSize = 50;
    private int guardPageSize = 30;
    private int maxPages = 1;
    private int requestMinIntervalMs = 1500;
    private String webLocation = "444.8";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getGuardPageSize() {
        return guardPageSize;
    }

    public void setGuardPageSize(int guardPageSize) {
        this.guardPageSize = guardPageSize;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getRequestMinIntervalMs() {
        return requestMinIntervalMs;
    }

    public void setRequestMinIntervalMs(int requestMinIntervalMs) {
        this.requestMinIntervalMs = requestMinIntervalMs;
    }

    public String getWebLocation() {
        return webLocation;
    }

    public void setWebLocation(String webLocation) {
        this.webLocation = webLocation;
    }
}
