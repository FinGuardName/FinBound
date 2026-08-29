package io.finguard.agent.api;

import io.finguard.agent.domain.AgentSimulationScenario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentSimulationRequest(
        @NotBlank String agentRunId,
        @NotBlank String passportId,
        @NotNull AgentSimulationScenario scenario
) {
}
