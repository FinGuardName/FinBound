package io.finguard.gateway.client.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.finguard.gateway.client.CoreClient;
import io.finguard.gateway.dto.AuditOutcome;
import io.finguard.gateway.dto.AuditStart;
import io.finguard.gateway.dto.BehaviorHistory;
import io.finguard.gateway.dto.PromptRiskSnapshot;
import io.finguard.gateway.dto.ResolvedContext;
import io.finguard.gateway.dto.ScopeStatus;
import io.finguard.gateway.dto.ToolCallRequest;
import io.finguard.gateway.identity.VerifiedAgentIdentity;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("!real-core")
public class MockCoreClient implements CoreClient {

    private static final String CASE_CONSUMER = "CUST-1001";

    @Override
    public ResolvedContext resolveContext(VerifiedAgentIdentity identity,
                                          ToolCallRequest request,
                                          String requestId,
                                          String traceparent) {
        log.debug("[mock-core] resolveContext requestId={} target={}", requestId, request.targetConsumerId());
        String customerScope = CASE_CONSUMER.equals(request.targetConsumerId()) ? "OK" : "VIOLATION";
        ScopeStatus scopeStatus = new ScopeStatus(
            "OK", "OK", "OK", "OK", "OK", "OK",
            customerScope, "OK", "OK"
        );
        return new ResolvedContext(
            requestUuid(requestId),
            new ResolvedContext.References("EMP-101", "LOAN-2026-001", request.passportId()),
            scopeStatus,
            new PromptRiskSnapshot("EVALUATED", BigDecimal.valueOf(0.05), false, "sha256:mock-evaluated", "prompt-guard-1")
        );
    }

    @Override
    public void createAudit(VerifiedAgentIdentity identity, AuditStart auditStart, String traceparent) {
        log.info("[mock-core] AuditEvent PROCESSING recorded requestId={}", auditStart.requestId());
    }

    @Override
    public void updateAuditOutcome(VerifiedAgentIdentity identity,
                                   String requestId,
                                   AuditOutcome outcome,
                                   String traceparent) {
        log.info("[mock-core] AuditEvent outcome recorded requestId={} outcome={} reasonCodes={}",
            requestId, outcome.systemOutcome(), outcome.reasonCodes());
    }

    @Override
    public BehaviorHistory behaviorHistory(VerifiedAgentIdentity identity,
                                           String window,
                                           String requestId,
                                           String traceparent) {
        return new BehaviorHistory(identity.agentId(), window, java.util.List.of());
    }

    @Override
    public void recordAuthFailure(String requestId, String traceparent, String reasonCode) {
        log.info("[mock-core] SecurityAuthEvent recorded requestId={} reason={}", requestId, reasonCode);
    }

    private UUID requestUuid(String requestId) {
        try {
            return UUID.fromString(requestId);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(requestId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
