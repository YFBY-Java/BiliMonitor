package com.socialmonitor.bilibili.live.danmaku.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.InflaterInputStream;
import org.brotli.dec.BrotliInputStream;
import org.springframework.stereotype.Component;

@Component
public class BilibiliLiveDanmakuPacketCodec {

    public static final int OP_HEARTBEAT = 2;
    public static final int OP_HEARTBEAT_REPLY = 3;
    public static final int OP_COMMAND = 5;
    public static final int OP_AUTH = 7;
    public static final int OP_AUTH_REPLY = 8;

    private static final int HEADER_LENGTH = 16;

    private final ObjectMapper objectMapper;

    public BilibiliLiveDanmakuPacketCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] authPacket(Long roomId, String token, String buvid, String clientVersion) {
        return authPacket(roomId, token, buvid, clientVersion, 3);
    }

    public byte[] authPacket(Long roomId, String token, String buvid, String clientVersion, int protocolVersion) {
        return authPacket(roomId, 0L, token, buvid, clientVersion, protocolVersion);
    }

    public byte[] authPacket(
            Long roomId,
            Long uid,
            String token,
            String buvid,
            String clientVersion,
            int protocolVersion
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uid", uid == null || uid < 0 ? 0 : uid);
        body.put("roomid", roomId);
        body.put("protover", normalizeProtocolVersion(protocolVersion));
        body.put("platform", "web");
        body.put("type", 2);
        body.put("key", token);
        body.put("buvid", buvid == null ? "" : buvid);
        body.put("clientver", clientVersion == null || clientVersion.isBlank() ? "1.14.3" : clientVersion);
        try {
            return packet(OP_AUTH, objectMapper.writeValueAsBytes(body));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to build Bilibili danmaku auth packet", exception);
        }
    }

    public byte[] heartbeatPacket() {
        return packet(OP_HEARTBEAT, "[object Object]".getBytes(StandardCharsets.UTF_8));
    }

    public List<ParsedPacket> parse(byte[] bytes) {
        List<ParsedPacket> packets = new ArrayList<>();
        parseInto(bytes, packets);
        return packets;
    }

    private byte[] packet(int operation, byte[] body) {
        byte[] safeBody = body == null ? new byte[0] : body;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + safeBody.length).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(HEADER_LENGTH + safeBody.length);
        buffer.putShort((short) HEADER_LENGTH);
        buffer.putShort((short) 1);
        buffer.putInt(operation);
        buffer.putInt(1);
        buffer.put(safeBody);
        return buffer.array();
    }

    private void parseInto(byte[] bytes, List<ParsedPacket> packets) {
        if (bytes == null || bytes.length < HEADER_LENGTH) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        while (buffer.remaining() >= HEADER_LENGTH) {
            int start = buffer.position();
            int packetLength = buffer.getInt();
            int headerLength = Short.toUnsignedInt(buffer.getShort());
            int protocolVersion = Short.toUnsignedInt(buffer.getShort());
            int operation = buffer.getInt();
            int sequence = buffer.getInt();
            if (packetLength < headerLength || headerLength < HEADER_LENGTH || packetLength - HEADER_LENGTH > bytes.length) {
                break;
            }
            int bodyLength = packetLength - headerLength;
            if (buffer.remaining() < packetLength - HEADER_LENGTH) {
                break;
            }
            int skipHeaderExt = headerLength - HEADER_LENGTH;
            if (skipHeaderExt > 0 && buffer.remaining() >= skipHeaderExt) {
                buffer.position(buffer.position() + skipHeaderExt);
            }
            byte[] body = new byte[Math.max(0, bodyLength)];
            if (body.length > 0) {
                buffer.get(body);
            }
            handlePacket(protocolVersion, operation, sequence, body, packets);
            buffer.position(Math.min(start + packetLength, bytes.length));
        }
    }

    private void handlePacket(int protocolVersion, int operation, int sequence, byte[] body, List<ParsedPacket> packets) {
        if (operation == OP_COMMAND && protocolVersion == 2) {
            handleCompressedCommand(protocolVersion, operation, sequence, inflateZlib(body), packets);
            return;
        }
        if (operation == OP_COMMAND && protocolVersion == 3) {
            handleCompressedCommand(protocolVersion, operation, sequence, inflateBrotli(body), packets);
            return;
        }
        Long popularity = null;
        if (operation == OP_HEARTBEAT_REPLY && body.length >= 4) {
            popularity = Integer.toUnsignedLong(ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN).getInt());
        }
        String text = body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);
        packets.add(new ParsedPacket(protocolVersion, operation, sequence, text, popularity));
    }

    private void handleCompressedCommand(
            int protocolVersion,
            int operation,
            int sequence,
            byte[] inflated,
            List<ParsedPacket> packets
    ) {
        if (inflated.length >= HEADER_LENGTH) {
            parseInto(inflated, packets);
        } else if (inflated.length > 0) {
            packets.add(new ParsedPacket(protocolVersion, operation, sequence,
                    new String(inflated, StandardCharsets.UTF_8), null));
        }
    }

    private byte[] inflateZlib(byte[] body) {
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(body));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        } catch (IOException exception) {
            return new byte[0];
        }
    }

    private byte[] inflateBrotli(byte[] body) {
        try (BrotliInputStream input = new BrotliInputStream(new ByteArrayInputStream(body));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return output.toByteArray();
        } catch (IOException exception) {
            return new byte[0];
        }
    }

    private int normalizeProtocolVersion(int protocolVersion) {
        return protocolVersion >= 0 && protocolVersion <= 3 ? protocolVersion : 3;
    }

    public record ParsedPacket(
            int protocolVersion,
            int operation,
            int sequence,
            String bodyText,
            Long popularity
    ) {
    }
}
