package com.socialmonitor;

import com.socialmonitor.config.CollectorSchedulerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(CollectorSchedulerProperties.class)
public class SocialDataMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialDataMonitorApplication.class, args);
    }
}

