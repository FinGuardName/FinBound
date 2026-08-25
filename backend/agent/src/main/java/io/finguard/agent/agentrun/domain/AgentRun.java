package io.finguard.agent.agentrun.domain;

import java.time.Instant;
import java.util.List;

/**
 * Core가 발급한 AgentRun 참조입니다.
 *
 * <p>Case, Passport, input reference와 상태의 권위 있는 원본은 Core에 있습니다. Agent 모듈은
 * 이 값을 로컬에서 발급하거나 상태 전환하지 않고 실행에 필요한 참조로만 사용합니다.
 */
public record AgentRun(
        String agentRunId,
        String agentId,
        String employeeId,
        String caseId,
        String passportId,
        List<String> inputRefs,
        AgentRunStatus status,
        Instant startedAt
) {
    public AgentRun {
        inputRefs = List.copyOf(inputRefs);
    }
}
