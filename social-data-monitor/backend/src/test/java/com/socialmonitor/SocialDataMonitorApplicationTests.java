package com.socialmonitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
        "app.collector.scheduler.enabled=false",
        "app.bilibili.follower-monitor.storage-enabled=false",
        "app.bilibili.live-monitor.storage-enabled=false",
        "app.bilibili.auth.enabled=false",
        "app.subject-monitor.enabled=false"
})
class SocialDataMonitorApplicationTests {

    @Test
    void contextLoads() {
    }
}
