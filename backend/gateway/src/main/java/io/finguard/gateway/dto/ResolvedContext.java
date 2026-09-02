package io.finguard.gateway.dto;

/** Core가 조립한 Scope Status와 저장된 Prompt Risk Snapshot의 정책용 투영. */
public record ResolvedContext(
    ScopeStatus scopeStatus,
    PromptRiskInput promptRisk
) { }
