package com.socialmonitor.douyin.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.douyin.auth", name = "enabled", havingValue = "true")
public class DouyinCredentialCipher {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final DouyinAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private byte[] keyBytes;

    public DouyinCredentialCipher(DouyinAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initializeKey() {
        String configuredKey = properties.credentialEncryptionKey();
        if (configuredKey != null && !configuredKey.isBlank()) {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(configuredKey.trim());
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY must be valid Base64."
                );
            }
            if (decoded.length != 32) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY must decode to 32 bytes."
                );
            }
            keyBytes = decoded;
            return;
        }
        throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY must be configured when Douyin auth is enabled."
        );
    }

    public Map<String, Object> encrypt(Map<String, Object> plainPayload) {
        requireInitialized();
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(objectMapper.writeValueAsBytes(plainPayload));
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("alg", "AES-256-GCM");
            envelope.put("kid", "env:SOCIAL_MONITOR_DOUYIN_CREDENTIAL_ENCRYPTION_KEY");
            envelope.put("iv", Base64.getEncoder().encodeToString(iv));
            envelope.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext));
            return envelope;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Failed to encrypt Douyin credential: " + exception.getMessage());
        }
    }

    public Map<String, Object> decrypt(String encryptedPayloadJson) {
        requireInitialized();
        try {
            Map<String, Object> envelope = objectMapper.readValue(encryptedPayloadJson, OBJECT_MAP);
            byte[] iv = Base64.getDecoder().decode(String.valueOf(envelope.get("iv")));
            byte[] ciphertext = Base64.getDecoder().decode(String.valueOf(envelope.get("ciphertext")));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return objectMapper.readValue(plain, OBJECT_MAP);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Failed to decrypt Douyin credential: " + exception.getMessage());
        }
    }

    private void requireInitialized() {
        if (keyBytes == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Douyin credential cipher is not initialized.");
        }
    }
}
