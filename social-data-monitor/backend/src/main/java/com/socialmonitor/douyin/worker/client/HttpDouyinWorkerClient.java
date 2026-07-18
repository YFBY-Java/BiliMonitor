package com.socialmonitor.douyin.worker.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.common.error.ErrorCode;
import com.socialmonitor.common.exception.BusinessException;
import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import com.socialmonitor.douyin.worker.dto.WorkerConsume;
import com.socialmonitor.douyin.worker.dto.WorkerHealth;
import com.socialmonitor.douyin.worker.dto.WorkerQrImage;
import com.socialmonitor.douyin.worker.dto.WorkerSessionStart;
import com.socialmonitor.douyin.worker.dto.WorkerStatus;
import com.socialmonitor.douyin.worker.dto.WorkerValidation;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

@Component
@ConditionalOnProperty(prefix = "app.douyin.auth", name = "enabled", havingValue = "true")
public class HttpDouyinWorkerClient implements DouyinWorkerClient {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final DouyinAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI baseUri;

    public HttpDouyinWorkerClient(DouyinAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.baseUri = normalizeBaseUri(properties.workerBaseUrl());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
    }

    @Override
    public WorkerHealth health() {
        Map<String, Object> data = getJson("/internal/v1/health");
        return new WorkerHealth(string(data, "status"), data);
    }

    @Override
    public WorkerSessionStart start(int expiresInSeconds) {
        Map<String, Object> data = sendJson(
                "POST", "/internal/v1/login-sessions", Map.of("expiresInSeconds", expiresInSeconds)
        );
        return new WorkerSessionStart(
                string(data, "workerSessionId"),
                string(data, "status"),
                offsetDateTime(data.get("expiresAt")),
                data
        );
    }

    @Override
    public WorkerQrImage qr(String workerSessionId) {
        HttpResponse<byte[]> response = sendBytes(request(
                "GET", sessionPath(workerSessionId, "/qr"), HttpRequest.BodyPublishers.noBody(), false
        ));
        requireSuccess(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        String contentType = response.headers().firstValue("Content-Type").orElse("image/png");
        return new WorkerQrImage(response.body(), contentType);
    }

    @Override
    public WorkerStatus status(String workerSessionId) {
        Map<String, Object> data = getJson(sessionPath(workerSessionId, "/status"));
        return new WorkerStatus(string(data, "status"), string(data, "message"), data);
    }

    @Override
    public WorkerConsume consume(String workerSessionId) {
        Map<String, Object> data = sendJson(
                "POST", sessionPath(workerSessionId, "/consume"), Map.of()
        );
        return new WorkerConsume(requiredMap(data, "bundle"), data);
    }

    @Override
    public void delete(String workerSessionId) {
        HttpResponse<byte[]> response = sendBytes(request(
                "DELETE", sessionPath(workerSessionId, ""), HttpRequest.BodyPublishers.noBody(), false
        ));
        requireSuccess(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
    }

    @Override
    public WorkerValidation validate(Map<String, Object> bundle) {
        Map<String, Object> data = sendJson(
                "POST", "/internal/v1/web-sessions/validate", Map.of("bundle", bundle)
        );
        return new WorkerValidation(
                Boolean.TRUE.equals(data.get("valid")),
                string(data, "message"),
                optionalMap(data.get("bundle")),
                data
        );
    }

    private Map<String, Object> getJson(String path) {
        HttpResponse<byte[]> response = sendBytes(request(
                "GET", path, HttpRequest.BodyPublishers.noBody(), false
        ));
        return envelopeData(response);
    }

    private Map<String, Object> sendJson(String method, String path, Object body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Failed to serialize Douyin Worker request: " + exception.getMessage());
        }
        HttpResponse<byte[]> response = sendBytes(request(
                method,
                path,
                HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8),
                true
        ));
        return envelopeData(response);
    }

    private HttpRequest request(
            String method,
            String path,
            HttpRequest.BodyPublisher body,
            boolean jsonBody
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofMillis(properties.requestTimeoutMs()))
                .header("Accept", "application/json")
                .method(method, body);
        if (jsonBody) {
            builder.header("Content-Type", "application/json; charset=utf-8");
        }
        if (properties.workerToken() != null && !properties.workerToken().isBlank()) {
            builder.header("X-Worker-Token", properties.workerToken());
        }
        return builder.build();
    }

    private HttpResponse<byte[]> sendBytes(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Douyin Worker request was interrupted.");
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Douyin Worker request failed: " + exception.getMessage());
        }
    }

    private Map<String, Object> envelopeData(HttpResponse<byte[]> response) {
        String body = new String(response.body(), StandardCharsets.UTF_8);
        requireSuccess(response.statusCode(), body);
        Map<String, Object> envelope;
        try {
            envelope = objectMapper.readValue(body, OBJECT_MAP);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Douyin Worker returned invalid JSON: " + body);
        }
        if (!Boolean.TRUE.equals(envelope.get("success"))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Douyin Worker rejected the request: " + body);
        }
        return requiredMap(envelope, "data");
    }

    private void requireSuccess(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Douyin Worker HTTP " + statusCode + ": " + body);
        }
    }

    private String sessionPath(String workerSessionId, String suffix) {
        if (workerSessionId == null || workerSessionId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Douyin Worker session id is required.");
        }
        return "/internal/v1/login-sessions/"
                + UriUtils.encodePathSegment(workerSessionId, StandardCharsets.UTF_8)
                + suffix;
    }

    private String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private OffsetDateTime offsetDateTime(Object value) {
        return value == null ? null : OffsetDateTime.parse(String.valueOf(value));
    }

    private Map<String, Object> requiredMap(Map<String, Object> values, String key) {
        Map<String, Object> result = optionalMap(values.get(key));
        if (result == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Douyin Worker response is missing object field: " + key);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> optionalMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private URI normalizeBaseUri(String value) {
        try {
            String normalized = value.endsWith("/") ? value : value + "/";
            URI uri = URI.create(normalized);
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("URI must be absolute");
            }
            return uri;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Douyin Worker base URL: " + value, exception);
        }
    }
}
