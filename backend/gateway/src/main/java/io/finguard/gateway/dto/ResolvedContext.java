package io.finguard.gateway.dto;

import java.util.UUID;

public record ResolvedContext(
    UUID requestId,
    References references,
    ScopeStatus scopeStatus,
    PromptRiskSnapshot promptRiskSnapshot
) {
    public record References(String employeeId, String caseId, String passportId) {
    }
}
