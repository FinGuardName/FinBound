package io.finguard.core.agentrun;

import java.time.Instant;
import java.util.List;

import io.finguard.core.domain.AgentRunStatus;

/**
 * AgentRun 응답. {@code docs/04-api-contract.md} §4.2 형태를 따른다.
 *
 * <p>권한 내용(allowedTools/allowedData)은 싣지 않는다 — 그건 Task Passport(§4.1)의 것이고,
 * 비교 화면용 조회는 §15의 {@code /api/v1/agent-runs/{agentRunId}/permission-comparison} 이 담당한다.
 *
 * <p>입력 원문을 되돌려주지 않는다. {@code inputRefs}만 나간다.
 */
public record AgentRunResponse(
        String agentRunId,
        String agentId,
        String employeeId,
        String caseId,
        String passportId,
        List<String> inputRefs,
        AgentRunStatus status,
        Instant startedAt) {
}
