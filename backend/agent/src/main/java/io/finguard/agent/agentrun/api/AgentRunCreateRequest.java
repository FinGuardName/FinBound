package io.finguard.agent.agentrun.api;

import io.finguard.agent.agentrun.domain.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentRunCreateRequest(
        @NotBlank String employeeId,
        @NotBlank String consumerId,
        @NotNull TaskType taskType,
        @NotBlank String inputText
) {
}
