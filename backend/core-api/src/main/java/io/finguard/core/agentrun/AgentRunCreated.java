package io.finguard.core.agentrun;

import io.finguard.core.domain.AgentSimulationScenario;

/**
 * AgentRun과 Task Passport가 저장됐다는 사실.
 *
 * <p>{@code caseConsumerId} 는 Financial Case 의 고객이다. Agent 의 정상 시나리오가 이 고객을
 * 조회한다 — 예전에는 시나리오에 고객이 박혀 있어 어떤 사건이든 같은 고객을 봤다(이슈 #115).
 *
 * <p>이 이벤트는 <strong>커밋 이후</strong>에만 처리된다. 커밋 전에 Agent를 깨우면 Agent가
 * Gateway를 거쳐 Core로 되돌아왔을 때 Passport가 아직 없어 조회에 실패한다.
 */
public record AgentRunCreated(
        String agentRunId,
        String passportId,
        String caseConsumerId,
        AgentSimulationScenario scenario) {
}
