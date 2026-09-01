package io.finguard.core.agentrun;

import io.finguard.core.domain.AgentSimulationScenario;
import io.finguard.core.domain.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AgentRun 생성 요청. {@code docs/04-api-contract.md} §3.
 *
 * <p>{@code inputText}는 비신뢰 입력이다. 저장도 로깅도 하지 않고 해시만 남긴다({@code docs/06} §24).
 */
public record AgentRunCreateRequest(
        @NotBlank String employeeId,
        @NotBlank String consumerId,
        @NotNull TaskType taskType,
        @NotBlank String inputText,
        AgentSimulationScenario scenario) {

    /**
     * {@code scenario}는 선택이고 기본값은 {@link AgentSimulationScenario#NORMAL_CREDIT_SCORE}다.
     *
     * <p>필수로 두면 이 값을 모르는 기존 호출자가 전부 깨진다. 그리고 기본값은 안전한 쪽이어야 한다 —
     * 지정하지 않았는데 공격 시나리오가 돌면 곤란하다.
     */
    public AgentRunCreateRequest {
        scenario = scenario == null ? AgentSimulationScenario.NORMAL_CREDIT_SCORE : scenario;
    }
}
