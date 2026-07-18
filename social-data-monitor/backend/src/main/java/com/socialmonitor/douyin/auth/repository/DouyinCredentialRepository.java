package com.socialmonitor.douyin.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.domain.DouyinAuthConstants;
import com.socialmonitor.douyin.auth.domain.DouyinStoredCredential;
import com.socialmonitor.douyin.auth.service.DouyinCredentialCipher;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.douyin.auth", name = "enabled", havingValue = "true")
public class DouyinCredentialRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DouyinCredentialCipher cipher;

    public DouyinCredentialRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            DouyinCredentialCipher cipher
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.cipher = cipher;
    }

    @Transactional
    public DouyinStoredCredential saveActive(
            String authType,
            Map<String, Object> plainPayload,
            OffsetDateTime expiresAt
    ) {
        requireSupportedAuthType(authType);
        Long platformId = platformId();
        acquireCredentialLock(platformId, authType);

        jdbcTemplate.update("""
                UPDATE platform_credential
                SET status = 'REVOKED', updated_at = now()
                WHERE platform_id = :platformId
                  AND auth_type = :authType
                  AND status = 'ACTIVE'
                """, Map.of("platformId", platformId, "authType", authType));

        Map<String, Object> encrypted = cipher.encrypt(plainPayload);
        DouyinStoredCredential stored = jdbcTemplate.queryForObject("""
                INSERT INTO platform_credential (
                    platform_id, auth_type, encrypted_payload, expires_at, risk_level, status, updated_at
                )
                VALUES (
                    :platformId, :authType, CAST(:payload AS jsonb), :expiresAt, 'HIGH', 'ACTIVE', now()
                )
                RETURNING *
                """, new MapSqlParameterSource()
                .addValue("platformId", platformId)
                .addValue("authType", authType)
                .addValue("payload", toJson(encrypted))
                .addValue("expiresAt", expiresAt), this::mapCredential);
        if (stored == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to persist Douyin credential.");
        }
        return stored;
    }

    public Optional<DouyinStoredCredential> findActive(String authType) {
        requireSupportedAuthType(authType);
        List<DouyinStoredCredential> rows = jdbcTemplate.query("""
                SELECT pc.*
                FROM platform_credential pc
                JOIN platform p ON p.id = pc.platform_id
                WHERE p.code = :platformCode
                  AND pc.auth_type = :authType
                  AND pc.status = 'ACTIVE'
                ORDER BY pc.updated_at DESC, pc.id DESC
                LIMIT 1
                """, Map.of("platformCode", DouyinAuthConstants.PLATFORM_CODE, "authType", authType), this::mapCredential);
        return rows.stream().findFirst();
    }

    public Optional<DouyinStoredCredential> findById(Long credentialId) {
        List<DouyinStoredCredential> rows = jdbcTemplate.query("""
                SELECT pc.*
                FROM platform_credential pc
                JOIN platform p ON p.id = pc.platform_id
                WHERE p.code = :platformCode
                  AND pc.id = :credentialId
                """, Map.of("platformCode", DouyinAuthConstants.PLATFORM_CODE, "credentialId", credentialId),
                this::mapCredential);
        return rows.stream().findFirst();
    }

    @Transactional
    public void revokeActive(String authType) {
        requireSupportedAuthType(authType);
        Long platformId = platformId();
        acquireCredentialLock(platformId, authType);
        jdbcTemplate.update("""
                UPDATE platform_credential
                SET status = 'REVOKED', updated_at = now()
                WHERE platform_id = :platformId
                  AND auth_type = :authType
                  AND status = 'ACTIVE'
                """, Map.of("platformId", platformId, "authType", authType));
    }

    public void markStatus(Long credentialId, String status) {
        jdbcTemplate.update("""
                UPDATE platform_credential
                SET status = :status, updated_at = now()
                WHERE id = :credentialId
                  AND platform_id = (SELECT id FROM platform WHERE code = :platformCode)
                """, Map.of(
                "credentialId", credentialId,
                "platformCode", DouyinAuthConstants.PLATFORM_CODE,
                "status", status
        ));
    }

    private void acquireCredentialLock(Long platformId, String authType) {
        String lockKey = platformId + ":" + authType;
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtext(:lockKey))",
                Map.of("lockKey", lockKey),
                resultSet -> {
                    if (resultSet.next()) {
                        resultSet.getObject(1);
                    }
                    return null;
                }
        );
    }

    private Long platformId() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM platform WHERE code = :code",
                Map.of("code", DouyinAuthConstants.PLATFORM_CODE),
                Long.class
        );
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO platform (code, name, status, updated_at)
                VALUES ('douyin', '抖音', 'ACTIVE', now())
                ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name
                RETURNING id
                """, Map.of(), Long.class);
        if (id == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Douyin platform row is unavailable.");
        }
        return id;
    }

    private DouyinStoredCredential mapCredential(ResultSet resultSet, int rowNum) throws SQLException {
        return new DouyinStoredCredential(
                resultSet.getLong("id"),
                resultSet.getLong("platform_id"),
                resultSet.getString("auth_type"),
                resultSet.getString("status"),
                cipher.decrypt(resultSet.getString("encrypted_payload")),
                resultSet.getObject("expires_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Failed to serialize Douyin credential: " + exception.getMessage());
        }
    }

    private void requireSupportedAuthType(String authType) {
        if (!DouyinAuthConstants.OAUTH_AUTH_TYPE.equals(authType)
                && !DouyinAuthConstants.WEB_AUTH_TYPE.equals(authType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported Douyin auth type: " + authType);
        }
    }
}
