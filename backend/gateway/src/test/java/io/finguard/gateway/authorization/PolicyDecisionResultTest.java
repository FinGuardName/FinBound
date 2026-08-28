package io.finguard.gateway.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.finguard.gateway.contract.PolicyDecision;

class PolicyDecisionResultTest {

    @Test
    void allowFlagIsTrueOnlyForAllowDecision() {
        assertThat(new PolicyDecisionResult(
            PolicyDecision.ALLOW, "LOW", false, List.of(), "v").isAllow()).isTrue();
        assertThat(new PolicyDecisionResult(
            PolicyDecision.BLOCK, "HIGH", true, List.of("X"), "v").isAllow()).isFalse();
    }

    @Test
    void blockFactoryPopulatesReasonCode() {
        PolicyDecisionResult result = PolicyDecisionResult.block("CONTEXT_SERVICE_UNAVAILABLE");
        assertThat(result.decision()).isEqualTo(PolicyDecision.BLOCK);
        assertThat(result.reasonCodes()).containsExactly("CONTEXT_SERVICE_UNAVAILABLE");
        assertThat(result.riskFlagged()).isTrue();
    }
}
