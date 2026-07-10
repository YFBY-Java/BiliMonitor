package com.socialmonitor.bilibili.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.auth.config.BilibiliAuthProperties;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BilibiliCredentialCipher {

    private static final Logger log = LoggerFactory.getLogger(BilibiliCredentialCipher.class);
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final BilibiliAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private byte[] keyBytes;

    public BilibiliCredentialCipher(BilibiliAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initializeKey() {
        String configuredKey = properties.getCredentialEncryptionKey();
        if (configuredKey != null && !configuredKey.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(configuredKey.trim());
            if (decoded.length != 32) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY must decode to 32 bytes.");
            }
            keyBytes = decoded;
            return;
        }
        keyBytes = new byte[32];
        secureRandom.nextBytes(keyBytes);
        log.warn("SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY is not configured. Using an in-memory key for this process; persisted credentials cannot be decrypted after restart until a fixed key is configured.");
    }

    public Map<String, Object> encrypt(Map<String, Object> plainPayload) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(objectMapper.writeValueAsBytes(plainPayload));
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("alg", "AES-256-GCM");
            envelope.put("kid", properties.getCredentialEncryptionKey() == null || properties.getCredentialEncryptionKey().isBlank()
                    ? "runtime:ephemeral"
                    : "env:SOCIAL_MONITOR_CREDENTIAL_ENCRYPTION_KEY");
            envelope.put("iv", Base64.getEncoder().encodeToString(iv));
            envelope.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext));
            return envelope;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Failed to encrypt Bilibili credential: " + exception.getMessage());
        }
    }

    public Map<String, Object> decrypt(String encryptedPayloadJson) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(encryptedPayloadJson, OBJECT_MAP);
            byte[] iv = Base64.getDecoder().decode(String.valueOf(envelope.get("iv")));
            byte[] ciphertext = Base64.getDecoder().decode(String.valueOf(envelope.get("ciphertext")));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return objectMapper.readValue(plain, OBJECT_MAP);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Failed to decrypt Bilibili credential: " + exception.getMessage());
        }
    }
}
