package io.finguard.agent.domain;

public enum AgentSimulationScenario {
    NORMAL_CREDIT_SCORE("CUST-1001"),
    CASE_SCOPE_ATTACK("CUST-9999");

    private final String targetConsumerId;

    AgentSimulationScenario(String targetConsumerId) {
        this.targetConsumerId = targetConsumerId;
    }

    public String targetConsumerId() {
        return targetConsumerId;
    }
}
