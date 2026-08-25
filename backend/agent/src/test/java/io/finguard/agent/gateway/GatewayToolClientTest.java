package io.finguard.agent.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.finguard.agent.config.AgentProperties;
import io.finguard.agent.domain.FinancialAction;
import io.finguard.agent.domain.FinancialDataType;
import io.finguard.agent.domain.FinancialTool;
import io.finguard.agent.domain.PolicyDecision;
import reactor.core.publisher.Mono;

class GatewayToolClientTest {
    @Test
    void sendsCredentialRequestIdAndTraceparent() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        GatewayToolClient client = client(
                request -> {
                    captured.set(request);
                    return jsonResponse(
                            HttpStatus.OK,
                            "{\"requestId\":\"REQ-001\",\"decision\":\"ALLOW\","
                                    + "\"result\":{\"creditScore\":812}}"
                    );
                },
                Duration.ofSeconds(1)
        );

        GatewayToolCallResponse response = client.execute(toolRequest()).block();

        assertThat(response).isNotNull();
        assertThat(response.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(captured.get().url().getPath()).isEqualTo("/gateway/v1/tool-calls");
        assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer test-agent-service-credential");
        assertThat(captured.get().headers().getFirst(GatewayToolClient.REQUEST_ID_HEADER))
                .isNotBlank();
        assertThat(captured.get().headers().getFirst(GatewayToolClient.TRACEPARENT_HEADER))
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    @Test
    void acceptsGatewayBlockAsPolicyResult() {
        GatewayToolClient client = client(
                request -> jsonResponse(
                        HttpStatus.FORBIDDEN,
                        "{\"requestId\":\"REQ-002\",\"decision\":\"BLOCK\","
                                + "\"reasonCodes\":[\"CASE_SCOPE_VIOLATION\"]}"
                ),
                Duration.ofSeconds(1)
        );

        GatewayToolCallResponse response = client.execute(toolRequest()).block();

        assertThat(response).isNotNull();
        assertThat(response.decision()).isEqualTo(PolicyDecision.BLOCK);
        assertThat(response.reasonCodes()).containsExactly("CASE_SCOPE_VIOLATION");
    }

    @Test
    void rejectsSuccessfulResponseWithoutDecision() {
        GatewayToolClient client = client(
                request -> jsonResponse(HttpStatus.OK, "{\"requestId\":\"REQ-003\"}"),
                Duration.ofSeconds(1)
        );

        assertThatThrownBy(() -> client.execute(toolRequest()).block())
                .isInstanceOf(GatewayCallException.class)
                .hasMessage("GATEWAY_RESPONSE_INVALID");
    }

    @Test
    void rejectsSuccessfulResponseWithoutBody() {
        GatewayToolClient client = client(
                request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()),
                Duration.ofSeconds(1)
        );

        assertThatThrownBy(() -> client.execute(toolRequest()).block())
                .isInstanceOf(GatewayCallException.class)
                .hasMessage("GATEWAY_RESPONSE_INVALID");
    }

    @Test
    void mapsUnexpectedGatewayStatusToFailure() {
        GatewayToolClient client = client(
                request -> Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build()),
                Duration.ofSeconds(1)
        );

        assertThatThrownBy(() -> client.execute(toolRequest()).block())
                .isInstanceOf(GatewayCallException.class)
                .hasMessage("GATEWAY_REQUEST_FAILED");
    }

    @Test
    void mapsGatewayTimeoutToExplicitFailure() {
        GatewayToolClient client = client(request -> Mono.never(), Duration.ofMillis(10));

        assertThatThrownBy(() -> client.execute(toolRequest()).block())
                .isInstanceOf(GatewayCallException.class)
                .hasMessage("GATEWAY_TIMEOUT");
    }

    @Test
    void mapsGatewayTransportFailureToUnavailable() {
        GatewayToolClient client = client(
                request -> Mono.error(new IllegalStateException("transport failed")),
                Duration.ofSeconds(1)
        );

        assertThatThrownBy(() -> client.execute(toolRequest()).block())
                .isInstanceOf(GatewayCallException.class)
                .hasMessage("GATEWAY_UNAVAILABLE");
    }

    @Test
    void serializesOnlyGatewayContractFields() throws Exception {
        String json = new ObjectMapper().writeValueAsString(toolRequest());

        assertThat(json).contains(
                "\"agentRunId\":\"RUN-001\"",
                "\"passportId\":\"PASS-001\"",
                "\"tool\":\"CREDIT_SCORE_READ\"",
                "\"targetConsumerId\":\"CUST-1001\"",
                "\"requestedData\":[\"CREDIT_SCORE\"]",
                "\"action\":\"READ\""
        );
        assertThat(json).doesNotContain(
                "employeeId",
                "agentId",
                "caseId",
                "allowedTools",
                "allowedData"
        );
    }

    private GatewayToolClient client(ExchangeFunction exchangeFunction, Duration timeout) {
        AgentProperties properties = new AgentProperties(
                "http://gateway.test",
                "test-agent-service-credential",
                "test-internal-credential",
                timeout
        );
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        return new GatewayToolClient(builder, properties);
    }

    private Mono<ClientResponse> jsonResponse(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private GatewayToolCallRequest toolRequest() {
        return new GatewayToolCallRequest(
                "RUN-001",
                "PASS-001",
                FinancialTool.CREDIT_SCORE_READ,
                "CUST-1001",
                List.of(FinancialDataType.CREDIT_SCORE),
                FinancialAction.READ
        );
    }
}
