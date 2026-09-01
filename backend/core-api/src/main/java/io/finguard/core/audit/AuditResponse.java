package io.finguard.core.audit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import io.finguard.core.domain.AuditEvent;
import io.finguard.core.domain.AuditStatus;
import io.finguard.core.domain.PolicyDecision;

/** 생성 및 Outcome 갱신 뒤 반환하는 Business Audit 상태. */
public record AuditResponse(
        String auditEventId,
        String requestId,
        String traceId,
        String agentId,
        String agentRunId,
        PolicyDecision decision,
        Set<String> reasonCodes,
        Boolean downstreamReached,
        Boolean responseReleased,
        Boolean success,
        Integer recordsRead,
        Long latencyMs,
        String errorLocation,
        BigDecimal behaviorRisk,
        String policyVersion,
        AuditStatus status,
        Instant requestedAt,
        Instant completedAt) {

    public AuditResponse {
        reasonCodes = Set.copyOf(reasonCodes);
    }

    public static AuditResponse from(AuditEvent event) {
        return new AuditResponse(
                event.getAuditEventId(),
                event.getRequestId(),
                event.getTraceId(),
                event.getAgentId(),
                event.getAgentRunId(),
                event.getDecision(),
                event.getReasonCodes(),
                event.getDownstreamReached(),
                event.getResponseReleased(),
                event.getSuccess(),
                event.getRecordsRead(),
                event.getLatencyMs(),
                event.getErrorLocation(),
                event.getBehaviorRisk(),
                event.getPolicyVersion(),
                event.getStatus(),
                event.getRequestedAt(),
                event.getCompletedAt());
    }
}
