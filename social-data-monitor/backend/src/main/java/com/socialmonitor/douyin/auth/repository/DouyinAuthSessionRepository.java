package com.socialmonitor.douyin.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.domain.DouyinAuthSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.douyin.auth", name = "enabled", havingValue = "true")
public class DouyinAuthSessionRepository {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DouyinAuthSessionRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public DouyinAuthSession create(DouyinAuthSession session) {
        jdbcTemplate.update("""
                INSERT INTO douyin_auth_session (
                    login_id, flow_type, provider_mode, worker_session_id, state, status,
                    expires_at, completed_at, error_code, error_message, raw_result_json,
                    created_at, updated_at
                )
                VALUES (
                    :loginId, :flowType, :providerMode, :workerSessionId, :state, :status,
                    :expiresAt, :completedAt, :errorCode, :errorMessage, CAST(:rawResult AS jsonb),
                    :createdAt, :updatedAt
                )
                """, parameters(session));
        return session;
    }

    public Optional<DouyinAuthSession> findByLoginId(UUID loginId) {
        return query("SELECT * FROM douyin_auth_session WHERE login_id = :loginId",
                Map.of("loginId", loginId));
    }

    public Optional<DouyinAuthSession> findByLoginIdForUpdate(UUID loginId) {
        return query("SELECT * FROM douyin_auth_session WHERE login_id = :loginId FOR UPDATE",
                Map.of("loginId", loginId));
    }

    public Optional<DouyinAuthSession> findByState(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        return query("SELECT * FROM douyin_auth_session WHERE state = :state", Map.of("state", state));
    }

    public void updateStatus(UUID loginId, String status, Map<String, Object> rawResult) {
        jdbcTemplate.update("""
                UPDATE douyin_auth_session
                SET status = :status,
                    raw_result_json = CAST(:rawResult AS jsonb),
                    error_code = NULL,
                    error_message = NULL,
                    updated_at = now()
                WHERE login_id = :loginId
                  AND status NOT IN ('SUCCESS', 'FAILED', 'EXPIRED')
                """, new MapSqlParameterSource()
                .addValue("loginId", loginId)
                .addValue("status", status)
                .addValue("rawResult", toJson(rawResult)));
    }

    public void completeSuccess(UUID loginId, Map<String, Object> rawResult) {
        jdbcTemplate.update("""
                UPDATE douyin_auth_session
                SET status = 'SUCCESS',
                    raw_result_json = CAST(:rawResult AS jsonb),
                    completed_at = now(),
                    error_code = NULL,
                    error_message = NULL,
                    updated_at = now()
                WHERE login_id = :loginId
                """, new MapSqlParameterSource()
                .addValue("loginId", loginId)
                .addValue("rawResult", toJson(rawResult)));
    }

    public void completeFailure(
            UUID loginId,
            String errorCode,
            String errorMessage,
            Map<String, Object> rawResult
    ) {
        jdbcTemplate.update("""
                UPDATE douyin_auth_session
                SET status = 'FAILED',
                    raw_result_json = CAST(:rawResult AS jsonb),
                    completed_at = now(),
                    error_code = :errorCode,
                    error_message = :errorMessage,
                    updated_at = now()
                WHERE login_id = :loginId
                  AND status <> 'SUCCESS'
                """, new MapSqlParameterSource()
                .addValue("loginId", loginId)
                .addValue("errorCode", errorCode)
                .addValue("errorMessage", errorMessage)
                .addValue("rawResult", toJson(rawResult)));
    }

    public boolean tryClaimForValidation(UUID loginId, Map<String, Object> rawResult) {
        int updated = jdbcTemplate.update("""
                UPDATE douyin_auth_session
                SET status = 'VALIDATING',
                    raw_result_json = CAST(:rawResult AS jsonb),
                    error_code = NULL,
                    error_message = NULL,
                    updated_at = now()
                WHERE login_id = :loginId
                  AND status = 'WAITING'
                """, new MapSqlParameterSource()
                .addValue("loginId", loginId)
                .addValue("rawResult", toJson(rawResult)));
        return updated == 1;
    }

    public void attachWorkerSession(UUID loginId, String workerSessionId, Map<String, Object> rawResult) {
        jdbcTemplate.update("""
                UPDATE douyin_auth_session
                SET worker_session_id = :workerSessionId,
                    raw_result_json = CAST(:rawResult AS jsonb),
                    updated_at = now()
                WHERE login_id = :loginId
                """, new MapSqlParameterSource()
                .addValue("loginId", loginId)
                .addValue("workerSessionId", workerSessionId)
                .addValue("rawResult", toJson(rawResult)));
    }

    private Optional<DouyinAuthSession> query(String sql, Map<String, ?> parameters) {
        List<DouyinAuthSession> rows = jdbcTemplate.query(sql, parameters, this::mapSession);
        return rows.stream().findFirst();
    }

    private MapSqlParameterSource parameters(DouyinAuthSession session) {
        return new MapSqlParameterSource()
                .addValue("loginId", session.loginId())
                .addValue("flowType", session.flowType())
                .addValue("providerMode", session.providerMode())
                .addValue("workerSessionId", session.workerSessionId())
                .addValue("state", session.state())
                .addValue("status", session.status())
                .addValue("expiresAt", session.expiresAt())
                .addValue("completedAt", session.completedAt())
                .addValue("errorCode", session.errorCode())
                .addValue("errorMessage", session.errorMessage())
                .addValue("rawResult", toJson(session.rawResult()))
                .addValue("createdAt", session.createdAt())
                .addValue("updatedAt", session.updatedAt());
    }

    private DouyinAuthSession mapSession(ResultSet resultSet, int rowNum) throws SQLException {
        return new DouyinAuthSession(
                resultSet.getObject("login_id", UUID.class),
                resultSet.getString("flow_type"),
                resultSet.getString("provider_mode"),
                resultSet.getString("worker_session_id"),
                resultSet.getString("state"),
                resultSet.getString("status"),
                resultSet.getObject("expires_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                fromJson(resultSet.getString("raw_result_json")),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Failed to serialize Douyin auth session: " + exception.getMessage());
        }
    }

    private Map<String, Object> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, OBJECT_MAP);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Failed to deserialize Douyin auth session: " + exception.getMessage());
        }
    }
}
