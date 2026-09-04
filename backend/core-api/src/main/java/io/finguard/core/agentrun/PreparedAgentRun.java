package io.finguard.core.agentrun;

import java.util.Optional;

import io.finguard.core.risk.PromptRiskEvaluation;

/**
 * 쓰기 트랜잭션에 들어가기 전에 확보한 것들.
 *
 * <p>식별자를 미리 만드는 이유는 {@code docs/04} §8 요청 본문이 {@code agentRunId}·{@code inputRef}
 * 를 요구하기 때문이다. 같은 값을 ai-risk 요청과 DB 저장 양쪽에 쓴다 — 요청에 실은 식별자와 실제로
 * 저장되는 식별자가 다르면 Detector 쪽 기록과 Core 기록을 맞춰 볼 수 없다.
 *
 * <p>{@code evaluation} 이 비어 있는 경우는 둘이다 — 재사용할 {@code EVALUATED} 스냅샷이 이미
 * 있었거나, 평가가 실패했거나. 둘을 구분할 필요는 없다. 어느 쪽이든 새로 쓸 판정이 없다.
 */
public record PreparedAgentRun(
        String agentRunId,
        String inputRef,
        String inputHash,
        Optional<PromptRiskEvaluation> evaluation) {
}
