package io.finguard.gateway.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import io.finguard.gateway.authorization.AuthorizationOutcome;
import io.finguard.gateway.authorization.AuthorizationService;
import io.finguard.gateway.authorization.PolicyDecisionResult;
import io.finguard.gateway.client.CoreClient;
import io.finguard.gateway.client.DownstreamClient;
import io.finguard.gateway.contract.FinancialAction;
import io.finguard.gateway.contract.FinancialDataType;
import io.finguard.gateway.contract.FinancialTool;
import io.finguard.gateway.contract.PolicyDecision;
import io.finguard.gateway.dto.AuditOutcome;
import io.finguard.gateway.dto.AuditStart;
import io.finguard.gateway.dto.DownstreamToolResult;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.exception.AuditWriteException;
import io.finguard.gateway.exception.DownstreamTimeoutException;
import io.finguard.gateway.exception.DuplicateRequestException;
import io.finguard.gateway.identity.VerifiedAgentIdentity;

class ToolCallEnforcementServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T12:00:00Z"), ZoneOffset.UTC);

    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final CoreClient coreClient = mock(CoreClient.class);
    private final DownstreamClient downstreamClient = mock(DownstreamClient.class);
    private final ToolCallEnforcementService service = new ToolCallEnforcementService(
        authorizationService, coreClient, downstreamClient, CLOCK);

    private final VerifiedAgentIdentity identity = VerifiedAgentIdentity.verified("LOAN-AGENT-01");
    private final ToolCallRequest request = new ToolCallRequest(
        "RUN-001", "PASS-001", FinancialTool.CREDIT_SCORE_READ, "CUST-1001",
        List.of(FinancialDataType.CREDIT_SCORE), FinancialAction.READ);

    @Test
    void allowCallsDownstreamAndCompletesAudit() {
        when(authorizationService.decide(any(), any(), any(), any(), any()))
            .thenReturn(allowOutcome());
        when(downstreamClient.execute(any(), any(), any())).thenReturn(
            new DownstreamToolResult("REQ-1", FinancialTool.CREDIT_SCORE_READ, "CUST-1001",
                Map.of("creditScore", 812)));

        EnforcementResult result = service.enforce(identity, request, "REQ-1", "trace");

        assertThat(result.status().is2xxSuccessful()).isTrue();
        assertThat(result.body().decision()).isEqualTo(PolicyDecision.ALLOW);
        verify(coreClient).createAudit(eq(identity), any(AuditStart.class), eq("trace"));
        verify(downstreamClient).execute(request, "REQ-1", "trace");
        verify(coreClient).updateAuditOutcome(eq(identity), eq("REQ-1"), any(AuditOutcome.class), eq("trace"));
    }

    @Test
    void blockDoesNotCallDownstreamAndCompletesAudit() {
        when(authorizationService.decide(any(), any(), any(), any(), any())).thenReturn(
            new AuthorizationOutcome(
                new PolicyDecisionResult(PolicyDecision.BLOCK, "CRITICAL", true,
                    List.of("CASE_SCOPE_VIOLATION"), "policy-1"),
                0.10));

        EnforcementResult result = service.enforce(identity, request, "REQ-2", "trace");

        assertThat(result.status().value()).isEqualTo(403);
        assertThat(result.body().reasonCodes()).containsExactly("CASE_SCOPE_VIOLATION");
        verify(downstreamClient, never()).execute(any(), any(), any());
        verify(coreClient).updateAuditOutcome(eq(identity), eq("REQ-2"), any(AuditOutcome.class), eq("trace"));
    }

    @Test
    void auditCreateFailureFailsClosedBeforeDownstream() {
        org.mockito.Mockito.doThrow(new AuditWriteException("boom"))
            .when(coreClient).createAudit(any(), any(), any());

        EnforcementResult result = service.enforce(identity, request, "REQ-3", "trace");

        assertThat(result.status().value()).isEqualTo(403);
        assertThat(result.body().reasonCodes()).containsExactly("AUDIT_WRITE_FAILED");
        verify(authorizationService, never()).decide(any(), any(), any(), any(), any());
        verify(downstreamClient, never()).execute(any(), any(), any());
    }

    @Test
    void duplicateAuditCreateFailsClosedBeforeDownstream() {
        org.mockito.Mockito.doThrow(new DuplicateRequestException("duplicate", new RuntimeException()))
            .when(coreClient).createAudit(any(), any(), any());

        EnforcementResult result = service.enforce(identity, request, "REQ-4", "trace");

        assertThat(result.status().value()).isEqualTo(403);
        assertThat(result.body().reasonCodes()).containsExactly("DUPLICATE_REQUEST");
        verify(authorizationService, never()).decide(any(), any(), any(), any(), any());
        verify(downstreamClient, never()).execute(any(), any(), any());
    }

    @Test
    void concurrentDuplicateRequestFailsClosedBeforeSecondDownstream() throws Exception {
        CountDownLatch auditStarted = new CountDownLatch(1);
        CountDownLatch releaseAudit = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            auditStarted.countDown();
            releaseAudit.await();
            return null;
        }).when(coreClient).createAudit(any(), any(), any());
        when(authorizationService.decide(any(), any(), any(), any(), any())).thenReturn(allowOutcome());
        when(downstreamClient.execute(any(), any(), any())).thenReturn(
            new DownstreamToolResult("REQ-5", FinancialTool.CREDIT_SCORE_READ, "CUST-1001",
                Map.of("creditScore", 812)));

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<EnforcementResult> first =
                executor.submit(() -> service.enforce(identity, request, "REQ-5", "trace"));
            auditStarted.await();

            EnforcementResult duplicate = service.enforce(identity, request, "REQ-5", "trace");
            releaseAudit.countDown();

            assertThat(duplicate.status().value()).isEqualTo(403);
            assertThat(duplicate.body().reasonCodes()).containsExactly("DUPLICATE_REQUEST");
            assertThat(first.get().status().is2xxSuccessful()).isTrue();
        }
    }

    @Test
    void downstreamTimeoutCompletesAuditAsTimeoutError() {
        when(authorizationService.decide(any(), any(), any(), any(), any())).thenReturn(allowOutcome());
        org.mockito.Mockito.doThrow(new DownstreamTimeoutException("timeout", new RuntimeException()))
            .when(downstreamClient).execute(any(), any(), any());

        EnforcementResult result = service.enforce(identity, request, "REQ-6", "trace");

        assertThat(result.status().value()).isEqualTo(403);
        assertThat(result.body().reasonCodes()).containsExactly("DOWNSTREAM_TIMEOUT");
        verify(coreClient).updateAuditOutcome(eq(identity), eq("REQ-6"), any(AuditOutcome.class), eq("trace"));
    }

    private AuthorizationOutcome allowOutcome() {
        return new AuthorizationOutcome(
            new PolicyDecisionResult(PolicyDecision.ALLOW, "LOW", false, List.of(), "policy-1"),
            0.10);
    }
}
