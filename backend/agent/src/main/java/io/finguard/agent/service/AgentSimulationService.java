package io.finguard.agent.service;

import org.springframework.stereotype.Service;

import io.finguard.agent.api.AgentSimulationRequest;
import io.finguard.agent.api.AgentSimulationResponse;
import io.finguard.agent.domain.FinancialAction;
import io.finguard.agent.gateway.GatewayToolCallRequest;
import io.finguard.agent.gateway.GatewayToolClient;
import reactor.core.publisher.Mono;

@Service
public class AgentSimulationService {
    private final GatewayToolClient gatewayToolClient;

    public AgentSimulationService(GatewayToolClient gatewayToolClient) {
        this.gatewayToolClient = gatewayToolClient;
    }

    public Mono<AgentSimulationResponse> simulate(AgentSimulationRequest request) {
        GatewayToolCallRequest gatewayRequest = new GatewayToolCallRequest(
                request.agentRunId(),
                request.passportId(),
                request.scenario().tool(),
                // 정상 시나리오는 사건의 고객을, 공격은 자기 Fixture 고객을 쓴다.
                request.scenario().resolveTargetConsumerId(request.caseConsumerId()),
                request.scenario().requestedData(),
                FinancialAction.READ
        );
        return gatewayToolClient.execute(gatewayRequest)
                .map(gatewayResponse -> new AgentSimulationResponse(
                        request.scenario(),
                        gatewayResponse
                ));
    }
}
