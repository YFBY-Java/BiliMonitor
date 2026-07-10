package com.socialmonitor.bilibili.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.config.BilibiliFollowerMonitorProperties;
import com.socialmonitor.bilibili.domain.BilibiliFetchedUserSnapshot;
import com.socialmonitor.platform.enums.FetchErrorType;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class BilibiliApiClientTests {

    private final BilibiliApiClient client = new BilibiliApiClient(
            new ObjectMapper(),
            new BilibiliFollowerMonitorProperties()
    );

    @Test
    void parsesPublicCardResponse() {
        String raw = """
                {
                  "code": 0,
                  "message": "OK",
                  "data": {
                    "card": {
                      "mid": "2",
                      "name": "碧诗",
                      "face": "https://i2.hdslb.com/bfs/face/example.jpg",
                      "fans": 1395836,
                      "attention": 423
                    },
                    "follower": 1395836
                  }
                }
                """;

        BilibiliFetchedUserSnapshot snapshot = client.parseCardResponse(raw, OffsetDateTime.parse("2026-06-08T12:00:00+08:00"));

        assertThat(snapshot.mid()).isEqualTo(2L);
        assertThat(snapshot.nickname()).isEqualTo("碧诗");
        assertThat(snapshot.avatarUrl()).isEqualTo("https://i2.hdslb.com/bfs/face/example.jpg");
        assertThat(snapshot.followerCount()).isEqualTo(1_395_836L);
        assertThat(snapshot.followingCount()).isEqualTo(423L);
        assertThat(snapshot.sourceEndpoint()).isEqualTo(BilibiliApiClient.CARD_ENDPOINT);
    }

    @Test
    void rejectsCardResponseWithoutFollowerCount() {
        String raw = """
                {
                  "code": 0,
                  "data": {
                    "card": {
                      "mid": "2",
                      "name": "碧诗"
                    }
                  }
                }
                """;

        assertThatThrownBy(() -> client.parseCardResponse(raw, OffsetDateTime.now()))
                .isInstanceOf(BilibiliFetchException.class)
                .satisfies(error -> assertThat(((BilibiliFetchException) error).errorType())
                        .isEqualTo(FetchErrorType.PARSE_ERROR));
    }
}
