package io.finguard.agent.agentrun.service;

public class AgentRunCreationException extends RuntimeException {
    public AgentRunCreationException(Throwable cause) {
        super("Core could not issue the AgentRun", cause);
    }
}
