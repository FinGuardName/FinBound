package io.finguard.agent.agentrun.port;

import io.finguard.agent.agentrun.domain.AgentRunContext;
import io.finguard.agent.agentrun.domain.TaskType;

public interface AgentRunContextProvider {
    AgentRunContext prepare(String employeeId, String consumerId, TaskType taskType);
}
