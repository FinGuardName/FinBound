package io.finguard.gateway.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.finguard.gateway.client.impl.CoreClientImpl;
import io.finguard.gateway.contract.FinancialAction;
import io.finguard.gateway.contract.FinancialDataType;
import io.finguard.gateway.contract.FinancialTool;
import io.finguard.gateway.dto.AuditOutcome;
import io.finguard.gateway.dto.AuditStart;
import io.finguard.gateway.dto.BehaviorHistory;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.exception.AuditWriteException;
import io.finguard.gateway.exception.BehaviorHistoryUnavailableException;
import io.finguard.gateway.exception.DuplicateRequestException;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

class CoreClientImplTest {

    private WireMockServer server;
    private CoreClientImpl client;

    private final VerifiedAgentIdentity identity = VerifiedAgentIdentity.verified("LOAN-AGENT-01");
    private final ToolCallRequest request = new ToolCallRequest(
        "RUN-001", "PASS-001", FinancialTool.CREDIT_SCORE_READ, "CUST-1001",
        List.of(FinancialDataType.CREDIT_SCORE), FinancialAction.READ);

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        client = new CoreClientImpl(server.baseUrl(), "internal-secret", 1_000);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void resolveContextCallsCoreInternalApiWithVerifiedAgentHeaders() {
        server.stubFor(post(urlEqualTo("/internal/v1/context/resolve"))
            .withHeader("X-FinGuard-Service-Credential", equalTo("internal-secret"))
            .withHeader("X-Verified-Agent-Id", equalTo("LOAN-AGENT-01"))
            .withHeader("X-Request-Id", equalTo("550e8400-e29b-41d4-a716-446655440000"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "requestId": "550e8400-e29b-41d4-a716-446655440000",
                      "references": {
                        "employeeId": "EMP-101",
                        "caseId": "LOAN-2026-001",
                        "passportId": "PASS-001"
                      },
                      "scopeStatus": {
                        "employeeAuthority": "OK",
                        "permissionTemplate": "OK",
                        "caseStatus": "OK",
                        "mandate": "OK",
                        "passportStatus": "OK",
                        "agentBinding": "OK",
                        "customerScope": "OK",
                        "toolScope": "OK",
                        "dataScope": "OK"
                      },
                      "promptRiskSnapshot": {
                        "evaluationStatus": "NOT_EVALUATED",
                        "promptRisk": 0.00,
                        "riskLevel": "LOW",
                        "detected": false,
                        "inputHash": "sha256:pending",
                        "modelVersion": "prompt-guard-1"
                      }
                    }
                    """)));

        ResolvedContext context = client.resolveContext(
            identity, request, "550e8400-e29b-41d4-a716-446655440000", "trace");

        assertThat(context.references().caseId()).isEqualTo("LOAN-2026-001");
        assertThat(context.scopeStatus().customerScope()).isEqualTo("OK");
        assertThat(context.promptRiskSnapshot().riskLevel()).isEqualTo("LOW");
        assertThat(context.promptRiskSnapshot().detected()).isFalse();
    }

    @Test
    void auditCreateFailureIsMappedToAuditWriteException() {
        server.stubFor(post(urlEqualTo("/internal/v1/audits"))
            .willReturn(aResponse().withStatus(503)));

        AuditStart auditStart = new AuditStart(
            "REQ-1", "trace", "RUN-001", "LOAN-AGENT-01", null,
            "CUST-1001", FinancialTool.CREDIT_SCORE_READ, "PROCESSING", Instant.now());

        assertThatThrownBy(() -> client.createAudit(identity, auditStart, "trace"))
            .isInstanceOf(AuditWriteException.class);
    }

    @Test
    void auditCreateConflictIsMappedToDuplicateRequest() {
        server.stubFor(post(urlEqualTo("/internal/v1/audits"))
            .willReturn(aResponse().withStatus(409)));

        AuditStart auditStart = new AuditStart(
            "REQ-1", "trace", "RUN-001", "LOAN-AGENT-01", null,
            "CUST-1001", FinancialTool.CREDIT_SCORE_READ, "PROCESSING", Instant.now());

        assertThatThrownBy(() -> client.createAudit(identity, auditStart, "trace"))
            .isInstanceOf(DuplicateRequestException.class);
    }

    @Test
    void behaviorHistoryUsesCoreHistoryApi() {
        server.stubFor(get(urlEqualTo("/internal/v1/agents/LOAN-AGENT-01/behavior-history?window=5m"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "agentId": "LOAN-AGENT-01",
                      "window": "5m",
                      "completedEvents": []
                    }
                    """)));

        BehaviorHistory history = client.behaviorHistory(identity, "5m", "REQ-1", "trace");

        assertThat(history.agentId()).isEqualTo("LOAN-AGENT-01");
        assertThat(history.completedEvents()).isEmpty();
    }

    @Test
    void emptyBehaviorHistoryResponseFailsClosed() {
        server.stubFor(get(urlEqualTo("/internal/v1/agents/LOAN-AGENT-01/behavior-history?window=5m"))
            .willReturn(aResponse().withStatus(200)));

        assertThatThrownBy(() -> client.behaviorHistory(identity, "5m", "REQ-1", "trace"))
            .isInstanceOf(BehaviorHistoryUnavailableException.class);
    }

    @Test
    void recordAuthFailureCallsSecurityEventApi() {
        server.stubFor(post(urlEqualTo("/internal/v1/security-events/auth-failure"))
            .willReturn(aResponse().withStatus(201)));

        client.recordAuthFailure("REQ-401", "trace", "AGENT_AUTHENTICATION_FAILED");

        assertThat(server.getAllServeEvents()).hasSize(1);
        assertThat(server.getAllServeEvents().getFirst().getRequest()
            .getHeader("X-FinGuard-Service-Credential")).isEqualTo("internal-secret");
    }

    @Test
    void updateAuditOutcomeCallsCorePatchApi() {
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.patch(
                urlEqualTo("/internal/v1/audits/REQ-1/outcome"))
            .willReturn(aResponse().withStatus(200)));

        AuditOutcome outcome = new AuditOutcome(
            io.finguard.gateway.contract.PolicyDecision.BLOCK,
            "COMPLETED",
            java.util.Set.of("CASE_SCOPE_VIOLATION"),
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            "policy-1",
            Instant.now());

        client.updateAuditOutcome(identity, "REQ-1", outcome, "trace");

        assertThat(server.getAllServeEvents()).hasSize(1);
    }
}
