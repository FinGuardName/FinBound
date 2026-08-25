package io.finguard.agent.agentrun.port;

import java.util.Optional;

import io.finguard.agent.agentrun.domain.AgentRun;

public interface AgentRunRepository {
    AgentRun save(AgentRun agentRun);

    Optional<AgentRun> findById(String agentRunId);
}
