package io.finguard.core.agentrun;

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
        @NotBlank String inputText) {
}
