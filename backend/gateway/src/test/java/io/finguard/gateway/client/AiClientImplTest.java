package io.finguard.gateway.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.finguard.gateway.client.impl.AiClientImpl;
import io.finguard.gateway.contract.FinancialAction;
import io.finguard.gateway.contract.FinancialDataType;
import io.finguard.gateway.contract.FinancialTool;
import io.finguard.gateway.contract.PolicyDecision;
import io.finguard.gateway.dto.BehaviorHistory;
import io.finguard.gateway.dto.BehaviorRiskResult;
import io.finguard.gateway.dto.PromptRiskSnapshot;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.ScopeStatus;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

class AiClientImplTest {

    private WireMockServer server;
    private AiClientImpl client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        client = new AiClientImpl(server.baseUrl(), "internal-secret", 1_000);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void evaluateBehaviorSendsHistoryAndCurrentAttemptWithoutFutureOutcomeFields() {
        server.stubFor(post(urlEqualTo("/internal/v1/risk/behavior"))
            .withHeader("X-FinGuard-Service-Credential", equalTo("internal-secret"))
            .withHeader("X-Request-Id", equalTo("REQ-1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "behaviorRisk": 0.82,
                      "behaviorRiskLevel": "ALERT",
                      "isAnomaly": true,
                      "rawScore": -0.14,
                      "historyStatus": "READY",
                      "featureVersion": "behavior-features-1",
                      "modelVersion": "iforest-1"
                    }
                    """)));

        ToolCallRequest request = new ToolCallRequest(
            "RUN-001", "PASS-001", FinancialTool.CREDIT_SCORE_READ, "CUST-1001",
            List.of(FinancialDataType.CREDIT_SCORE), FinancialAction.READ);
        ResolvedContext context = new ResolvedContext(
            UUID.randomUUID(),
            new ResolvedContext.References("EMP-101", "LOAN-2026-001", "PASS-001"),
            ScopeStatus.allOk(),
            PromptRiskSnapshot.notEvaluated());

        BehaviorRiskResult result = client.evaluateBehavior(
            VerifiedAgentIdentity.verified("LOAN-AGENT-01"),
            request,
            context,
            new BehaviorHistory("LOAN-AGENT-01", "5m", List.of()),
            "REQ-1",
            "trace",
            Instant.parse("2026-08-17T12:00:00Z"));

        assertThat(result.behaviorRiskLevel()).isEqualTo("ALERT");
        String body = server.getAllServeEvents().getFirst().getRequest().getBodyAsString();
        assertThat(body).contains("\"currentAttempt\"");
        assertThat(body).doesNotContain("\"success\"");
        assertThat(body).doesNotContain("\"latencyMs\"");
        assertThat(body).doesNotContain("\"recordsRead\"");
    }

    @Test
    void evaluateBehaviorSkipsIncompleteHistoryEventsFromCore() {
        server.stubFor(post(urlEqualTo("/internal/v1/risk/behavior"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "behaviorRisk": 0.11,
                      "behaviorRiskLevel": "LOW",
                      "isAnomaly": false,
                      "rawScore": 0.01,
                      "historyStatus": "READY",
                      "featureVersion": "behavior-features-1",
                      "modelVersion": "iforest-1"
                    }
                    """)));

        ToolCallRequest request = new ToolCallRequest(
            "RUN-001", "PASS-001", FinancialTool.CREDIT_SCORE_READ, "CUST-1001",
            List.of(FinancialDataType.CREDIT_SCORE), FinancialAction.READ);
        ResolvedContext context = new ResolvedContext(
            UUID.randomUUID(),
            new ResolvedContext.References("EMP-101", "LOAN-2026-001", "PASS-001"),
            ScopeStatus.allOk(),
            PromptRiskSnapshot.notEvaluated());
        BehaviorHistory history = new BehaviorHistory("LOAN-AGENT-01", "5m", List.of(
            new BehaviorHistory.CompletedEvent(
                "REQ-COMPLETE", "CASE-1", "CUST-1001", FinancialTool.CREDIT_SCORE_READ,
                Instant.parse("2026-08-17T11:59:00Z"), PolicyDecision.ALLOW, true, 17L,
                List.of(FinancialDataType.CREDIT_SCORE)),
            new BehaviorHistory.CompletedEvent(
                "REQ-INCOMPLETE", null, "CUST-1001", FinancialTool.CREDIT_SCORE_READ,
                Instant.parse("2026-08-17T11:58:00Z"), PolicyDecision.ALLOW, true, 19L,
                List.of(FinancialDataType.CREDIT_SCORE))));

        client.evaluateBehavior(
            VerifiedAgentIdentity.verified("LOAN-AGENT-01"),
            request,
            context,
            history,
            "REQ-2",
            "trace",
            Instant.parse("2026-08-17T12:00:00Z"));

        String body = server.getAllServeEvents().getFirst().getRequest().getBodyAsString();
        assertThat(body).contains("REQ-COMPLETE");
        assertThat(body).doesNotContain("REQ-INCOMPLETE");
    }
}
