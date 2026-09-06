package io.finguard.agent.api;

import io.finguard.agent.domain.AgentSimulationScenario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /internal/v1/agent-simulations} 요청 본문. {@code docs/04-api-contract.md} §3.1.
 *
 * <p>{@code caseConsumerId} 는 Core 가 만든 Financial Case 의 고객이다. 정상 시나리오가 이 고객을
 * 조회한다. <strong>이름이 {@code targetConsumerId} 가 아닌 이유</strong>는 공격 시나리오가 이 값을
 * 무시하고 자기 Fixture 고객을 쓰기 때문이다 — 실제 조회 대상이라고 부르면 절반은 거짓말이 된다.
 *
 * <p>Agent 가 이 값을 마음대로 정할 수 있는 것은 아니다. Gateway 는 Core 의 Context Resolve 로
 * Passport 와 사건을 직접 조회해 <strong>대상 = Passport 고객 = 사건 고객</strong> 삼자 일치를
 * 확인한다. 어긋나면 {@code CASE_SCOPE_VIOLATION} 이다.
 */
public record AgentSimulationRequest(
        @NotBlank String agentRunId,
        @NotBlank String passportId,
        @NotBlank @Size(max = 64) String caseConsumerId,
        @NotNull AgentSimulationScenario scenario
) {
    public AgentSimulationRequest {
        if (agentRunId == null || agentRunId.isBlank()
                || passportId == null || passportId.isBlank()
                || caseConsumerId == null || caseConsumerId.isBlank()
                || scenario == null) {
            throw new IllegalArgumentException(
                    "Core-issued references, case consumer and scenario are required");
        }
    }
}
