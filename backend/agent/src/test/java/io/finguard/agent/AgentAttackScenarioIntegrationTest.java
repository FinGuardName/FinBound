package io.finguard.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/** HTTP adapter contract tests; the stub does not implement Core scope comparisons or OPA policy. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentAttackScenarioIntegrationTest {
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicReference<String> REQUEST_BODY = new AtomicReference<>();
    private static final AtomicReference<StubResponse> STUB = new AtomicReference<>();
    private static final DisposableServer GATEWAY = HttpServer.create().port(0)
            .route(routes -> routes.post("/gateway/v1/tool-calls", (request, response) ->
                    request.receive().aggregate().asString().flatMap(body -> {
                        CALLS.incrementAndGet();
                        REQUEST_BODY.set(body);
                        StubResponse stub = STUB.get();
                        return response.status(stub.status())
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .sendString(Mono.just(stub.body())).then();
                    })))
            .bindNow();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("finguard.agent.gateway-base-url", () -> "http://localhost:" + GATEWAY.port());
        registry.add("finguard.agent.service-credential", () -> "scenario-test-agent");
        registry.add("finguard.agent.internal-credential", () -> "scenario-test-internal");
        registry.add("finguard.agent.gateway-timeout", () -> "1s");
    }

    @BeforeEach
    void reset() {
        CALLS.set(0);
        REQUEST_BODY.set(null);
        STUB.set(null);
    }

    @AfterAll
    static void closeGateway() {
        GATEWAY.disposeNow();
    }

    @ParameterizedTest
    @CsvSource({
        "NORMAL_CREDIT_SCORE,CREDIT_SCORE_READ,CREDIT_SCORE,creditScore,812",
        "NORMAL_INCOME,INCOME_READ,INCOME,annualIncome,60000000",
        "NORMAL_DEBT,DEBT_READ,DEBT,totalDebt,20000000"
    })
    void forwardsNormalToolCalls(String scenario, String tool, String data, String resultField, int value)
            throws Exception {
        STUB.set(new StubResponse(200, """
                {"requestId":"REQ-060","decision":"ALLOW",
                 "result":{"tool":"%s","consumerId":"CUST-1001","%s":%d}}
                """.formatted(tool, resultField, value)));

        simulate(scenario).expectStatus().isOk().expectBody()
                .jsonPath("$.scenario").isEqualTo(scenario)
                .jsonPath("$.gatewayResponse.decision").isEqualTo("ALLOW")
                .jsonPath("$.gatewayResponse.result." + resultField).isEqualTo(value);

        assertRequest(tool, data, "CUST-1001");
    }

    @ParameterizedTest
    @CsvSource({
        "CASE_SCOPE_ATTACK,CREDIT_SCORE_READ,CREDIT_SCORE,CUST-9999,CASE_SCOPE_VIOLATION",
        "TOOL_SCOPE_ATTACK,INCOME_READ,INCOME,CUST-1001,TOOL_SCOPE_VIOLATION",
        "DATA_SCOPE_ATTACK,CREDIT_SCORE_READ,CREDIT_SCORE|INCOME,CUST-1001,DATA_SCOPE_VIOLATION",
        "MANDATE_SCOPE_ATTACK,DEBT_READ,DEBT,CUST-1001,MANDATE_SCOPE_VIOLATION"
    })
    void preservesGatewayBlockAndReasonCodes(
            String scenario, String tool, String data, String consumer, String reason) throws Exception {
        // Configure a server response explicitly: scenario labels are not authorization inputs.
        STUB.set(new StubResponse(403, """
                {"requestId":"REQ-060","decision":"BLOCK","reasonCodes":["%s"]}
                """.formatted(reason)));

        simulate(scenario).expectStatus().isOk().expectBody()
                .jsonPath("$.scenario").isEqualTo(scenario)
                .jsonPath("$.gatewayResponse.requestId").isEqualTo("REQ-060")
                .jsonPath("$.gatewayResponse.decision").isEqualTo("BLOCK")
                .jsonPath("$.gatewayResponse.reasonCodes.length()").isEqualTo(1)
                .jsonPath("$.gatewayResponse.reasonCodes[0]").isEqualTo(reason)
                .jsonPath("$.gatewayResponse.result").doesNotExist()
                .jsonPath("$.errorCode").doesNotExist();

        assertRequest(tool, data, consumer);
    }

    @ParameterizedTest
    @CsvSource({
        "DATA_SCOPE_VIOLATION|MANDATE_SCOPE_VIOLATION|TOOL_SCOPE_VIOLATION",
        "MANDATE_SCOPE_VIOLATION|DATA_SCOPE_VIOLATION|MANDATE_SCOPE_VIOLATION",
        "MANDATE_SCOPE_VIOLATION|TASK_PASSPORT_INACTIVE"
    })
    void preservesCompositeReasonsWithoutSortingOrDeduplication(String reasons) throws Exception {
        // Transport fixtures, not evidence that Core produces these scope combinations.
        ObjectMapper mapper = new ObjectMapper();
        String[] expected = reasons.split("\\|");
        STUB.set(new StubResponse(403, """
                {"requestId":"REQ-060","decision":"BLOCK","reasonCodes":%s}
                """.formatted(mapper.writeValueAsString(expected))));

        byte[] responseBody = simulate("MANDATE_SCOPE_ATTACK").expectStatus().isOk().expectBody()
                .jsonPath("$.gatewayResponse.decision").isEqualTo("BLOCK")
                .jsonPath("$.errorCode").doesNotExist()
                .returnResult().getResponseBody();
        assertThat(mapper.readTree(responseBody).path("gatewayResponse").path("reasonCodes"))
                .isEqualTo(mapper.valueToTree(expected));
        assertRequest("DEBT_READ", "DEBT", "CUST-1001");
    }

    @ParameterizedTest
    @CsvSource({
        "NORMAL_INCOME", "NORMAL_DEBT", "TOOL_SCOPE_ATTACK", "DATA_SCOPE_ATTACK", "MANDATE_SCOPE_ATTACK"
    })
    void gatewaySchemaRejectionIsAnErrorNotAScopeBlock(String scenario) {
        STUB.set(new StubResponse(400, "{\"errorCode\":\"INVALID_TOOL_REQUEST\"}"));

        simulate(scenario).expectStatus().isEqualTo(502).expectBody()
                .jsonPath("$.errorCode").isEqualTo("GATEWAY_REQUEST_FAILED")
                .jsonPath("$.gatewayResponse").doesNotExist()
                .jsonPath("$.decision").doesNotExist();
        assertThat(CALLS.get()).isEqualTo(1);
    }

    private void assertRequest(String tool, String data, String consumer) throws Exception {
        assertThat(CALLS.get()).isEqualTo(1);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode body = mapper.readTree(REQUEST_BODY.get());
        assertThat(body.size()).isEqualTo(6);
        assertThat(body.get("agentRunId").asText()).isEqualTo("RUN-CORE-060");
        assertThat(body.get("passportId").asText()).isEqualTo("PASS-CORE-060");
        assertThat(body.get("tool").asText()).isEqualTo(tool);
        assertThat(body.get("requestedData")).isEqualTo(mapper.valueToTree(data.split("\\|")));
        assertThat(body.get("targetConsumerId").asText()).isEqualTo(consumer);
        assertThat(body.get("action").asText()).isEqualTo("READ");
    }

    private WebTestClient.ResponseSpec simulate(String scenario) {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build()
                .post().uri("/internal/v1/agent-simulations")
                .header("X-FinGuard-Internal-Credential", "scenario-test-internal")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"agentRunId":"RUN-CORE-060","passportId":"PASS-CORE-060","scenario":"%s"}
                        """.formatted(scenario))
                .exchange();
    }

    private record StubResponse(int status, String body) {
    }
}
