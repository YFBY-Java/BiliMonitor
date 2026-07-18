package com.socialmonitor.douyin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DouyinCredentialCipherTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripsNestedRawPayloadWithoutDroppingFields() throws Exception {
        DouyinCredentialCipher cipher = cipherWithKey(fixedKey());
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("cookies", List.of(Map.of(
                "name", "sessionid",
                "value", "raw-value",
                "httpOnly", true,
                "sameSite", "None"
        )));
        raw.put("storageState", Map.of("origins", List.of()));
        raw.put("rawWorkerResult", Map.of("unknownField", List.of(1, 2, 3)));

        Map<String, Object> envelope = cipher.encrypt(raw);
        Map<String, Object> restored = cipher.decrypt(objectMapper.writeValueAsString(envelope));

        assertThat(restored).isEqualTo(raw);
        assertThat(envelope).containsEntry("alg", "AES-256-GCM");
        assertThat(objectMapper.writeValueAsString(envelope)).doesNotContain("raw-value");
    }

    @Test
    void rejectsKeysThatAreNotExactlyThirtyTwoBytes() {
        DouyinCredentialCipher cipher = cipherWithKeyWithoutInitialization(
                Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8))
        );

        assertThatThrownBy(cipher::initializeKey)
                .hasMessageContaining("must decode to 32 bytes");
    }

    private DouyinCredentialCipher cipherWithKey(String key) {
        DouyinCredentialCipher cipher = cipherWithKeyWithoutInitialization(key);
        cipher.initializeKey();
        return cipher;
    }

    private DouyinCredentialCipher cipherWithKeyWithoutInitialization(String key) {
        return new DouyinCredentialCipher(properties(key), objectMapper);
    }

    private DouyinAuthProperties properties(String key) {
        return new DouyinAuthProperties(
                true,
                "mock",
                "",
                "",
                "",
                "user_info",
                key,
                "http://127.0.0.1:8787",
                "",
                180,
                1500,
                5000,
                30000
        );
    }

    private String fixedKey() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }
}
