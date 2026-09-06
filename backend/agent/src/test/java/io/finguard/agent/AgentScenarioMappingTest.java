package io.finguard.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.finguard.agent.api.AgentSimulationRequest;
import io.finguard.agent.domain.AgentSimulationScenario;
import io.finguard.agent.domain.PolicyDecision;
import io.finguard.agent.gateway.GatewayCallException;
import io.finguard.agent.gateway.GatewayToolCallRequest;
import io.finguard.agent.gateway.GatewayToolCallResponse;
import io.finguard.agent.gateway.GatewayToolClient;
import io.finguard.agent.service.AgentSimulationService;
import reactor.core.publisher.Mono;

class AgentScenarioMappingTest {
    private final GatewayToolClient gateway = mock(GatewayToolClient.class);
    private final AgentSimulationService service = new AgentSimulationService(gateway);
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
        "NORMAL_CREDIT_SCORE,CUST-1001,CREDIT_SCORE_READ,CREDIT_SCORE",
        "NORMAL_INCOME,CUST-1001,INCOME_READ,INCOME",
        "NORMAL_DEBT,CUST-1001,DEBT_READ,DEBT",
        "CASE_SCOPE_ATTACK,CUST-9999,CREDIT_SCORE_READ,CREDIT_SCORE",
        // 공격 셋은 Mandate가 좁은 Fixture 고객을 노린다 — docs/04-api-contract.md §3.1.
        // 이 표를 고치면 §3.1 과 core-api 의 AttackScenarioFixtureTest 도 함께 고쳐야 한다.
        "TOOL_SCOPE_ATTACK,CUST-1002,INCOME_READ,INCOME",
        "DATA_SCOPE_ATTACK,CUST-1002,CREDIT_SCORE_READ,CREDIT_SCORE|INCOME",
        "MANDATE_SCOPE_ATTACK,CUST-1003,DEBT_READ,DEBT"
    })
    void mapsOnlyRuntimeFieldsDeterministically(
            AgentSimulationScenario scenario, String consumer, String tool, String data) throws Exception {
        GatewayToolCallResponse response = new GatewayToolCallResponse(
                "REQ-001", PolicyDecision.ALLOW, mapper.readTree("{\"value\":1}"), List.of());
        when(gateway.execute(any())).thenReturn(Mono.just(response));

        // An attack label must never manufacture a BLOCK when the server allows the request.
        for (int attempt = 0; attempt < 2; attempt++) {
            var result = service.simulate(request(scenario)).block();
            assertThat(result).isNotNull();
            assertThat(result.scenario()).isEqualTo(scenario);
            assertThat(result.gatewayResponse()).isSameAs(response);
        }

        ArgumentCaptor<GatewayToolCallRequest> captured = ArgumentCaptor.forClass(GatewayToolCallRequest.class);
        verify(gateway, times(2)).execute(captured.capture());
        assertThat(captured.getAllValues().get(0)).isEqualTo(captured.getAllValues().get(1));
        JsonNode body = mapper.valueToTree(captured.getValue());
        assertThat(body.size()).isEqualTo(6);
        assertThat(body.get("agentRunId").asText()).isEqualTo("RUN-CORE-060");
        assertThat(body.get("passportId").asText()).isEqualTo("PASS-CORE-060");
        assertThat(body.get("targetConsumerId").asText()).isEqualTo(consumer);
        assertThat(body.get("tool").asText()).isEqualTo(tool);
        assertThat(body.get("action").asText()).isEqualTo("READ");
        assertThat(body.get("requestedData")).isEqualTo(mapper.valueToTree(data.split("\\|")));
    }

    @ParameterizedTest
    @EnumSource(AgentSimulationScenario.class)
    void preservesBlockAndEveryReasonWithoutReordering(AgentSimulationScenario scenario) {
        var response = new GatewayToolCallResponse("REQ-002", PolicyDecision.BLOCK, null,
                List.of("MANDATE_SCOPE_VIOLATION", "DATA_SCOPE_VIOLATION", "MANDATE_SCOPE_VIOLATION"));
        when(gateway.execute(any())).thenReturn(Mono.just(response));

        var result = service.simulate(request(scenario)).block();

        assertThat(result).isNotNull();
        assertThat(result.gatewayResponse()).isSameAs(response);
        verify(gateway).execute(any());
    }

    @ParameterizedTest
    @EnumSource(AgentSimulationScenario.class)
    void doesNotTurnExecutionErrorIntoPolicyResult(AgentSimulationScenario scenario) {
        var failure = new GatewayCallException("GATEWAY_TIMEOUT");
        when(gateway.execute(any())).thenReturn(Mono.error(failure));

        assertThatThrownBy(() -> service.simulate(request(scenario)).block()).isSameAs(failure);
        verify(gateway).execute(any());
    }

    @ParameterizedTest
    @EnumSource(AgentSimulationScenario.class)
    void scenarioDataCannotBeMutated(AgentSimulationScenario scenario) {
        assertThatThrownBy(() -> scenario.requestedData().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 이슈 #115. 예전에는 시나리오에 고객이 박혀 있어 어떤 사건이든 CUST-1001 을 조회했다.
     * 화면에서 다른 고객의 업무를 시작하면 사건은 그 고객으로 만들어지는데 조회는 엉뚱한
     * 고객을 향해 CASE_SCOPE_VIOLATION 으로 막혔다.
     */
    @ParameterizedTest
    @EnumSource(
            value = AgentSimulationScenario.class,
            names = {"NORMAL_CREDIT_SCORE", "NORMAL_INCOME", "NORMAL_DEBT"})
    void normalScenariosAskAboutTheConsumerOfTheCase(AgentSimulationScenario scenario) {
        when(gateway.execute(any())).thenReturn(Mono.just(allowResponse()));

        service.simulate(new AgentSimulationRequest(
                "RUN-115", "PASS-115", "CUST-2001", scenario)).block();

        ArgumentCaptor<GatewayToolCallRequest> sent =
                ArgumentCaptor.forClass(GatewayToolCallRequest.class);
        verify(gateway).execute(sent.capture());
        assertThat(sent.getValue().targetConsumerId()).isEqualTo("CUST-2001");
    }

    /**
     * 공격은 자기 Fixture 고객을 지킨다. 사건의 고객을 쓰면 사건 밖 고객을 노리는 것도,
     * Mandate 가 좁은 고객을 노리는 것도 성립하지 않아 공격이 공격이 아니게 된다.
     */
    @ParameterizedTest
    @CsvSource({
        "CASE_SCOPE_ATTACK,CUST-9999",
        "TOOL_SCOPE_ATTACK,CUST-1002",
        "DATA_SCOPE_ATTACK,CUST-1002",
        "MANDATE_SCOPE_ATTACK,CUST-1003"
    })
    void attackScenariosIgnoreTheCaseConsumer(
            AgentSimulationScenario scenario, String pinned) {
        when(gateway.execute(any())).thenReturn(Mono.just(allowResponse()));

        service.simulate(new AgentSimulationRequest(
                "RUN-115", "PASS-115", "CUST-2001", scenario)).block();

        ArgumentCaptor<GatewayToolCallRequest> sent =
                ArgumentCaptor.forClass(GatewayToolCallRequest.class);
        verify(gateway).execute(sent.capture());
        assertThat(sent.getValue().targetConsumerId()).isEqualTo(pinned);
    }

    @ParameterizedTest
    @EnumSource(
            value = AgentSimulationScenario.class,
            names = {"NORMAL_CREDIT_SCORE", "NORMAL_INCOME", "NORMAL_DEBT"})
    void normalScenariosRefuseToRunWithoutACaseConsumer(AgentSimulationScenario scenario) {
        assertThatThrownBy(() -> scenario.resolveTargetConsumerId(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private GatewayToolCallResponse allowResponse() {
        try {
            return new GatewayToolCallResponse(
                    "REQ-115", PolicyDecision.ALLOW, mapper.readTree("{\"value\":1}"), List.of());
        } catch (Exception unreachable) {
            throw new IllegalStateException(unreachable);
        }
    }

    private AgentSimulationRequest request(AgentSimulationScenario scenario) {
        return new AgentSimulationRequest("RUN-CORE-060", "PASS-CORE-060", "CUST-1001", scenario);
    }
}
