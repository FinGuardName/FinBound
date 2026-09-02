package io.finguard.gateway.dto;

/** Core의 입력 수명주기별 PromptRiskSnapshot에서 가져온 정책 입력. */
public record PromptRiskInput(
    String evaluationStatus,
    double promptRisk,
    String promptRiskLevel,
    boolean promptInjectionDetected
) { }
