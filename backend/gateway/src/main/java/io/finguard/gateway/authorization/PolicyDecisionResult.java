package io.finguard.gateway.authorization;

import java.util.List;

import io.finguard.gateway.contract.PolicyDecision;

/**
 * OPA가 반환한 원본 판정. Rego 응답 스키마(docs §12)와 1:1로 대응한다.
 * 관찰치(behaviorRisk 등)는 {@link AuthorizationOutcome}에 별도로 담는다.
 */
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
