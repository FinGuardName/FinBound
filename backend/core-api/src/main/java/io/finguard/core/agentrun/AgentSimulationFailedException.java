package io.finguard.core.agentrun;

/**
 * Agent 실행 지시가 실패했다.
 *
 * <p>이 예외는 {@code POST /api/v1/agent-runs} 응답을 뒤집지 않는다. Passport는 이미 발급됐고
 * 그 판단은 유효하다 — 실패한 것은 실행이지 권한 판단이 아니다.
 */
public class AgentSimulationFailedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AgentSimulationFailedException(String agentRunId, Throwable cause) {
        super("Agent simulation failed for " + agentRunId, cause);
    }
}
