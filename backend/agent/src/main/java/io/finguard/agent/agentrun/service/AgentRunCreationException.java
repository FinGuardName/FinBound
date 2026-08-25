package io.finguard.agent.agentrun.service;

public class AgentRunCreationException extends RuntimeException {
    public AgentRunCreationException(Throwable cause) {
        super("AgentRun creation dependency failed", cause);
    }
}
