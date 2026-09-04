package io.finguard.core.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import io.finguard.core.domain.PromptRiskLevel;

/**
 * {@code POST /internal/v1/risk/prompt} 어댑터. {@code docs/04-api-contract.md} §8.
 *
 * <p>{@code MockRestServiceServer} 를 쓰지 않는다. 그쪽은 빌더에 mock 요청 팩토리를 심는데
 * 이 클라이언트는 생성자에서 타임아웃을 걸려고 {@code requestFactory} 를 덮어쓴다. 그러면 요청이
 * mock 에 닿지 않고 검증이 통째로 무의미해진다. 타임아웃은 빼면 안 되는 설정이므로 실제 스텁
 * 서버를 띄워 <strong>나가는 요청을 그대로</strong> 본다.
 */
class PromptRiskHttpClientTest {

    private static final String HASH = "sha256:" + "a".repeat(64);

    private HttpServer server;
    private PromptRiskHttpClient client;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> credentialHeader = new AtomicReference<>();
    private final AtomicReference<StubResponse> stub = new AtomicReference<>();

    private record StubResponse(int status, String body) {
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/internal/v1/risk/prompt",
                exchange -> {
                    calls.incrementAndGet();
                    credentialHeader.set(
                            exchange.getRequestHeaders()
                                    .getFirst(PromptRiskProperties.SERVICE_CREDENTIAL_HEADER));
                    try (InputStream in = exchange.getRequestBody()) {
                        requestBody.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                    StubResponse response = stub.get();
                    byte[] payload = response.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(response.status(), payload.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(payload);
                    }
                });
        server.start();

        client =
                new PromptRiskHttpClient(
                        RestClient.builder(),
                        new PromptRiskProperties(
                                "http://127.0.0.1:" + server.getAddress().getPort(),
                                "test-service-credential",
                                1000,
                                3000));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String okBody(String detected, String risk, String level) {
        return """
                {"detected": %s, "promptRisk": %s, "riskLevel": "%s",
                 "attackType": null, "matchedRules": [],
                 "inputHash": "%s", "modelVersion": "%s"}
                """
                .formatted(detected, risk, level, HASH, PromptRiskModel.CURRENT_VERSION);
    }

    @Test
    void sendsTheContractBodyWithTheServiceCredentialHeader() throws Exception {
        stub.set(new StubResponse(200, okBody("false", "0.05", "LOW")));

        Optional<PromptRiskEvaluation> result =
                client.evaluate("RUN-001", "INPUT-001", "대출심사를 진행해줘.", HASH);

        assertThat(result).isPresent();
        assertThat(result.get().riskLevel()).isEqualTo(PromptRiskLevel.LOW);
        assertThat(calls.get()).isEqualTo(1);
        // ai-risk 는 X-FinGuard-Service-Credential 을 받는다. Agent 의 Internal 헤더와 다르다.
        assertThat(credentialHeader.get()).isEqualTo("test-service-credential");

        JsonNode body = new ObjectMapper().readTree(requestBody.get());
        assertThat(body.get("agentRunId").asText()).isEqualTo("RUN-001");
        assertThat(body.get("inputRef").asText()).isEqualTo("INPUT-001");
        assertThat(body.get("inputText").asText()).isEqualTo("대출심사를 진행해줘.");
        assertThat(body.get("inputHash").asText()).isEqualTo(HASH);
        // contentLanguage 를 지어내지 않는다 — docs/04 §8.
        assertThat(body.has("contentLanguage")).isFalse();
    }

    @Test
    void returnsEmptyWhenTheServiceFails() {
        // 실패는 예외가 아니다. 호출부는 NOT_EVALUATED 로 남기고 실행을 계속한다.
        stub.set(new StubResponse(503, "{\"detail\": \"PROMPT_RISK_UNAVAILABLE\"}"));

        assertThat(client.evaluate("RUN-001", "INPUT-001", "text", HASH)).isEmpty();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void returnsEmptyWhenTheResponseFailsValidation() {
        // detected=true 인데 ALERT 다 — docs/04:391 위반. 통과시키면 거짓 기록이 된다.
        stub.set(new StubResponse(200, okBody("true", "0.5", "ALERT")));

        assertThat(client.evaluate("RUN-001", "INPUT-001", "text", HASH)).isEmpty();
        assertThat(calls.get()).isEqualTo(1);
    }
}
