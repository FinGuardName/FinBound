package io.finguard.gateway.dto;

import java.time.Instant;

import io.finguard.gateway.contract.FinancialTool;

public record AuditStart(
    String requestId,
    String traceId,
    String agentRunId,
    String verifiedAgentId,
    String caseId,
    String targetConsumerId,
    FinancialTool requestedTool,
    String status,
    Instant requestedAt
) {
}
