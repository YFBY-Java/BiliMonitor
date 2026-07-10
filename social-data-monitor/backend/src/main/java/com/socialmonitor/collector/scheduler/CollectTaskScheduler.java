package com.socialmonitor.collector.scheduler;

import com.socialmonitor.config.CollectorSchedulerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CollectTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(CollectTaskScheduler.class);

    private final CollectorSchedulerProperties properties;

    public CollectTaskScheduler(CollectorSchedulerProperties properties) {
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.collector.scheduler.fixed-delay-ms:30000}")
    public void scanDueTasks() {
        if (!properties.isEnabled()) {
            return;
        }
        log.info("Collect task scheduler tick. DB-backed due-task scan is reserved for the next implementation step.");
    }
}

