package io.finguard.core.agentrun;

import io.finguard.core.domain.AgentSimulationScenario;
import io.finguard.core.domain.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * AgentRun 생성 요청. {@code docs/04-api-contract.md} §3.
 *
 * <p>{@code inputText}는 비신뢰 입력이다. 저장도 로깅도 하지 않고 해시만 남긴다({@code docs/06} §24).
 */
public record AgentRunCreateRequest(
        @NotBlank String employeeId,
        @NotBlank String consumerId,
        @NotNull TaskType taskType,
        // ai-risk는 4096자를 넘으면 422로 거부한다(ai-risk/app/schemas/prompt.py:33).
        // 여기서 막지 않으면 "성공했지만 반드시 막힐 실행"이 생긴다 — Detector가 평가하지
        // 못한 입력은 스냅샷이 NOT_EVALUATED로 남고 Gateway가 전부 fail-closed한다.
        @NotBlank @Size(max = 4096) String inputText,
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
