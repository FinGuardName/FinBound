package io.finguard.core.agentrun;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import io.finguard.core.domain.AgentRunStatus;
import io.finguard.core.domain.DataType;
import io.finguard.core.domain.Tool;

/**
 * AgentRun 시작 결과. {@code docs/04-api-contract.md} §3 · §4.2.
 *
 * <p>Agent는 권한 내용을 직접 수정하지 않고 {@code passportId}만 Runtime 요청에 사용한다.
 * {@code allowedTools} / {@code allowedData}는 발급된 Passport의 내용이며 호출자가 화면에 쓰기 위한
 * 참고값이다 — 권한의 근거는 서버에 저장된 Passport다.
 *
 * <p>입력 원문은 담지 않는다. {@code inputRefs}만 남는다({@code docs/06} §24).
 */
public record AgentRunStarted(
        String agentRunId,
        String agentId,
        String employeeId,
        String caseId,
        String passportId,
        String consumerId,
        List<String> inputRefs,
        AgentRunStatus status,
        Instant startedAt,
        Set<Tool> allowedTools,
        Set<DataType> allowedData) {
}
