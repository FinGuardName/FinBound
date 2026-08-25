package io.finguard.agent.agentrun.adapter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import io.finguard.agent.agentrun.domain.AgentRun;
import io.finguard.agent.agentrun.port.AgentRunRepository;

@Repository
public class InMemoryAgentRunRepository implements AgentRunRepository {
    private final Map<String, AgentRun> agentRuns = new ConcurrentHashMap<>();

    @Override
    public AgentRun save(AgentRun agentRun) {
        agentRuns.put(agentRun.agentRunId(), agentRun);
        return agentRun;
    }

    @Override
    public Optional<AgentRun> findById(String agentRunId) {
        return Optional.ofNullable(agentRuns.get(agentRunId));
    }
}
