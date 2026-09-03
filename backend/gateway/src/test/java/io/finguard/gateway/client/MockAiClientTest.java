package io.finguard.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.finguard.gateway.client.impl.MockAiClient;
import io.finguard.gateway.contract.FinancialAction;
import io.finguard.gateway.contract.FinancialDataType;
import io.finguard.gateway.contract.FinancialTool;
import io.finguard.gateway.dto.BehaviorHistory;
import io.finguard.gateway.dto.BehaviorRiskResult;
import io.finguard.gateway.dto.PromptRiskSnapshot;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.RiskInput;
import io.finguard.gateway.dto.ScopeStatus;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

class MockAiClientTest {

    @Test
    void returnsLowRisk() {
        BehaviorRiskResult risk = new MockAiClient().evaluateBehavior(
            VerifiedAgentIdentity.verified("LOAN-AGENT-01"),
            new ToolCallRequest("R", "P", FinancialTool.CREDIT_SCORE_READ, "CUST-1001",
                List.of(FinancialDataType.CREDIT_SCORE), FinancialAction.READ),
            new ResolvedContext(UUID.randomUUID(),
                new ResolvedContext.References("EMP-101", "LOAN-2026-001", "PASS-001"),
                ScopeStatus.allOk(),
                PromptRiskSnapshot.notEvaluated()),
            new BehaviorHistory("LOAN-AGENT-01", "5m", List.of()),
            "REQ-1",
            null,
            java.time.Instant.now());
        assertThat(risk.behaviorRiskLevel()).isEqualTo("LOW");
        assertThat(risk.isAnomaly()).isFalse();
    }
}
