package com.socialmonitor.douyin.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DouyinAuthPropertiesTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "app.douyin.auth.enabled=true",
                    "app.douyin.auth.oauth-mode=mock",
                    "app.douyin.auth.worker-base-url=http://127.0.0.1:8787",
                    "app.douyin.auth.poll-interval-ms=900"
            );

    @Test
    void bindsIsolatedDouyinSettings() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DouyinAuthProperties.class);
            DouyinAuthProperties value = context.getBean(DouyinAuthProperties.class);
            assertThat(value.enabled()).isTrue();
            assertThat(value.oauthMode()).isEqualTo("mock");
            assertThat(value.workerBaseUrl()).isEqualTo("http://127.0.0.1:8787");
            assertThat(value.pollIntervalMs()).isEqualTo(900);
        });
    }

    @Test
    void suppliesPortableDefaults() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .run(context -> {
                    DouyinAuthProperties value = context.getBean(DouyinAuthProperties.class);
                    assertThat(value.enabled()).isFalse();
                    assertThat(value.oauthMode()).isEqualTo("disabled");
                    assertThat(value.oauthScope()).isEqualTo("user_info");
                    assertThat(value.workerBaseUrl()).isEqualTo("http://127.0.0.1:8787");
                    assertThat(value.qrExpireSeconds()).isEqualTo(180);
                    assertThat(value.pollIntervalMs()).isEqualTo(1500);
                });
    }

    @Test
    void registersConfigurationOnlyWhenFeatureIsEnabled() {
        ApplicationContextRunner conditionalRunner = new ApplicationContextRunner()
                .withUserConfiguration(DouyinAuthConfiguration.class);

        conditionalRunner.run(context -> assertThat(context).doesNotHaveBean(DouyinAuthProperties.class));
        conditionalRunner
                .withPropertyValues("app.douyin.auth.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DouyinAuthProperties.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DouyinAuthProperties.class)
    static class TestConfig {
    }
}
