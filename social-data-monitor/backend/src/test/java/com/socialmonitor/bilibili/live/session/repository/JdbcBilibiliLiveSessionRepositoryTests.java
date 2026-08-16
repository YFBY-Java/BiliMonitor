package com.socialmonitor.bilibili.live.session.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcBilibiliLiveSessionRepositoryTests {

    @Test
    void providesJdbcBackedRepositoryBean() throws Exception {
        Class<?> repositoryType = Class.forName(
                "com.socialmonitor.bilibili.live.session.repository.JdbcBilibiliLiveSessionRepository"
        );

        assertThat(repositoryType).isAssignableTo(BilibiliLiveSessionRepository.class);
    }
}
