package io.finguard.core.context;

import java.math.BigDecimal;
import java.util.UUID;

import io.finguard.core.domain.PromptRiskEvaluationStatus;

/** {@code docs/04-api-contract.md} §7 성공 응답. */
public record ContextResolveResponse(
        UUID requestId,
        References references,
        ScopeStatus scopeStatus,
        PromptRiskView promptRiskSnapshot) {

    public record References(String employeeId, String caseId, String passportId) {
    }

    public record PromptRiskView(
            PromptRiskEvaluationStatus evaluationStatus,
            BigDecimal promptRisk,
            boolean detected,
            String inputHash,
            String modelVersion) {
    }
}
