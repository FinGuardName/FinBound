package io.finguard.core.domain;

/** Prompt Risk 평가 여부. docs/04-api-contract.md §7 — 검사하지 않았음과 검사했고 음성은 반드시 구분한다. */
public enum PromptRiskEvaluationStatus {
    EVALUATED,
    NOT_EVALUATED,
}
