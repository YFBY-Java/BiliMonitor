package com.socialmonitor.douyin.worker.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmonitor.douyin.auth.config.DouyinAuthProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpDouyinWorkerClientTests {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> requestToken = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsWorkerTokenAndPreservesUnknownJsonFields() {
        server.createContext("/internal/v1/login-sessions", exchange -> {
            captureRequest(exchange);
            json(exchange, 201, """
                    {"success":true,"data":{
                      "workerSessionId":"worker-1","status":"STARTING",
                      "expiresAt":"2026-07-18T12:03:00Z","futureWorkerField":{"raw":"keep-me"}
                    }}
                    """);
        });
        HttpDouyinWorkerClient client = client();

        var result = client.start(180);

        assertThat(requestToken.get()).isEqualTo("worker-secret");
        assertThat(requestBody.get()).contains("\"expiresInSeconds\":180");
        assertThat(result.workerSessionId()).isEqualTo("worker-1");
        assertThat(result.rawResult()).containsKey("futureWorkerField");
    }

    @Test
    void proxiesQrBytesAndContentTypeWithoutTransformation() {
        byte[] rawPng = new byte[] {0x01, 0x23, (byte) 0xFF, 0x55};
        server.createContext("/internal/v1/login-sessions/worker-1/qr", exchange -> {
            requestToken.set(exchange.getRequestHeaders().getFirst("X-Worker-Token"));
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, rawPng.length);
            exchange.getResponseBody().write(rawPng);
            exchange.close();
        });

        var result = client().qr("worker-1");

        assertThat(result.bytes()).containsExactly(rawPng);
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(requestToken.get()).isEqualTo("worker-secret");
    }

    @Test
    void returnsTheCompleteValidatedBundleFromWorker() {
        server.createContext("/internal/v1/web-sessions/validate", exchange -> {
            captureRequest(exchange);
            json(exchange, 200, """
                    {"success":true,"data":{
                      "valid":true,"message":"reusable",
                      "bundle":{"cookies":[{"name":"future_cookie","value":"raw"}],
                                "unknownBundleField":"keep-me"},
                      "details":{"unknownValidationField":"keep-me"}
                    }}
                    """);
        });
        Map<String, Object> original = Map.of("cookies", java.util.List.of(), "rawInput", "keep-input");

        var result = client().validate(original);

        assertThat(requestBody.get()).contains("keep-input");
        assertThat(result.valid()).isTrue();
        assertThat(result.bundle()).containsEntry("unknownBundleField", "keep-me");
        assertThat(result.rawResult()).containsKey("details");
    }

    private HttpDouyinWorkerClient client() {
        return new HttpDouyinWorkerClient(properties(), new ObjectMapper());
    }

    private DouyinAuthProperties properties() {
        return new DouyinAuthProperties(
                true, "disabled", "", "", "", "user_info", "",
                baseUrl, "worker-secret", 180, 1500, 2000, 5000
        );
    }

    private void captureRequest(HttpExchange exchange) throws IOException {
        requestToken.set(exchange.getRequestHeaders().getFirst("X-Worker-Token"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
