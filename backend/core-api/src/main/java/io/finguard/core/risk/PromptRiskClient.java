package io.finguard.core.risk;

import java.util.Optional;

/**
 * Prompt Risk 평가기.
 *
 * <p><strong>실패를 예외로 던지지 않는다.</strong> ai-risk 장애는 치명적이지 않기로 했다 — 실행은
 * 생성하고 스냅샷을 {@code NOT_EVALUATED} 로 남긴 뒤 Gateway 가 Tool Call 시점에 fail-closed 한다
 * ({@code docs/01} AC-14). 예외로 던지면 호출부마다 try/catch 가 생기고 그 결정이 흐려진다.
 */
public interface PromptRiskClient {

    Optional<PromptRiskEvaluation> evaluate(
            String agentRunId, String inputRef, String inputText, String inputHash);
}
