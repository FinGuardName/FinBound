package io.finguard.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.finguard.gateway.client.impl.MockAiClient;
import io.finguard.gateway.contract.FinancialAction;
import io.finguard.gateway.contract.FinancialDataType;
import io.finguard.gateway.contract.FinancialTool;
import io.finguard.gateway.dto.BehaviorRiskInput;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

class MockAiClientTest {

    @Test
    void returnsLowRisk() {
        BehaviorRiskInput risk = new MockAiClient().evaluate(
            VerifiedAgentIdentity.verified("LOAN-AGENT-01"),
            new ToolCallRequest("R", "P", FinancialTool.CREDIT_SCORE_READ, "CUST-1001",
                List.of(FinancialDataType.CREDIT_SCORE), FinancialAction.READ),
            "REQ-1");
        assertThat(risk.behaviorRiskLevel()).isEqualTo("LOW");
        assertThat(risk.behaviorAnomalyDetected()).isFalse();
    }
}
