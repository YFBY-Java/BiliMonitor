package com.socialmonitor.bilibili.live.danmaku.parser;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** SEND_GIFT_V2 data.pb: base64 protobuf, with repeated gift items at field 10.
 * Field reference: https://github.com/xfgryujk/blivedm/blob/dev/blivedm/models/pb.py
 * Only reads wire primitives; unknown fields are skipped for forward compatibility.
 */
final class BilibiliGiftV2Decoder {
    private static final int MAX_ENCODED_LENGTH = 2_000_000;

    private BilibiliGiftV2Decoder() { }

    static List<ObjectNode> decode(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("Invalid gift protobuf size");
        }
        ObjectNode sender = JsonNodeFactory.instance.objectNode();
        List<ObjectNode> gifts = new ArrayList<>();
        for (Field field : fields(Base64.getDecoder().decode(encoded))) {
            if (field.number == 1) sender.put("uid", field.integer());
            else if (field.number == 2) sender.put("uname", field.text());
            else if (field.number == 8) {
                for (Field medal : fields(field.bytes())) {
                    if (medal.number == 6) sender.putObject("medal_info").put("medal_name", medal.text());
                }
            } else if (field.number == 10) gifts.add(gift(field.bytes()));
        }
        if (gifts.isEmpty()) throw new IllegalArgumentException("Empty gift protobuf list");
        for (ObjectNode gift : gifts) gift.setAll(sender);
        return gifts;
    }

    private static ObjectNode gift(byte[] bytes) {
        ObjectNode gift = JsonNodeFactory.instance.objectNode();
        for (Field field : fields(bytes)) {
            switch (field.number) {
                case 1 -> gift.put("giftId", field.integer());
                case 2 -> gift.put("giftName", field.text());
                case 3 -> gift.put("num", field.integer());
                case 5 -> gift.put("price", field.integer());
                case 7 -> gift.put("total_coin", field.integer());
                case 8 -> gift.put("coin_type", field.text());
                case 9 -> gift.put("tid", field.text());
                case 10 -> gift.put("timestamp", field.integer());
                case 12 -> gift.put("rnd", field.text());
                default -> { }
            }
        }
        if (gift.path("giftId").asLong() <= 0 || gift.path("num").asLong() <= 0
                || gift.path("num").asLong() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid gift item identity or quantity");
        }
        return gift;
    }

    private static List<Field> fields(byte[] bytes) {
        ByteBuffer input = ByteBuffer.wrap(bytes);
        List<Field> result = new ArrayList<>();
        while (input.hasRemaining()) {
            long tag = varint(input);
            long number = tag >>> 3;
            if (number == 0 || number > 0x1fffffff) throw new IllegalArgumentException("Invalid protobuf tag");
            int wire = (int) (tag & 7);
            switch (wire) {
                case 0 -> result.add(new Field((int) number, wire, varint(input), null));
                case 1 -> skip(input, 8);
                case 2 -> {
                    long length = varint(input);
                    if (length < 0 || length > input.remaining()) throw new IllegalArgumentException("Truncated protobuf");
                    byte[] value = new byte[(int) length];
                    input.get(value);
                    result.add(new Field((int) number, wire, 0, value));
                }
                case 5 -> skip(input, 4);
                default -> throw new IllegalArgumentException("Unsupported protobuf wire type");
            }
        }
        return result;
    }

    private static void skip(ByteBuffer input, int count) {
        if (input.remaining() < count) throw new IllegalArgumentException("Truncated protobuf");
        input.position(input.position() + count);
    }

    private static long varint(ByteBuffer input) {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (!input.hasRemaining()) throw new IllegalArgumentException("Truncated varint");
            int next = Byte.toUnsignedInt(input.get());
            if (shift == 63 && (next & 0xfe) != 0) throw new IllegalArgumentException("Varint overflow");
            value |= (long) (next & 0x7f) << shift;
            if ((next & 0x80) == 0) return value;
        }
        throw new IllegalArgumentException("Invalid varint");
    }

    private record Field(int number, int wire, long value, byte[] payload) {
        long integer() {
            if (wire != 0 || value < 0) throw new IllegalArgumentException("Invalid unsigned gift integer");
            return value;
        }
        byte[] bytes() {
            if (wire != 2) throw new IllegalArgumentException("Invalid gift bytes");
            return payload;
        }
        String text() { return new String(bytes(), StandardCharsets.UTF_8); }
    }
}
