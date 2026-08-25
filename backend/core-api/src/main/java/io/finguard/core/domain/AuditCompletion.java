package io.finguard.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** 한 번만 적용할 수 있는 Business Audit 최종 결과. */
public record AuditCompletion(
        PolicyDecision decision,
        AuditStatus systemOutcome,
        Set<String> reasonCodes,
        boolean downstreamReached,
        boolean responseReleased,
        BigDecimal behaviorRisk,
        String policyVersion,
        Instant completedAt) {

    public AuditCompletion {
        if (systemOutcome == null || systemOutcome == AuditStatus.PROCESSING) {
            throw new IllegalArgumentException("Audit completion requires a final system outcome");
        }
        if (systemOutcome == AuditStatus.COMPLETED && decision == null) {
            throw new IllegalArgumentException("Completed audit requires a policy decision");
        }
        if (decision == PolicyDecision.BLOCK && downstreamReached) {
            throw new IllegalArgumentException("Blocked audit cannot reach downstream");
        }
        if (behaviorRisk != null
                && (behaviorRisk.compareTo(BigDecimal.ZERO) < 0
                        || behaviorRisk.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("Behavior risk must be between zero and one");
        }
        if (completedAt == null) {
            throw new IllegalArgumentException("Audit completion requires completedAt");
        }
        reasonCodes = Set.copyOf(reasonCodes);
    }
}
