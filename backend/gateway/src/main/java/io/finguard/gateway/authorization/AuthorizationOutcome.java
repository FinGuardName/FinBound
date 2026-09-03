package io.finguard.gateway.authorization;

import java.util.List;

public record AuthorizationOutcome(PolicyDecisionResult decision, Double behaviorRisk) {

    public boolean isAllow() {
        return decision.isAllow();
    }

    public List<String> reasonCodes() {
        return decision.reasonCodes();
    }

    public String policyVersion() {
        return decision.policyVersion();
    }

    public String severity() {
        return decision.severity();
    }

    public boolean riskFlagged() {
        return decision.riskFlagged();
    }

    public static AuthorizationOutcome failClosed(String reasonCode) {
        return new AuthorizationOutcome(PolicyDecisionResult.block(reasonCode), null);
    }
}
