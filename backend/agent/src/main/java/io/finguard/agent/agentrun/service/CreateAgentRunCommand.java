package io.finguard.agent.agentrun.service;

import io.finguard.agent.agentrun.domain.TaskType;

public record CreateAgentRunCommand(
        String employeeId,
        String consumerId,
        TaskType taskType,
        String inputText
) {
}
