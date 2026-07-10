package com.socialmonitor.bilibili.live.danmaku.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BilibiliLiveDanmakuPacketCodecTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BilibiliLiveDanmakuPacketCodec codec = new BilibiliLiveDanmakuPacketCodec(objectMapper);

    @Test
    void authPacketUsesProvidedLoginUid() throws Exception {
        byte[] packet = codec.authPacket(7734200L, 123456789L, "token-value", "buvid-value", "1.14.3", 3);

        ByteBuffer buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN);
        int packetLength = buffer.getInt();
        int headerLength = Short.toUnsignedInt(buffer.getShort());
        int protocolVersion = Short.toUnsignedInt(buffer.getShort());
        int operation = buffer.getInt();
        buffer.getInt();
        byte[] body = new byte[packetLength - headerLength];
        buffer.get(body);
        JsonNode json = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));

        assertThat(headerLength).isEqualTo(16);
        assertThat(protocolVersion).isEqualTo(1);
        assertThat(operation).isEqualTo(BilibiliLiveDanmakuPacketCodec.OP_AUTH);
        assertThat(json.path("uid").asLong()).isEqualTo(123456789L);
        assertThat(json.path("roomid").asLong()).isEqualTo(7734200L);
        assertThat(json.path("protover").asInt()).isEqualTo(3);
        assertThat(json.path("key").asText()).isEqualTo("token-value");
    }
}
