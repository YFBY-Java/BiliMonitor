package com.socialmonitor.douyin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.socialmonitor.douyin.auth.client.DouyinOAuthClient;
import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import com.socialmonitor.douyin.auth.repository.DouyinAuthSessionRepository;
import com.socialmonitor.douyin.auth.repository.DouyinCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DouyinOAuthServiceWiringTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("app.douyin.auth.enabled=true")
            .withBean(DouyinAuthProperties.class, () -> mock(DouyinAuthProperties.class))
            .withBean(DouyinAuthSessionRepository.class, () -> mock(DouyinAuthSessionRepository.class))
            .withBean(DouyinCredentialRepository.class, () -> mock(DouyinCredentialRepository.class))
            .withBean(DouyinOAuthClient.class, () -> mock(DouyinOAuthClient.class))
            .withBean(
                    DouyinCredentialOperationTransaction.class,
                    () -> mock(DouyinCredentialOperationTransaction.class)
            )
            .withBean(DouyinOAuthService.class);

    @Test
    void springSelectsTheProductionConstructorWhenDouyinAuthIsEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DouyinOAuthService.class);
        });
    }
}
