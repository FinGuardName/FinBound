package io.finguard.core.agentrun;

/** Core가 발급한 실행 참조로 P0 Agent Simulator를 시작하는 경계입니다. */
public interface AgentSimulatorClient {
    void simulate(String agentRunId, String passportId);
}
