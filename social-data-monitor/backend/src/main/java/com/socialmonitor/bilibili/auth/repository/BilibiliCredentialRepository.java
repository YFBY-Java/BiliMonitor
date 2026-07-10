package com.socialmonitor.bilibili.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.bilibili.auth.domain.BilibiliAccount;
import com.socialmonitor.bilibili.auth.domain.BilibiliAuthConstants;
import com.socialmonitor.bilibili.auth.domain.BilibiliCookie;
import com.socialmonitor.bilibili.auth.domain.BilibiliCookieState;
import com.socialmonitor.bilibili.auth.domain.PersistedBilibiliCredential;
import com.socialmonitor.bilibili.auth.service.BilibiliCredentialCipher;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.bilibili.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BilibiliCredentialRepository {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BilibiliCredentialCipher cipher;

    public BilibiliCredentialRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            BilibiliCredentialCipher cipher
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.cipher = cipher;
    }

    @Transactional
    public PersistedBilibiliCredential saveActive(BilibiliCookieState state) {
        Long platformId = platformId();
        jdbcTemplate.update("""
                UPDATE platform_credential SET status = 'REVOKED', updated_at = now()
                WHERE platform_id = :platformId
                  AND auth_type = :authType
                  AND status = 'ACTIVE'
                """, Map.of("platformId", platformId, "authType", BilibiliAuthConstants.AUTH_TYPE));

        Map<String, Object> payload = toPlainPayload(state, OffsetDateTime.now());
        Map<String, Object> encrypted = cipher.encrypt(payload);
        PersistedBilibiliCredential credential = jdbcTemplate.queryForObject("""
                INSERT INTO platform_credential (
                    platform_id, auth_type, encrypted_payload, expires_at, risk_level, status, updated_at
                )
                VALUES (
                    :platformId, :authType, CAST(:payload AS jsonb), :expiresAt, 'LOW', 'ACTIVE', now()
                )
                RETURNING *
                """, new MapSqlParameterSource()
                .addValue("platformId", platformId)
                .addValue("authType", BilibiliAuthConstants.AUTH_TYPE)
                .addValue("payload", toJson(encrypted))
                .addValue("expiresAt", state.expiresAt()), this::mapCredential);
        upsertAccount(platformId, credential.credentialId(), state.account());
        return credential;
    }

    public Optional<PersistedBilibiliCredential> findActive() {
        String sql = """
                SELECT *
                FROM platform_credential
                WHERE platform_id = :platformId
                  AND auth_type = :authType
                  AND status = 'ACTIVE'
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, Map.of("platformId", platformId(), "authType", BilibiliAuthConstants.AUTH_TYPE), this::mapCredential)
                .stream()
                .findFirst();
    }

    public void updateState(Long credentialId, BilibiliCookieState state, String status) {
        Map<String, Object> payload = toPlainPayload(state, null);
        Map<String, Object> encrypted = cipher.encrypt(payload);
        jdbcTemplate.update("""
                UPDATE platform_credential SET
                    encrypted_payload = CAST(:payload AS jsonb),
                    expires_at = :expiresAt,
                    status = :status,
                    updated_at = now()
                WHERE id = :credentialId
                """, new MapSqlParameterSource()
                .addValue("credentialId", credentialId)
                .addValue("payload", toJson(encrypted))
                .addValue("expiresAt", state.expiresAt())
                .addValue("status", status));
        upsertAccount(platformId(), credentialId, state.account());
    }

    public void markStatus(Long credentialId, String status) {
        jdbcTemplate.update("""
                UPDATE platform_credential SET status = :status, updated_at = now()
                WHERE id = :credentialId
                """, Map.of("credentialId", credentialId, "status", status));
    }

    public void revokeActive() {
        jdbcTemplate.update("""
                UPDATE platform_credential SET status = 'REVOKED', updated_at = now()
                WHERE platform_id = :platformId
                  AND auth_type = :authType
                  AND status = 'ACTIVE'
                """, Map.of("platformId", platformId(), "authType", BilibiliAuthConstants.AUTH_TYPE));
        jdbcTemplate.update("""
                UPDATE platform_account SET credential_id = NULL, status = 'REVOKED', updated_at = now()
                WHERE platform_id = :platformId
                  AND credential_id IS NOT NULL
                """, Map.of("platformId", platformId()));
    }

    private PersistedBilibiliCredential mapCredential(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> payload = cipher.decrypt(rs.getString("encrypted_payload"));
        return new PersistedBilibiliCredential(
                rs.getLong("id"),
                rs.getLong("platform_id"),
                rs.getString("status"),
                fromPlainPayload(payload),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private Long platformId() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM platform WHERE code = :code",
                Map.of("code", BilibiliAuthConstants.PLATFORM_CODE),
                Long.class
        );
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbcTemplate.queryForObject("""
                INSERT INTO platform (code, name, status, updated_at)
                VALUES ('bilibili', 'Bilibili', 'ACTIVE', now())
                RETURNING id
                """, Map.of(), Long.class);
    }

    private void upsertAccount(Long platformId, Long credentialId, BilibiliAccount account) {
        if (account == null || account.mid() == null) {
            return;
        }
        Map<String, Object> extension = new LinkedHashMap<>();
        extension.put("face", account.face());
        extension.put("level", account.level());
        extension.put("vipStatus", account.vipStatus());
        jdbcTemplate.update("""
                INSERT INTO platform_account (
                    platform_id, credential_id, external_account_id, display_name, profile_url,
                    status, last_sync_at, extension_json, updated_at
                )
                VALUES (
                    :platformId, :credentialId, :externalAccountId, :displayName, :profileUrl,
                    'ACTIVE', now(), CAST(:extension AS jsonb), now()
                )
                ON CONFLICT (platform_id, external_account_id) DO UPDATE SET
                    credential_id = EXCLUDED.credential_id,
                    display_name = EXCLUDED.display_name,
                    profile_url = EXCLUDED.profile_url,
                    status = 'ACTIVE',
                    last_sync_at = now(),
                    extension_json = EXCLUDED.extension_json,
                    updated_at = now()
                """, new MapSqlParameterSource()
                .addValue("platformId", platformId)
                .addValue("credentialId", credentialId)
                .addValue("externalAccountId", String.valueOf(account.mid()))
                .addValue("displayName", account.uname())
                .addValue("profileUrl", "https://space.bilibili.com/" + account.mid())
                .addValue("extension", toJson(extension)));
    }

    public Map<String, Object> toPlainPayload(BilibiliCookieState state, OffsetDateTime createdAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", 1);
        payload.put("authType", "WEB_COOKIE");
        payload.put("source", "WEB_QRCODE");
        payload.put("cookies", state.cookies().stream().map(this::cookieToMap).toList());
        payload.put("refreshToken", state.refreshToken());
        payload.put("account", accountToMap(state.account()));
        payload.put("expiresAt", state.expiresAt() == null ? null : state.expiresAt().toString());
        payload.put("createdAt", createdAt == null ? null : createdAt.toString());
        payload.put("lastValidatedAt", state.lastValidatedAt() == null ? null : state.lastValidatedAt().toString());
        payload.put("lastRefreshCheckedAt", state.lastRefreshCheckedAt() == null ? null : state.lastRefreshCheckedAt().toString());
        payload.put("lastNav", state.lastNav() == null ? Map.of() : state.lastNav());
        return payload;
    }

    private BilibiliCookieState fromPlainPayload(Map<String, Object> payload) {
        List<BilibiliCookie> cookies = new ArrayList<>();
        Object cookieValue = payload.get("cookies");
        if (cookieValue instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    cookies.add(cookieFromMap(map));
                }
            }
        }
        return new BilibiliCookieState(
                cookies,
                string(payload.get("refreshToken")),
                accountFromMap(payload.get("account")),
                dateTime(payload.get("expiresAt")),
                dateTime(payload.get("lastValidatedAt")),
                dateTime(payload.get("lastRefreshCheckedAt")),
                map(payload.get("lastNav"))
        );
    }

    private Map<String, Object> cookieToMap(BilibiliCookie cookie) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", cookie.name());
        value.put("value", cookie.value());
        value.put("domain", cookie.domain());
        value.put("path", cookie.path());
        value.put("expiresAt", cookie.expiresAt() == null ? null : cookie.expiresAt().toString());
        value.put("httpOnly", cookie.httpOnly());
        value.put("secure", cookie.secure());
        value.put("sameSite", cookie.sameSite());
        return value;
    }

    private BilibiliCookie cookieFromMap(Map<?, ?> map) {
        return new BilibiliCookie(
                string(map.get("name")),
                string(map.get("value")),
                string(map.get("domain")),
                string(map.get("path")),
                dateTime(map.get("expiresAt")),
                bool(map.get("httpOnly")),
                bool(map.get("secure")),
                string(map.get("sameSite"))
        );
    }

    private Map<String, Object> accountToMap(BilibiliAccount account) {
        if (account == null) {
            return Map.of();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("mid", account.mid());
        value.put("uname", account.uname());
        value.put("face", account.face());
        value.put("level", account.level());
        value.put("vipStatus", account.vipStatus());
        return value;
    }

    private BilibiliAccount accountFromMap(Object value) {
        Map<String, Object> map = map(value);
        if (map.isEmpty()) {
            return null;
        }
        return new BilibiliAccount(
                longValue(map.get("mid")),
                string(map.get("uname")),
                string(map.get("face")),
                intValue(map.get("level")),
                intValue(map.get("vipStatus"))
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Failed to serialize Bilibili credential: " + exception.getMessage());
        }
    }

    private Map<String, Object> map(Object value) {
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, OBJECT_MAP);
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private OffsetDateTime dateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private boolean bool(Object value) {
        return value instanceof Boolean b && b;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
