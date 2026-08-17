package com.socialmonitor.bilibili.live.danmaku;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BilibiliLiveDanmakuIdentityMigrationTests {

    @Test
    void addsSenderUidToRecentDanmakuProjection() throws Exception {
        URL resource = getClass().getResource("/db/migration/V11__bilibili_live_danmaku_recent_sender_uid.sql");
        assertThat(resource).as("V11 recent danmaku sender UID migration").isNotNull();
        String sql = Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("ALTER TABLE bilibili_live_danmaku_recent")
                .contains("ADD COLUMN IF NOT EXISTS sender_uid BIGINT")
                .contains("CHECK (sender_uid IS NULL OR sender_uid > 0)");
    }
}
