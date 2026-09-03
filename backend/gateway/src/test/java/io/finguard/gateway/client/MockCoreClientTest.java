package io.finguard.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.finguard.gateway.client.impl.MockCoreClient;
import io.finguard.gateway.contract.FinancialAction;
import io.finguard.gateway.contract.FinancialDataType;
import io.finguard.gateway.contract.FinancialTool;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

class MockCoreClientTest {

    private final MockCoreClient client = new MockCoreClient();
    private final VerifiedAgentIdentity identity = VerifiedAgentIdentity.verified("LOAN-AGENT-01");

    @Test
    void caseConsumerYieldsAllOk() {
        ToolCallRequest request = new ToolCallRequest(
            "RUN-001", "PASS-001", FinancialTool.CREDIT_SCORE_READ, "CUST-1001",
            List.of(FinancialDataType.CREDIT_SCORE), FinancialAction.READ);
        ResolvedContext context = client.resolveContext(identity, request, "REQ-001", null);
        assertThat(context.scopeStatus().customerScope()).isEqualTo("OK");
        assertThat(context.promptRiskSnapshot().evaluationStatus()).isEqualTo("EVALUATED");
    }

    @Test
    void nonCaseConsumerYieldsCustomerScopeViolation() {
        ToolCallRequest request = new ToolCallRequest(
            "RUN-001", "PASS-001", FinancialTool.CREDIT_SCORE_READ, "CUST-9999",
            List.of(FinancialDataType.CREDIT_SCORE), FinancialAction.READ);
        ResolvedContext context = client.resolveContext(identity, request, "REQ-002", null);
        assertThat(context.scopeStatus().customerScope()).isEqualTo("VIOLATION");
        assertThat(context.scopeStatus().employeeAuthority()).isEqualTo("OK");
    }

    @Test
    void recordAuthFailureIsSafe() {
        client.recordAuthFailure("REQ-X01", null, "AGENT_AUTHENTICATION_FAILED");
    }
}
