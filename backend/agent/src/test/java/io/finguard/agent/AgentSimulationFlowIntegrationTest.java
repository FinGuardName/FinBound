package io.finguard.agent;

import static io.finguard.agent.security.InternalCredentialWebFilter.INTERNAL_CREDENTIAL_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private static final AtomicInteger GATEWAY_CALLS = new AtomicInteger();
    private static final AtomicInteger GATEWAY_STATUS = new AtomicInteger(200);
    private static final AtomicReference<String> GATEWAY_BODY = new AtomicReference<>();
    private static final AtomicReference<Duration> GATEWAY_DELAY =
            new AtomicReference<>(Duration.ZERO);
    private static final DisposableServer MOCK_GATEWAY = createMockGateway();

    @LocalServerPort
    private int agentPort;

    @BeforeEach
    void resetGateway() {
        CAPTURED_REQUEST.set(null);
        GATEWAY_CALLS.set(0);
        GATEWAY_STATUS.set(200);
        GATEWAY_DELAY.set(Duration.ZERO);
        GATEWAY_BODY.set("""
                {"requestId":"REQ-001","decision":"ALLOW",
                 "result":{"tool":"CREDIT_SCORE_READ","consumerId":"CUST-1001","creditScore":812}}
                """);
    }

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
        assertThat(GATEWAY_CALLS.get()).isEqualTo(1);
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
                "allowedData",
                "allowedActions"
        );
        assertThat(captured.body()).doesNotContain(
                "inputText",
                "inputRefs",
                "prompt",
                "documentText"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"passportId\":\"PASS-CORE-001\",\"scenario\":\"NORMAL_CREDIT_SCORE\"}",
        "{\"agentRunId\":\"RUN-CORE-001\",\"scenario\":\"NORMAL_CREDIT_SCORE\"}",
        "{\"agentRunId\":null,\"passportId\":\"PASS-CORE-001\",\"scenario\":\"NORMAL_CREDIT_SCORE\"}",
        "{\"agentRunId\":\" \",\"passportId\":\"PASS-CORE-001\",\"scenario\":\"NORMAL_CREDIT_SCORE\"}",
        "{\"agentRunId\":\"RUN-CORE-001\",\"passportId\":\" \",\"scenario\":\"NORMAL_CREDIT_SCORE\"}"
    })
    void missingOrBlankReferencesNeverReachGateway(String body) {
        simulate(body).expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errorCode")
                .isEqualTo("INVALID_AGENT_SIMULATION_REQUEST");

        assertThat(GATEWAY_CALLS.get()).isZero();
        assertThat(CAPTURED_REQUEST.get()).isNull();
    }

    @Test
    void forwardsUnknownReferencesAndPreservesGatewayRejection() {
        // This stub models a real Core rejection. It does not prove that MockCoreClient checks issuance.
        GATEWAY_STATUS.set(403);
        GATEWAY_BODY.set("""
                {"requestId":"REQ-001","decision":"BLOCK","reasonCodes":["CONTEXT_NOT_FOUND"]}
                """);

        simulate("""
                {"agentRunId":"RUN-NOT-ISSUED","passportId":"PASS-NOT-ISSUED",
                 "scenario":"NORMAL_CREDIT_SCORE"}
                """).expectStatus().isOk().expectBody()
                .jsonPath("$.gatewayResponse.decision").isEqualTo("BLOCK")
                .jsonPath("$.gatewayResponse.reasonCodes[0]").isEqualTo("CONTEXT_NOT_FOUND")
                .jsonPath("$.gatewayResponse.result").doesNotExist();

        assertThat(GATEWAY_CALLS.get()).isEqualTo(1);
        assertThat(CAPTURED_REQUEST.get().body()).contains("RUN-NOT-ISSUED", "PASS-NOT-ISSUED");
    }

    @Test
    void rejectsStubAcknowledgementWithoutFinancialData() {
        GATEWAY_BODY.set("""
                {"requestId":"REQ-001","decision":"ALLOW","result":{"tool":"CREDIT_SCORE_READ"}}
                """);

        assertGatewayError("GATEWAY_RESPONSE_INVALID");
    }

    @Test
    void preservesBlockWithoutReleasingFinancialResult() {
        GATEWAY_STATUS.set(403);
        GATEWAY_BODY.set("""
                {"requestId":"REQ-001","decision":"BLOCK","reasonCodes":["CASE_SCOPE_VIOLATION"]}
                """);

        simulate(validRequest("CASE_SCOPE_ATTACK")).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.gatewayResponse.decision").isEqualTo("BLOCK")
                .jsonPath("$.gatewayResponse.result").doesNotExist();

        assertThat(GATEWAY_CALLS.get()).isEqualTo(1);
        assertThat(CAPTURED_REQUEST.get().body()).contains("\"targetConsumerId\":\"CUST-9999\"");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 500, 503})
    void gatewayHttpErrorsAreNotPolicyDecisions(int status) {
        GATEWAY_STATUS.set(status);
        GATEWAY_BODY.set("{\"message\":\"sensitive-upstream-detail\"}");

        assertGatewayError("GATEWAY_REQUEST_FAILED");
    }

    @Test
    void malformedGatewayResponseFailsClosed() {
        GATEWAY_BODY.set("{\"decision\":\"ALLOW\"}");

        assertGatewayError("GATEWAY_RESPONSE_INVALID");
    }

    @Test
    void gatewayTimeoutFailsWithoutRetry() {
        GATEWAY_DELAY.set(Duration.ofSeconds(2));

        assertGatewayError("GATEWAY_TIMEOUT");
    }

    private void assertGatewayError(String errorCode) {
        simulate(validRequest("NORMAL_CREDIT_SCORE")).expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo(errorCode)
                .jsonPath("$.gatewayResponse").doesNotExist()
                .jsonPath("$.decision").doesNotExist()
                .jsonPath("$.message").isEqualTo("The Gateway call could not be completed");

        assertThat(GATEWAY_CALLS.get()).isEqualTo(1);
    }

    private WebTestClient.ResponseSpec simulate(String body) {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + agentPort).build()
                .post().uri("/internal/v1/agent-simulations")
                .header(INTERNAL_CREDENTIAL_HEADER, "test-internal-credential")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body).exchange();
    }

    private String validRequest(String scenario) {
        return """
                {"agentRunId":"RUN-CORE-001","passportId":"PASS-CORE-001","scenario":"%s"}
                """.formatted(scenario);
    }

    private static DisposableServer createMockGateway() {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/gateway/v1/tool-calls", (request, response) ->
                        request.receive().aggregate().asString().flatMap(body -> {
                            GATEWAY_CALLS.incrementAndGet();
                            CAPTURED_REQUEST.set(new CapturedGatewayRequest(
                                    request.requestHeaders().get(HttpHeaders.AUTHORIZATION),
                                    request.requestHeaders().get("X-Request-Id"),
                                    request.requestHeaders().get("Traceparent"),
                                    body
                            ));
                            response.status(GATEWAY_STATUS.get());
                            response.header(
                                    HttpHeaders.CONTENT_TYPE,
                                    MediaType.APPLICATION_JSON_VALUE
                            );
                            return response.sendString(Mono.just(GATEWAY_BODY.get())
                                    .delayElement(GATEWAY_DELAY.get())).then();
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
