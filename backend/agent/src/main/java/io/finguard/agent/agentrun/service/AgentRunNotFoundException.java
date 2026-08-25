package io.finguard.agent.agentrun.service;

public class AgentRunNotFoundException extends RuntimeException {
    public AgentRunNotFoundException(String agentRunId) {
        super("AgentRun not found: " + agentRunId);
    }
}
