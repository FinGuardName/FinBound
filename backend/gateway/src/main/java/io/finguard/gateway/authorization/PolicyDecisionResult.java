package io.finguard.gateway.authorization;

import java.util.List;

import io.finguard.gateway.contract.PolicyDecision;

public record PolicyDecisionResult(
    PolicyDecision decision,
    String severity,
    boolean riskFlagged,
    List<String> reasonCodes,
    String policyVersion
) {
    public boolean isAllow() {
        return decision == PolicyDecision.ALLOW;
    }

    public static PolicyDecisionResult block(String reasonCode) {
        return new PolicyDecisionResult(
            PolicyDecision.BLOCK, "CRITICAL", true, List.of(reasonCode), null);
    }
}
