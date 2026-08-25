package io.finguard.agent.agentrun.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;

import io.finguard.agent.agentrun.domain.AgentRunContext;
import io.finguard.agent.agentrun.domain.TaskType;
import io.finguard.agent.agentrun.port.AgentRunContextProvider;

@Component
public class LocalAgentRunContextProvider implements AgentRunContextProvider {
    @Override
    public AgentRunContext prepare(String employeeId, String consumerId, TaskType taskType) {
        return new AgentRunContext(
                "LOCAL-CASE-" + UUID.randomUUID(),
                "LOCAL-PASS-" + UUID.randomUUID()
        );
    }
}
