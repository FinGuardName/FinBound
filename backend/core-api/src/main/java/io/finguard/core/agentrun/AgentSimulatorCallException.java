package io.finguard.core.agentrun;

public class AgentSimulatorCallException extends RuntimeException {
    private final String errorCode;

    public AgentSimulatorCallException(String errorCode) {
        super("Agent Simulator call failed");
        this.errorCode = errorCode;
    }

    public AgentSimulatorCallException(String errorCode, Throwable cause) {
        super("Agent Simulator call failed", cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
