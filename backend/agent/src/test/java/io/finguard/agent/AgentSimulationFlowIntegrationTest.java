package io.finguard.agent;

import static io.finguard.agent.security.InternalCredentialWebFilter.INTERNAL_CREDENTIAL_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentSimulationFlowIntegrationTest {
    private static final AtomicReference<CapturedGatewayRequest> CAPTURED_REQUEST =
            new AtomicReference<>();
    private static final DisposableServer MOCK_GATEWAY = createMockGateway();

    @LocalServerPort
    private int agentPort;

    @DynamicPropertySource
    static void configureAgent(DynamicPropertyRegistry registry) {
        registry.add(
                "finguard.agent.gateway-base-url",
                () -> "http://localhost:" + MOCK_GATEWAY.port()
        );
        registry.add(
                "finguard.agent.service-credential",
                () -> "test-agent-service-credential"
        );
        registry.add(
                "finguard.agent.internal-credential",
                () -> "test-internal-credential"
        );
        registry.add("finguard.agent.gateway-timeout", () -> "1s");
    }

    @AfterAll
    static void stopMockGateway() {
        MOCK_GATEWAY.disposeNow();
    }

    @Test
    void executesNormalScenarioThroughRealHttpClient() {
        WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + agentPort)
                .build()
                .post()
                .uri("/internal/v1/agent-simulations")
                .header(INTERNAL_CREDENTIAL_HEADER, "test-internal-credential")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "agentRunId": "RUN-CORE-001",
                          "passportId": "PASS-CORE-001",
                          "scenario": "NORMAL_CREDIT_SCORE"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.scenario").isEqualTo("NORMAL_CREDIT_SCORE")
                .jsonPath("$.gatewayResponse.decision").isEqualTo("ALLOW")
                .jsonPath("$.gatewayResponse.result.creditScore").isEqualTo(812);

        CapturedGatewayRequest captured = CAPTURED_REQUEST.get();
        assertThat(captured.authorization()).isEqualTo("Bearer test-agent-service-credential");
        assertThat(captured.requestId()).isNotBlank();
        assertThat(captured.traceparent()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        assertThat(captured.body()).contains(
                "\"agentRunId\":\"RUN-CORE-001\"",
                "\"passportId\":\"PASS-CORE-001\"",
                "\"targetConsumerId\":\"CUST-1001\"",
                "\"tool\":\"CREDIT_SCORE_READ\"",
                "\"requestedData\":[\"CREDIT_SCORE\"]",
                "\"action\":\"READ\""
        );
        assertThat(captured.body()).doesNotContain(
                "employeeId",
                "agentId",
                "caseId",
                "allowedTools",
                "allowedData"
        );
        assertThat(captured.body()).doesNotContain(
                "inputText",
                "inputRefs",
                "prompt",
                "documentText"
        );
    }

    private static DisposableServer createMockGateway() {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/gateway/v1/tool-calls", (request, response) ->
                        request.receive().aggregate().asString().flatMap(body -> {
                            CAPTURED_REQUEST.set(new CapturedGatewayRequest(
                                    request.requestHeaders().get(HttpHeaders.AUTHORIZATION),
                                    request.requestHeaders().get("X-Request-Id"),
                                    request.requestHeaders().get("Traceparent"),
                                    body
                            ));
                            response.status(200);
                            response.header(
                                    HttpHeaders.CONTENT_TYPE,
                                    MediaType.APPLICATION_JSON_VALUE
                            );
                            return response.sendString(Mono.just("""
                                    {
                                      "requestId": "REQ-001",
                                      "decision": "ALLOW",
                                      "result": {
                                        "creditScore": 812
                                      }
                                    }
                                    """)).then();
                        })))
                .bindNow();
    }

    private record CapturedGatewayRequest(
            String authorization,
            String requestId,
            String traceparent,
            String body
    ) {
    }
}
