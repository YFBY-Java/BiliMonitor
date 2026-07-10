package com.socialmonitor.collector.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private final Map<String, Instant> nextAllowedAt = new ConcurrentHashMap<>();

    public void acquire(String platformCode, String policyKey) {
        // MVP placeholder: use DB/Redis-backed token buckets when multiple instances are introduced.
    }

    public void acquireMinInterval(String platformCode, String policyKey, Duration minInterval) {
        if (minInterval == null || minInterval.isZero() || minInterval.isNegative()) {
            return;
        }
        String key = platformCode + ":" + policyKey;
        synchronized (nextAllowedAt) {
            Instant now = Instant.now();
            Instant allowedAt = nextAllowedAt.getOrDefault(key, now);
            if (allowedAt.isAfter(now)) {
                sleep(Duration.between(now, allowedAt));
                now = Instant.now();
            }
            nextAllowedAt.put(key, now.plus(minInterval));
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
