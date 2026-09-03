package io.finguard.gateway.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.finguard.gateway.contract.PolicyDecision;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditOutcome(
    PolicyDecision decision,
    String systemOutcome,
    Set<String> reasonCodes,
    boolean downstreamReached,
    boolean responseReleased,
    Boolean success,
    Integer recordsRead,
    Long latencyMs,
    String errorLocation,
    BigDecimal behaviorRisk,
    String policyVersion,
    Instant completedAt
) {
    public AuditOutcome {
        reasonCodes = reasonCodes == null ? Set.of() : Set.copyOf(reasonCodes);
    }
}
