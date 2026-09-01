package io.finguard.agent;

import static io.finguard.agent.security.InternalCredentialWebFilter.INTERNAL_CREDENTIAL_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.finguard.agent.domain.AgentSimulationScenario;
import io.finguard.agent.domain.FinancialAction;
import io.finguard.agent.domain.FinancialDataType;
import io.finguard.agent.domain.FinancialTool;
import io.finguard.agent.domain.PolicyDecision;
import io.finguard.agent.gateway.GatewayCallException;
import io.finguard.agent.gateway.GatewayToolCallRequest;
import io.finguard.agent.gateway.GatewayToolCallResponse;
import io.finguard.agent.gateway.GatewayToolClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "finguard.agent.gateway-base-url=http://localhost:8081",
            "finguard.agent.service-credential=test-agent-service-credential",
            "finguard.agent.internal-credential=test-internal-credential",
            "finguard.agent.gateway-timeout=1s",
        }
)
class AgentSimulationControllerTest {
    private static final String ENDPOINT = "/internal/v1/agent-simulations";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GatewayToolClient gatewayToolClient;

    @Test
    void sendsNormalScenarioOnlyToGateway() throws Exception {
        when(gatewayToolClient.execute(any())).thenReturn(Mono.just(new GatewayToolCallResponse(
                "REQ-001",
                PolicyDecision.ALLOW,
                objectMapper.readTree("{\"creditScore\":812}"),
                List.of()
        )));

        client().post()
                .uri(ENDPOINT)
                .header(INTERNAL_CREDENTIAL_HEADER, "test-internal-credential")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(simulationRequest(AgentSimulationScenario.NORMAL_CREDIT_SCORE))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.scenario").isEqualTo("NORMAL_CREDIT_SCORE")
                .jsonPath("$.gatewayResponse.decision").isEqualTo("ALLOW")
                .jsonPath("$.gatewayResponse.result.creditScore").isEqualTo(812);

        GatewayToolCallRequest gatewayRequest = capturedGatewayRequest();
        assertThat(gatewayRequest.targetConsumerId()).isEqualTo("CUST-1001");
        assertThat(gatewayRequest.tool()).isEqualTo(FinancialTool.CREDIT_SCORE_READ);
        assertThat(gatewayRequest.requestedData()).containsExactly(FinancialDataType.CREDIT_SCORE);
        assertThat(gatewayRequest.action()).isEqualTo(FinancialAction.READ);
    }

    @Test
    void sendsCaseScopeAttackToGatewayAndReturnsBlock() {
        when(gatewayToolClient.execute(any())).thenReturn(Mono.just(new GatewayToolCallResponse(
                "REQ-002",
                PolicyDecision.BLOCK,
                null,
                List.of("CASE_SCOPE_VIOLATION")
        )));

        client().post()
                .uri(ENDPOINT)
                .header(INTERNAL_CREDENTIAL_HEADER, "test-internal-credential")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(simulationRequest(AgentSimulationScenario.CASE_SCOPE_ATTACK))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.scenario").isEqualTo("CASE_SCOPE_ATTACK")
                .jsonPath("$.gatewayResponse.decision").isEqualTo("BLOCK")
                .jsonPath("$.gatewayResponse.reasonCodes[0]")
                .isEqualTo("CASE_SCOPE_VIOLATION");

        assertThat(capturedGatewayRequest().targetConsumerId()).isEqualTo("CUST-9999");
    }

    @Test
    void rejectsMissingInternalCredentialBeforeGatewayCall() {
        client().post()
                .uri(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(simulationRequest(AgentSimulationScenario.NORMAL_CREDIT_SCORE))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INTERNAL_CREDENTIAL_INVALID");

        verifyNoInteractions(gatewayToolClient);
    }

    @Test
    void rejectsInvalidInternalCredentialBeforeGatewayCall() {
        client().post()
                .uri(ENDPOINT)
                .header(INTERNAL_CREDENTIAL_HEADER, "wrong-credential")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(simulationRequest(AgentSimulationScenario.NORMAL_CREDIT_SCORE))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INTERNAL_CREDENTIAL_INVALID");

        verifyNoInteractions(gatewayToolClient);
    }

    @Test
    void rejectsUnknownScenarioBeforeGatewayCall() {
        String body = """
                {
                  "agentRunId": "RUN-001",
                  "passportId": "PASS-001",
                  "scenario": "UNKNOWN_SCENARIO"
                }
                """;

        client().post()
                .uri(ENDPOINT)
                .header(INTERNAL_CREDENTIAL_HEADER, "test-internal-credential")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INVALID_AGENT_SIMULATION_REQUEST");

        verifyNoInteractions(gatewayToolClient);
    }

    @Test
    void exposesGatewayFailureWithoutTreatingItAsAllow() {
        when(gatewayToolClient.execute(any())).thenReturn(Mono.error(
                new GatewayCallException("GATEWAY_TIMEOUT")
        ));

        client().post()
                .uri(ENDPOINT)
                .header(INTERNAL_CREDENTIAL_HEADER, "test-internal-credential")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(simulationRequest(AgentSimulationScenario.NORMAL_CREDIT_SCORE))
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("GATEWAY_TIMEOUT");
    }

    @Test
    void allowsHealthCheckWithoutInternalCredential() {
        client().get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private GatewayToolCallRequest capturedGatewayRequest() {
        ArgumentCaptor<GatewayToolCallRequest> captor = ArgumentCaptor.forClass(
                GatewayToolCallRequest.class
        );
        verify(gatewayToolClient).execute(captor.capture());
        return captor.getValue();
    }

    private String simulationRequest(AgentSimulationScenario scenario) {
        return """
                {
                  "agentRunId": "RUN-001",
                  "passportId": "PASS-001",
                  "scenario": "%s"
                }
                """.formatted(scenario.name());
    }
}
