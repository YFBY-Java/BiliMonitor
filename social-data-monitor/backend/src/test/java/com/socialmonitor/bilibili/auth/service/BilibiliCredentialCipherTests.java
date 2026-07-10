package com.socialmonitor.bilibili.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.auth.config.BilibiliAuthProperties;
import com.socialmonitor.common.exception.BusinessException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BilibiliCredentialCipherTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void encryptsPayloadWithoutLeavingCredentialPlaintextInEnvelope() throws Exception {
        BilibiliAuthProperties properties = new BilibiliAuthProperties();
        properties.setCredentialEncryptionKey(Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes()));
        BilibiliCredentialCipher cipher = new BilibiliCredentialCipher(properties, objectMapper);
        cipher.initializeKey();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("SESSDATA", "full-sessdata-value");
        payload.put("bili_jct", "full-csrf-value");
        payload.put("refreshToken", "full-refresh-token-value");

        Map<String, Object> encrypted = cipher.encrypt(payload);
        String encryptedJson = objectMapper.writeValueAsString(encrypted);

        assertThat(encryptedJson).contains("AES-256-GCM");
        assertThat(encryptedJson).doesNotContain("full-sessdata-value", "full-csrf-value", "full-refresh-token-value");
        assertThat(cipher.decrypt(encryptedJson)).containsAllEntriesOf(payload);
    }

    @Test
    void rejectsConfiguredKeyThatIsNotThirtyTwoBytes() {
        BilibiliAuthProperties properties = new BilibiliAuthProperties();
        properties.setCredentialEncryptionKey(Base64.getEncoder().encodeToString("too-short".getBytes()));
        BilibiliCredentialCipher cipher = new BilibiliCredentialCipher(properties, objectMapper);

        assertThatThrownBy(cipher::initializeKey)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("32 bytes");
    }
}
