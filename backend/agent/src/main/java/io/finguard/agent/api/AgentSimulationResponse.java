package io.finguard.agent.api;

import io.finguard.agent.domain.AgentSimulationScenario;
import io.finguard.agent.gateway.GatewayToolCallResponse;

public record AgentSimulationResponse(
        AgentSimulationScenario scenario,
        GatewayToolCallResponse gatewayResponse
) {
}
